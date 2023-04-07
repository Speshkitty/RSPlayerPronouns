package com.speshkitty.playerpronouns;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.client.RuneLite;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;

@Slf4j
@Singleton
public class DatabaseAPI {
    private static final MediaType JSON = MediaType.parse("text/json");
    private static final String apiAddress = "https://sx055om2ka.execute-api.eu-west-2.amazonaws.com/publish/";
    private static final String localCacheFolder = RuneLite.RUNELITE_DIR + "/pronouns";
    private static final String localCacheFile = localCacheFolder + "/pronouns.json";

    private HashMap<String, DatabaseData> knownPronouns = new HashMap<>();
    private boolean cacheIsDirty = false;

    @Inject private OkHttpClient okHttpClient;
    @Inject private Gson gson;
    @Inject private Client client;

    @Inject private ConfigManager configManager;

    @Inject
    private PlayerPronounsConfig config;
    private final PlayerPronounsPlugin playerPronounsPlugin;

    @Inject
    DatabaseAPI(PlayerPronounsPlugin playerPronounsPlugin) {
        this.playerPronounsPlugin = playerPronounsPlugin;

        tryCreateFile();
    }

    @Inject
    private ChatMessageManager chatMessageManager;

    protected void destroy(){
        knownPronouns = new HashMap<>();
        cacheIsDirty = false;
    }

    private void tryCreateFile() {
        try {
            if (new File(localCacheFolder).mkdir()) {
                log.debug("Created folder: " + localCacheFolder);
            }
            if (new File(localCacheFile).createNewFile()) {
                log.debug("Created file: " + localCacheFile);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected String findUserPronouns(String username) {
        String hashedName = hashString(username);
        if (knownPronouns.containsKey(hashedName)) {
            DatabaseData data = knownPronouns.get(hashedName);
            if (data == null || data.getPronoun() == null) {
                return "";
            }
            return knownPronouns.get(hashedName).getPronoun();
        } else {
            return "";
        }
    }

    protected void getData() {
        tryCreateFile();
        if (knownPronouns.size() == 0) {
            readFromFile();
            log.debug("Read " + knownPronouns.size() + " items from file!");
        }

        readFromServer();
    }

    //Annoying workaround to make gson work right
    Type typeMyType = new TypeToken<HashMap<String, DatabaseData>>() {}.getType();

    private void readFromFile() {
        try {
            FileReader reader = new FileReader(localCacheFile);
            knownPronouns = gson.fromJson(reader, typeMyType);
            if (knownPronouns == null) {
                knownPronouns = new HashMap<>();
                return;
            }
            cacheIsDirty = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void readFromServer() {
        if (client.getGameState() != GameState.LOGGED_IN || playerPronounsPlugin.getPlayerNameHashed().isEmpty()) {
            return;
        }

        List<String> playersToLookup = new ArrayList<>();

        playersToLookup.addAll(addCachedPlayers());
        playersToLookup.addAll(addClanPlayers(client.getClanSettings()));
        playersToLookup.addAll(addClanPlayers(client.getGuestClanSettings()));
        playersToLookup.addAll(addFriendsChatPlayers());
        playersToLookup.addAll(addFriendsList());

        if (playersToLookup.size() == 0) { // Nothing to lookup
            return;
        }

        JsonArray array = gson.toJsonTree(playersToLookup.toArray()).getAsJsonArray();
        JsonObject data = new JsonObject();
        data.addProperty("senderUsername", playerPronounsPlugin.getPlayerNameHashed());
        data.add("usernames", array);

        log.debug(data.toString());

        RequestBody body = RequestBody.create(JSON, data.toString());

        Request request = new Request.Builder()
                .url(apiAddress)
                .post(body)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.body() == null) return;
            String responseString = response.body().string();

            if (responseString.contains("errorMessage")) {
                return;
            }
            final ResponseObject responseObject = gson.fromJson(responseString, ResponseObject.class);

            playersToLookup.forEach(s -> {
                int val = responseObject.findIndex(s);

                if (val == -1) {
                    knownPronouns.putIfAbsent(s, new DatabaseData(Instant.now().getEpochSecond(), null));
                } else {
                    knownPronouns.putIfAbsent(s, new DatabaseData(Instant.now().getEpochSecond(), responseObject.getBody().get(val).getPronoun()));
                }
                cacheIsDirty = true;
            });
        } catch (IOException e) {
            log.error("Error communicating with server!");
        }
        saveDataToFile();
    }

    private List<String> addCachedPlayers() {
        List<String> toReturn = new ArrayList<>();
        for (Player cachedPlayer : client.getCachedPlayers()) {
            if (cachedPlayer == null) {
                continue;
            }
            String hashedTarget = hashString(cachedPlayer.getName());

            if (shouldAddNameToLookup(hashedTarget)) {
                toReturn.add(hashedTarget);
            }
        }
        return toReturn;
    }
    private List<String> addClanPlayers(ClanSettings clan) {
        List<String> toReturn = new ArrayList<>();
        if(clan == null) { //Player isn't in a clan/guest clan
            return toReturn;
        }
        for (ClanMember clanMember : clan.getMembers()) {
            if (clanMember == null) {
                continue;
            }
            String hashedTarget = hashString(clanMember.getName());

            if (shouldAddNameToLookup(hashedTarget)) {
                toReturn.add(hashedTarget);
            }
        }
        return toReturn;
    }
    private List<String> addFriendsChatPlayers() {
        List<String> toReturn = new ArrayList<>();
        if(client.getFriendsChatManager() == null) { //Player isn't in a friends chat
            return toReturn;
        }

        for (FriendsChatMember friendsChatMember : client.getFriendsChatManager().getMembers()) {
            if (friendsChatMember == null) {
                continue;
            }
            String hashedTarget = hashString(friendsChatMember.getName());

            if (shouldAddNameToLookup(hashedTarget)) {
                toReturn.add(hashedTarget);
            }
        }
        return toReturn;
    }
    private List<String> addFriendsList(){
        List<String> toReturn = new ArrayList<>();
        if(client.getFriendContainer() == null) { //Player isn't in a friends chat
            return toReturn;
        }

        for (Friend friend : client.getFriendContainer().getMembers()) {
            if (friend == null) {
                continue;
            }
            String hashedTarget = hashString(friend.getName());

            if (shouldAddNameToLookup(hashedTarget)) {
                toReturn.add(hashedTarget);
            }
        }
        return toReturn;
    }

    private boolean shouldAddNameToLookup(String name) {
        //if (name.equalsIgnoreCase(playerPronounsPlugin.playerNameHashed)) { return false; }
        return !knownPronouns.containsKey(name);
    }

    protected void putPlayersPronoun(Pronoun oldPronoun, boolean isLoginTriggered) {
        if (client.getGameState() != GameState.LOGGED_IN || playerPronounsPlugin.getPlayerNameHashed().isEmpty()) {
            return;
        }
        int pronounToPut = config.presetPronoun().getInternalValue();

        String apiKey = configManager.getConfiguration(PlayerPronounsConfig.GROUP,
                "apikey." + playerPronounsPlugin.getPlayerNameHashed());

        JsonObject data = new JsonObject();
        data.addProperty("username", playerPronounsPlugin.getPlayerNameHashed());
        data.addProperty("pronoun", pronounToPut);
        if(apiKey != null && !apiKey.isEmpty()) {
            data.addProperty("apikey", apiKey);
        }

        RequestBody body = RequestBody.create(JSON, data.toString());

        log.debug(data.toString());

        Request request = new Request.Builder()
                .url(apiAddress)
                .put(body)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {

            if (response.body() == null) return;
            JsonObject responseData = gson.fromJson(response.body().string(), JsonObject.class);
            JsonPrimitive statusCodePrim = responseData.getAsJsonPrimitive("statusCode");
            JsonPrimitive responseMessagePrim = responseData.getAsJsonPrimitive("body");

            int statusCode = statusCodePrim == null ? 430 : statusCodePrim.getAsInt();
            String responseMessage = responseMessagePrim == null ? "" : responseMessagePrim.getAsString();

            if(responseData.has("apikey")) {
                configManager.setConfiguration(PlayerPronounsConfig.GROUP,
                        "apikey." + playerPronounsPlugin.getPlayerNameHashed(),
                        responseData.getAsJsonPrimitive("apikey").getAsString());
                sendMessage("API key has been received and set, and is stored in your RuneLite config. Do not share this!");
                sendMessage("If you lose this, you will not be able to update your pronoun until it is removed from the database!");
            }

            if (statusCode == 200) {
                if (!isLoginTriggered) sendMessage(responseMessage);
            } else {
                if (!isLoginTriggered) sendMessage("Error " + statusCode + " - " + responseMessage);
                resetPronounConfig(oldPronoun);
            }
        } catch (IOException e) {
            log.error("Error putting data to server!");
        }
    }

    private void resetPronounConfig(Pronoun pronoun){
        playerPronounsPlugin.shouldUpdateConfig = false;
        configManager.setConfiguration(PlayerPronounsConfig.GROUP,"presetPronoun", pronoun);
        playerPronounsPlugin.shouldUpdateConfig = true;
    }

    protected void cleanUpData() {
        for (Map.Entry<String, DatabaseData> value : new HashMap<>(knownPronouns).entrySet()) {
            Instant timeCreated = Instant.ofEpochSecond(value.getValue().getRetrievedAt());
            Instant destroyAfter;
            if(value.getValue().getPronoun() == null || value.getValue().getPronoun().isEmpty()) {
                destroyAfter = timeCreated.plus(Period.ofDays(1)); //Check daily if no pronoun is known
            } else {
                destroyAfter = timeCreated.plus(Period.ofDays(14)); //Otherwise, wait 2 weeks to save my server from being bullied
            }
            if (Instant.now().isAfter(destroyAfter)) {
                knownPronouns.remove(value.getKey());
                cacheIsDirty = true;
            }
        }

        saveDataToFile();
    }

    protected void saveDataToFile() {
        if (!cacheIsDirty) {
            return;
        }

        tryCreateFile();
        try {
            HashMap<String, DatabaseData> toSave = new HashMap<>();

            knownPronouns.forEach((hashedName, data) -> {
                if (data != null && data.getPronoun() != null && !data.getPronoun().isEmpty()) {
                    toSave.put(hashedName, data);
                }
            });

            String jsonData = gson.toJson(toSave);

            FileWriter dataFile = new FileWriter(localCacheFile);
            dataFile.write(jsonData);
            dataFile.close();
            cacheIsDirty = false;
        } catch (IOException e) {
            log.error("Error saving data!");
            e.printStackTrace();
        }
    }

    private void sendMessage(String message){
        chatMessageManager.queue(
            QueuedMessage.builder()
                .type(ChatMessageType.CONSOLE)
                .runeLiteFormattedMessage(message)
                .build());
    }

    String hashString(String input) {
        if (input == null) {
            return "";
        }
        String cleanedName = Text.removeTags(input);
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(cleanedName.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "";
        }
    }
}

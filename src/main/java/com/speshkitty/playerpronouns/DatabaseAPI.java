package com.speshkitty.playerpronouns;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.client.RuneLite;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
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

    private static HashMap<String, DatabaseData> knownPronouns = new HashMap<>();
    private boolean cacheIsDirty = false;

    private static OkHttpClient okHttpClient = new OkHttpClient();

    @Inject private Client client;

    @Inject private ConfigManager configManager;

    @Inject
    private PlayerPronounsConfig config;
    private PlayerPronounsPlugin playerPronounsPlugin;

    @Inject
    DatabaseAPI(PlayerPronounsPlugin playerPronounsPlugin) {
        this.playerPronounsPlugin = playerPronounsPlugin;

        tryCreateFile();
    }

    @Inject
    private ChatMessageManager chatMessageManager;

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
            Gson gson = new Gson();
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
        if (client.getGameState() != GameState.LOGGED_IN || playerPronounsPlugin.playerNameHashed.isEmpty()) {
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

        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();

        JsonArray array = gson.toJsonTree(playersToLookup.toArray()).getAsJsonArray();
        JsonObject data = new JsonObject();
        data.addProperty("senderUsername", playerPronounsPlugin.playerNameHashed);
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
        if (name.equalsIgnoreCase(playerPronounsPlugin.playerNameHashed)) { return false; }
        if (knownPronouns.containsKey(name)) { return false; }

        return true;
    }

    protected void putPlayersPronoun(String oldPronoun) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        String playerNameHashed = hashString(client.getLocalPlayer().getName());
        String pronounToPut = config.pronoun();

        JsonObject data = new JsonObject();
        data.addProperty("username", playerNameHashed);
        data.addProperty("pronoun", pronounToPut);

        RequestBody body = RequestBody.create(JSON, data.toString());

        Request request = new Request.Builder()
                .url(apiAddress)
                .put(body)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            Gson gson = new Gson();

            if (response.body() == null) return;
            JsonObject responseData = gson.fromJson(response.body().string(), JsonObject.class);
            JsonPrimitive statusCodePrim = responseData.getAsJsonPrimitive("statusCode");
            int statusCode = statusCodePrim.getAsInt();

            if (statusCode == 429) {
                sendMessage("Unable to update pronouns - please try again later.");
                playerPronounsPlugin.shouldUpdateConfig = false;
                configManager.setConfiguration(
                        PlayerPronounsConfig.GROUP,
                        "pronoun",
                        oldPronoun);
            } else if (statusCode == 200) {
                sendMessage("Pronouns updated! Please note this may take some time to sync to everyone.");
            } else {
                sendMessage("An unknown error occurred (Error: " + statusCode + ")");
                playerPronounsPlugin.shouldUpdateConfig = false;
                configManager.setConfiguration(
                        PlayerPronounsConfig.GROUP,
                        "pronoun",
                        oldPronoun);
            }
        } catch (IOException e) {
            log.error("Error putting data to server!");
        }
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
            GsonBuilder builder = new GsonBuilder();
            builder.serializeNulls();
            Gson gson = builder.create();
            String jsonData = gson.toJson(knownPronouns);

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
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
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

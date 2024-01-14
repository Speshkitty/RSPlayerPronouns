package com.speshkitty.playerpronouns;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
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
import java.util.*;
import java.util.zip.GZIPInputStream;

@Slf4j
@Singleton
public class DatabaseAPI {
    private static final MediaType JSON = MediaType.parse("text/json");
    private static final String apiAddress = "https://sx055om2ka.execute-api.eu-west-2.amazonaws.com/publish/";
    private static final String lookupAddress = "https://osrs-pronouns-plugin.s3.eu-west-2.amazonaws.com/latest.txt";

    private HashMap<String, String> knownPronouns = new HashMap<>();

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
    }

    @Inject
    private ChatMessageManager chatMessageManager;

    protected void destroy(){
        knownPronouns = new HashMap<>();
    }

    protected String findUserPronouns(String username) {
        String hashedName = hashString(username);
        //log.debug(String.format("\"%s\" hashed to \"%s\"", username, hashedName)); Commented due to spammy
        return knownPronouns.getOrDefault(hashedName, "");
    }

    //Annoying workaround to make gson work right
    Type typeMyType = new TypeToken<List<DataBean>>() {}.getType();

    protected void readFromServer() {
        log.debug("Beginning read from server...");

        Request request = new Request.Builder()
                .url(lookupAddress)
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            log.debug("Request sent");
            if (response.body() == null) return;
            ResponseBody responseBody = response.body();
            log.debug("Response received!");

            String decompressed;
            try (final GZIPInputStream gzipInput = new GZIPInputStream(responseBody.byteStream())
                 ) {
                StringBuilder writer = new StringBuilder ();
                int b ;
                while((b = gzipInput.read()) != -1) {
                    writer.append((char)b);
                }
                decompressed = writer.toString(); //tasty JSON data


            } catch (IOException e) {
                throw new UncheckedIOException("Error while decompressing GZIP!", e);
            }

            try {
                List<DataBean> test = gson.fromJson(decompressed, typeMyType);
                knownPronouns.clear();
                for(DataBean bean : test){
                    knownPronouns.put(bean.getId(), bean.getPronoun());
                }
            }
            catch (Exception e){
                sendMessage("There was an error loading pronoun data from the server!");
                sendMessage("This will be automatically retried in 30 minutes.");
                log.error("Error parsing json!");
                e.printStackTrace();
            }

        } catch (IOException e) {
            log.error("Error communicating with server!");
        }
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

        //log.debug(data.toString());

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
            String responseMessage = responseMessagePrim == null ? "You are likely being rate limited!" : responseMessagePrim.getAsString();

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

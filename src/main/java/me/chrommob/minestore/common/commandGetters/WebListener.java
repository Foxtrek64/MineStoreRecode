package me.chrommob.minestore.common.commandGetters;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import me.chrommob.minestore.common.MineStoreCommon;
import me.chrommob.minestore.common.commandGetters.dataTypes.GsonReponse;
import me.chrommob.minestore.common.commandGetters.dataTypes.ParsedResponse;
import me.chrommob.minestore.common.config.ConfigKey;
import me.chrommob.minestore.common.config.ConfigReader;
import me.chrommob.minestore.common.interfaces.commands.CommandGetter;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

public class WebListener implements CommandGetter {
    private final MineStoreCommon mineStoreCommon;
    private final ConfigReader configReader;
    private final Gson gson = new Gson();
    private boolean running;
    private boolean wasEmpty = false;
    private URL queueUrl;
    private URL executedUrl;
    public Runnable runnable = new Runnable() {
        @Override
        public void run() {
            while (running) {
                if (wasEmpty) {
                    try {
                        Thread.sleep(25000);
                        wasEmpty = false;
                    } catch (InterruptedException e) {
                        MineStoreCommon.getInstance().debug(e);
                    }
                    continue;
                }
                mineStoreCommon.debug("[WebListener] Running...");
                try {
                    ParsedResponse parsedResponse = null;
                    GsonReponse response;
                    HttpsURLConnection urlConnection = (HttpsURLConnection) queueUrl.openConnection();
                    InputStream in = urlConnection.getInputStream();

                    BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(in));

                    String line;
                    while ((line = reader.readLine()) != null) {
                        try {
                            response = gson.fromJson(line, GsonReponse.class);
                            parsedResponse = parseGson(response);
                        } catch (JsonSyntaxException e) {
                            wasEmpty = true;
                        }
                    }
                    if (wasEmpty || parsedResponse == null || parsedResponse.username() == null) {
                        mineStoreCommon.debug("Got empty response from server");
                        wasEmpty = true;
                    }
                    reader.close();
                    in.close();
                    urlConnection.disconnect();
                    if (!wasEmpty) {
                        switch (parsedResponse.type()) {
                            case COMMAND:
                                mineStoreCommon.commandStorage().listener(parsedResponse);
                                mineStoreCommon.debug("Got command: " + "\"" + parsedResponse.command() + "\"" + " with id: " + parsedResponse.commandId() + " for player: " + parsedResponse.username() + " requires online: " + (parsedResponse.commandType().equals(ParsedResponse.COMMAND_TYPE.ONLINE) ? "true" : "false"));
                                break;
                            case AUTH:
                                mineStoreCommon.debug("Got auth for player: " + parsedResponse.username() + " with id: " + parsedResponse.authId());
                                mineStoreCommon.authHolder().listener(parsedResponse);
                                break;
                        }
                        post(parsedResponse);
                    }
                } catch (IOException e) {
                    MineStoreCommon.getInstance().debug(e);
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    MineStoreCommon.getInstance().debug(e);
                }
            }
        }
    };

    public WebListener(MineStoreCommon mineStoreCommon) {
        running = false;
        this.mineStoreCommon = mineStoreCommon;
        configReader = mineStoreCommon.configReader();
    }

    @Override
    public boolean load() {
        running = false;
        String finalQueueUrl;
        String finalExecutedUrl;
        String storeUrl = (String) configReader.get(ConfigKey.STORE_URL);
        if (storeUrl.endsWith("/")) {
            storeUrl = storeUrl.substring(0, storeUrl.length() - 1);
        }
        if ((boolean) configReader.get(ConfigKey.SECRET_ENABLED)) {
            finalQueueUrl = storeUrl + "/api/servers/" + configReader.get(ConfigKey.SECRET_KEY) + "/commands/queue";
            finalExecutedUrl = storeUrl + "/api/servers/" + configReader.get(ConfigKey.SECRET_KEY) + "/commands/executed/";
        } else {
            finalQueueUrl = storeUrl + "/api/servers/commands/queue";
            finalExecutedUrl = storeUrl + "/api/servers/commands/executed/";
        }
        try {
            queueUrl = new URL(finalQueueUrl);
            executedUrl = new URL(finalExecutedUrl);
        } catch (Exception e) {
            mineStoreCommon.log("Store URL is not a URL!");
            MineStoreCommon.getInstance().debug(e);
            return false;
        }
        try {
            HttpsURLConnection urlConnection = (HttpsURLConnection) queueUrl.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();
            urlConnection.disconnect();
        } catch (IOException e) {
            mineStoreCommon.log("Store URL is not a valid URL!");
            MineStoreCommon.getInstance().debug(e);
            return false;
        }
        running = true;
        return true;
    }

    @Override
    public void start() {
        new Thread(runnable).start();
    }

    private void post(ParsedResponse response) {
        try {
            String id = String.valueOf(response.commandId());
            URL url = new URL(executedUrl + id);
            MineStoreCommon.getInstance().debug("Posting to: " + url);
            HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setDoOutput(true);
            urlConnection.connect();
            try (final OutputStream os = urlConnection.getOutputStream()) {
                os.write(id.getBytes());
            }
            urlConnection.getInputStream();
            urlConnection.disconnect();
        } catch (IOException e) {
            MineStoreCommon.getInstance().debug(e);
        }
    }

    private ParsedResponse parseGson(GsonReponse response) {
        ParsedResponse.TYPE type;
        if (response.getType() != null) {
            type = ParsedResponse.TYPE.AUTH;
        } else {
            type = ParsedResponse.TYPE.COMMAND;
        }
        if (type == ParsedResponse.TYPE.COMMAND) {
            ParsedResponse.COMMAND_TYPE commandType;
            if (response.isPlayerOnlineNeeded()) {
                commandType = ParsedResponse.COMMAND_TYPE.ONLINE;
            } else {
                commandType = ParsedResponse.COMMAND_TYPE.OFFLINE;
            }
            return new ParsedResponse(type, commandType, response.command(), response.username(), response.requestId());
        } else {
            return new ParsedResponse(type, response.username(), response.authId(), response.requestId());
        }
    }
}

package com.mcmod.aiplayers.mindcraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public final class MindcraftBridgeClient {
    private static final Gson GSON = new GsonBuilder().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .version(HttpClient.Version.HTTP_1_1)
            .proxy(ProxySelector.of((InetSocketAddress) null))
            .build();

    private MindcraftBridgeClient() {
    }

    public static URI panelUri() {
        return URI.create("http://" + resolveLoopbackHost() + ":" + MindcraftConfigManager.getConfig().mindserverPort());
    }

    public static HealthResponse health() throws IOException, InterruptedException {
        return readJson("GET", "/api/aiplayers/health", null, HealthResponse.class);
    }

    public static List<MindcraftBotInfo> listBots() throws IOException, InterruptedException {
        BotListResponse response = readJson("GET", "/api/aiplayers/bots", null, BotListResponse.class);
        return response == null || response.agents == null ? List.of() : response.agents;
    }

    public static MindcraftBotInfo getBot(String name) throws IOException, InterruptedException {
        BotEnvelope response = readJson("GET", "/api/aiplayers/bots/" + name, null, BotEnvelope.class);
        return response == null ? null : response.agent;
    }

    public static MindcraftBotInfo createBot(CreateBotRequest request) throws IOException, InterruptedException {
        BotEnvelope response = readJson("POST", "/api/aiplayers/bots", request, BotEnvelope.class);
        return response == null ? null : response.agent;
    }

    public static void sendMessage(String name, String from, String message) throws IOException, InterruptedException {
        readJson("POST", "/api/aiplayers/bots/" + name + "/message", new MessageRequest(from, message), GenericResponse.class);
    }

    public static void stopBot(String name) throws IOException, InterruptedException {
        readJson("POST", "/api/aiplayers/bots/" + name + "/stop", null, GenericResponse.class);
    }

    public static void startBot(String name) throws IOException, InterruptedException {
        readJson("POST", "/api/aiplayers/bots/" + name + "/start", null, GenericResponse.class);
    }

    public static void removeBot(String name) throws IOException, InterruptedException {
        readJson("DELETE", "/api/aiplayers/bots/" + name, null, GenericResponse.class);
    }

    public static void bindOwner(String name, String ownerName, String ownerUuid) throws IOException, InterruptedException {
        readJson("POST", "/api/aiplayers/bots/" + name + "/bind-owner", new OwnerBindRequest(ownerName, ownerUuid), BotEnvelope.class);
    }

    private static <T> T readJson(String method, String path, Object body, Class<T> responseType) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri(path))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json");
        if (body == null) {
            if ("DELETE".equalsIgnoreCase(method)) {
                builder.DELETE();
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
        }
        HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Mindcraft bridge request failed: HTTP " + response.statusCode() + " body=" + response.body());
        }
        if (responseType == Void.class || response.body() == null || response.body().isBlank()) {
            return null;
        }
        return GSON.fromJson(response.body(), responseType);
    }

    private static URI baseUri(String path) {
        return panelUri().resolve(path);
    }

    private static String resolveLoopbackHost() {
        String configuredHost = MindcraftConfigManager.getConfig().host();
        if (configuredHost == null || configuredHost.isBlank() || "0.0.0.0".equals(configuredHost) || "localhost".equalsIgnoreCase(configuredHost)) {
            return "127.0.0.1";
        }
        return configuredHost;
    }

    public static final class CreateBotRequest {
        public String name;
        public String ownerName;
        public String ownerUuid;
        public String host;
        public int port;
        public String profilePath;
        public boolean renderBotView = false;
        public boolean speak = false;
        public String language = "zh";
        public boolean loadMemory = true;
    }

    public static final class HealthResponse {
        public boolean ok;
        public int mindserverPort;
        public int botCount;
        public List<MindcraftBotInfo> agents;
    }

    private static final class BotListResponse {
        public boolean ok;
        public List<MindcraftBotInfo> agents;
    }

    private static final class BotEnvelope {
        public boolean success;
        public String error;
        public MindcraftBotInfo agent;
    }

    private static final class GenericResponse {
        public boolean success;
        public String error;
    }

    private record MessageRequest(String from, String message) {
    }

    private record OwnerBindRequest(String ownerName, String ownerUuid) {
    }
}
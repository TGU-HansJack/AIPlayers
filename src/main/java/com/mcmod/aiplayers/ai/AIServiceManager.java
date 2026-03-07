package com.mcmod.aiplayers.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmod.aiplayers.entity.AIPlayerEntity;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.server.level.ServerPlayer;

public final class AIServiceManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "aiplayers-api.json");
    private static final String DEFAULT_PROVIDER = "qwen-compatible";
    private static final String DEFAULT_CHAT_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String DEFAULT_CHAT_MODEL = "qwen-plus";
    private static final String LEGACY_OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static volatile Config config = loadOrCreateConfig();
    private static volatile String lastStatus = "本地规则模式";

    private AIServiceManager() {
    }

    public static void initialize() {
        config = loadOrCreateConfig();
    }

    public static void reload() {
        config = loadOrCreateConfig();
    }

    public static boolean canUseExternalService() {
        return config.enabled
                && config.url != null
                && !config.url.isBlank()
                && config.model != null
                && !config.model.isBlank();
    }

    public static void setEnabled(boolean enabled) {
        Config next = config.normalize();
        next.enabled = enabled;
        config = next;
        saveConfig(next);
        lastStatus = enabled ? "AI接口已启用" : "AI接口已关闭";
    }

    public static String getStatusSummary() {
        String endpoint = config.url == null || config.url.isBlank() ? "未配置" : config.url;
        String model = config.model == null || config.model.isBlank() ? "未配置" : config.model;
        return "AI接口：" + (config.enabled ? "已启用" : "已关闭")
                + "；提供方：" + config.provider
                + "；模型：" + model
                + "；地址：" + endpoint
                + "；状态：" + lastStatus;
    }

    public static AIServiceResponse tryRespond(AIPlayerEntity companion, ServerPlayer speaker, String message) {
        if (!canUseExternalService()) {
            lastStatus = config.enabled ? "AI接口配置不完整" : "本地规则模式";
            return null;
        }

        try {
            HttpClient client = createClient();
            HttpRequest request = buildRequest(companion, speaker, message);
            lastStatus = "请求中";
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parseHttpResponse(response);
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            lastStatus = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            return null;
        }
    }

    public static CompletableFuture<AIServiceResponse> tryRespondAsync(AIPlayerEntity companion, ServerPlayer speaker, String message) {
        if (!canUseExternalService()) {
            lastStatus = config.enabled ? "AI接口配置不完整" : "本地规则模式";
            return CompletableFuture.completedFuture(null);
        }

        try {
            HttpClient client = createClient();
            HttpRequest request = buildRequest(companion, speaker, message);
            lastStatus = "请求中";
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .handle((response, throwable) -> {
                        if (throwable != null) {
                            Throwable cause = throwable instanceof CompletionException completion && completion.getCause() != null
                                    ? completion.getCause()
                                    : throwable;
                            lastStatus = cause.getClass().getSimpleName() + ": " + cause.getMessage();
                            return null;
                        }
                        return parseHttpResponse(response);
                    });
        } catch (IllegalArgumentException ex) {
            lastStatus = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            return CompletableFuture.completedFuture(null);
        }
    }

    private static HttpClient createClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, config.timeoutMs)))
                .build();
    }

    private static HttpRequest buildRequest(AIPlayerEntity companion, ServerPlayer speaker, String message) {
        JsonObject root = new JsonObject();
        root.addProperty("model", config.model);
        root.addProperty("temperature", 0.4D);

        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", buildSystemPrompt());
        messages.add(system);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", buildUserPrompt(companion, speaker, message));
        messages.add(user);

        root.add("messages", messages);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.url))
                .timeout(Duration.ofMillis(Math.max(1000, config.timeoutMs)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(root), StandardCharsets.UTF_8));

        if (config.apiKey != null && !config.apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + config.apiKey);
        }

        return builder.build();
    }

    private static String buildSystemPrompt() {
        return "你是 Minecraft 模组中的 AI 玩家同伴。"
                + "请只返回 JSON，对象字段为 reply、mode、action。"
                + "reply 是对玩家的简短中文回复。"
                + "mode 可选：unchanged,idle,follow,guard,gather_wood,mine,explore,build_shelter,survive。"
                + "action 可选：none,jump,crouch,stand,look_up,look_down,look_owner。"
                + "如果只是聊天，请把 mode 设为 unchanged，action 设为 none。";
    }

    private static String buildUserPrompt(AIPlayerEntity companion, ServerPlayer speaker, String message) {
        return "玩家消息：" + message + "\n"
                + "玩家名：" + speaker.getName().getString() + "\n"
                + "AI 名称：" + companion.getAIName() + "\n"
                + "当前模式：" + companion.getMode().commandName() + "\n"
                + "当前规划：" + companion.getPlanSummary() + "\n"
                + "状态摘要：" + companion.getStatusSummary() + "\n"
                + "记忆摘要：" + companion.getMemorySummary();
    }

    private static AIServiceResponse parseHttpResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            lastStatus = "HTTP " + response.statusCode();
            return null;
        }

        AIServiceResponse parsed = parseResponse(response.body());
        lastStatus = parsed != null ? "最近一次调用成功" : "响应解析失败";
        return parsed;
    }

    private static AIServiceResponse parseResponse(String body) {
        try {
            JsonElement rootElement = JsonParser.parseString(body);
            if (rootElement.isJsonObject()) {
                JsonObject root = rootElement.getAsJsonObject();
                String directReply = getString(root, "reply");
                if (directReply != null) {
                    return new AIServiceResponse(
                            directReply,
                            defaultString(getString(root, "mode"), "unchanged"),
                            defaultString(getString(root, "action"), "none"),
                            "api");
                }

                JsonArray choices = root.getAsJsonArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    JsonObject message = choice.getAsJsonObject("message");
                    if (message != null) {
                        String content = getString(message, "content");
                        if (content != null && !content.isBlank()) {
                            return parseAssistantContent(content);
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static AIServiceResponse parseAssistantContent(String content) {
        try {
            JsonObject object = JsonParser.parseString(content).getAsJsonObject();
            return new AIServiceResponse(
                    defaultString(getString(object, "reply"), content),
                    defaultString(getString(object, "mode"), "unchanged"),
                    defaultString(getString(object, "action"), "none"),
                    "api");
        } catch (RuntimeException ignored) {
            return new AIServiceResponse(content, "unchanged", "none", "api");
        }
    }

    private static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Config loadOrCreateConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (!Files.exists(CONFIG_PATH)) {
                Config template = Config.createDefault();
                saveConfig(template);
                lastStatus = "已生成默认配置文件：" + CONFIG_PATH;
                return template;
            }

            String content = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            Config loaded = GSON.fromJson(content, Config.class);
            if (loaded == null) {
                lastStatus = "?????????????";
                return Config.createDefault();
            }
            Config normalized = loaded.normalize();
            if (!GSON.toJson(normalized).equals(content)) {
                saveConfig(normalized);
            }
            return normalized;
        } catch (IOException ex) {
            lastStatus = "读取配置失败：" + ex.getMessage();
            return Config.createDefault();
        }
    }

    private static void saveConfig(Config value) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(value.normalize()), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            lastStatus = "保存配置失败：" + ex.getMessage();
        }
    }

    private static final class Config {
        private boolean enabled;
        private String provider;
        private String url;
        private String apiKey;
        private String model;
        private int timeoutMs;

        private static Config createDefault() {
            Config config = new Config();
            config.enabled = false;
            config.provider = DEFAULT_PROVIDER;
            config.url = DEFAULT_CHAT_URL;
            config.apiKey = "";
            config.model = DEFAULT_CHAT_MODEL;
            config.timeoutMs = 8000;
            return config;
        }

        private Config normalize() {
            if (this.provider == null || this.provider.isBlank() || "openai-compatible".equalsIgnoreCase(this.provider)) {
                this.provider = DEFAULT_PROVIDER;
            }
            if (this.url == null || this.url.isBlank() || LEGACY_OPENAI_CHAT_URL.equalsIgnoreCase(this.url)) {
                this.url = DEFAULT_CHAT_URL;
            }
            if (this.apiKey == null) {
                this.apiKey = "";
            }
            if (this.model == null || this.model.isBlank()) {
                this.model = DEFAULT_CHAT_MODEL;
            }
            if (this.timeoutMs <= 0) {
                this.timeoutMs = 8000;
            }
            return this;
        }
    }
}

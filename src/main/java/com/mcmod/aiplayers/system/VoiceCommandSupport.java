package com.mcmod.aiplayers.system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class VoiceCommandSupport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "aiplayers-voice.json");
    private static final String DEFAULT_STT_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String DEFAULT_STT_MODEL = "qwen3-asr-flash";
    private static final String DEFAULT_TTS_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    private static final String DEFAULT_TTS_MODEL = "qwen-tts";
    private static final String DEFAULT_TTS_VOICE = "Cherry";

    private VoiceCommandSupport() {
    }

    public static String reloadAndDescribe() {
        VoiceConfig config = loadOrCreate();
        return "语音配置已重载：enabled=" + config.enabled + "，target=" + config.defaultTarget + "，stt=" + config.sttModel + "，tts=" + config.ttsModel + "/" + config.ttsVoice;
    }

    public static String statusSummary() {
        VoiceConfig config = loadOrCreate();
        StringBuilder builder = new StringBuilder();
        builder.append("enabled=").append(config.enabled)
                .append(" | autoSpeakReplies=").append(config.autoSpeakReplies)
                .append(" | defaultTarget=").append(config.defaultTarget)
                .append(" | sttModel=").append(config.sttModel)
                .append(" | ttsModel=").append(config.ttsModel)
                .append('/').append(config.ttsVoice);
        if (config.sttApiKey == null || config.sttApiKey.isBlank()) {
            builder.append(" | STT Key 未配置");
        }
        if (config.ttsApiKey == null || config.ttsApiKey.isBlank()) {
            builder.append(" | TTS Key 未配置");
        }
        return builder.toString();
    }

    public static String testTts() {
        VoiceConfig config = loadOrCreate();
        if (!config.enabled) {
            return "语音功能未启用，请先把 config/aiplayers-voice.json 中 enabled 设为 true。";
        }
        if (config.ttsApiKey == null || config.ttsApiKey.isBlank()) {
            return "TTS API Key 未配置。";
        }
        try {
            int bytes = requestTts(config, "AIPlayers voice test");
            return bytes > 0 ? "TTS 测试成功，返回音频字节=" + bytes : "TTS 测试失败，未返回音频。";
        } catch (Exception ex) {
            return "TTS 测试失败：" + ex.getMessage();
        }
    }

    private static int requestTts(VoiceConfig config, String text) throws IOException, InterruptedException {
        if (usesQwenTts(config)) {
            JsonObject root = new JsonObject();
            root.addProperty("model", config.ttsModel);
            JsonObject input = new JsonObject();
            input.addProperty("text", text);
            root.add("input", input);
            JsonObject parameters = new JsonObject();
            parameters.addProperty("voice", config.ttsVoice);
            parameters.addProperty("format", "wav");
            root.add("parameters", parameters);
            HttpRequest request = createAuthorizedRequest(config.ttsUrl, config.timeoutMs, config.ttsApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(root), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = createHttpClient(config.timeoutMs).send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode());
            }
            JsonObject parsed = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject output = parsed.getAsJsonObject("output");
            if (output == null) {
                return 0;
            }
            JsonObject audio = output.getAsJsonObject("audio");
            if (audio == null) {
                return 0;
            }
            if (audio.has("data") && !audio.get("data").isJsonNull()) {
                return audio.get("data").getAsString().length();
            }
            if (audio.has("url") && !audio.get("url").isJsonNull()) {
                return audio.get("url").getAsString().length();
            }
            return 0;
        }

        JsonObject root = new JsonObject();
        root.addProperty("model", config.ttsModel);
        root.addProperty("voice", config.ttsVoice);
        root.addProperty("response_format", "wav");
        root.addProperty("input", text);
        HttpRequest request = createAuthorizedRequest(config.ttsUrl, config.timeoutMs, config.ttsApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(root), StandardCharsets.UTF_8))
                .build();
        HttpResponse<byte[]> response = createHttpClient(config.timeoutMs).send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body() == null ? 0 : response.body().length;
    }

    private static HttpClient createHttpClient(int timeoutMs) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(2000, timeoutMs)))
                .build();
    }

    private static HttpRequest.Builder createAuthorizedRequest(String url, int timeoutMs, String apiKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(Math.max(2000, timeoutMs)));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder;
    }

    private static boolean usesQwenTts(VoiceConfig config) {
        return config.ttsUrl != null
                && config.ttsUrl.contains("dashscope.aliyuncs.com")
                && config.ttsModel != null
                && config.ttsModel.startsWith("qwen");
    }

    private static VoiceConfig loadOrCreate() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (!Files.exists(CONFIG_PATH)) {
                VoiceConfig config = VoiceConfig.createDefault();
                Files.writeString(CONFIG_PATH, GSON.toJson(config.normalize()), StandardCharsets.UTF_8);
                return config;
            }
            VoiceConfig loaded = GSON.fromJson(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8), VoiceConfig.class);
            if (loaded == null) {
                loaded = VoiceConfig.createDefault();
            }
            VoiceConfig normalized = loaded.normalize();
            Files.writeString(CONFIG_PATH, GSON.toJson(normalized), StandardCharsets.UTF_8);
            return normalized;
        } catch (IOException ex) {
            return VoiceConfig.createDefault();
        }
    }

    private static final class VoiceConfig {
        private boolean enabled;
        private boolean autoSpeakReplies;
        private String defaultTarget;
        private String sttUrl;
        private String sttApiKey;
        private String sttModel;
        private String sttLanguage;
        private String ttsUrl;
        private String ttsApiKey;
        private String ttsModel;
        private String ttsVoice;
        private int timeoutMs;

        private static VoiceConfig createDefault() {
            VoiceConfig config = new VoiceConfig();
            config.enabled = false;
            config.autoSpeakReplies = false;
            config.defaultTarget = "";
            config.sttUrl = DEFAULT_STT_URL;
            config.sttApiKey = "";
            config.sttModel = DEFAULT_STT_MODEL;
            config.sttLanguage = "zh";
            config.ttsUrl = DEFAULT_TTS_URL;
            config.ttsApiKey = "";
            config.ttsModel = DEFAULT_TTS_MODEL;
            config.ttsVoice = DEFAULT_TTS_VOICE;
            config.timeoutMs = 15000;
            return config;
        }

        private VoiceConfig normalize() {
            if (this.defaultTarget == null) {
                this.defaultTarget = "";
            }
            if (this.sttUrl == null || this.sttUrl.isBlank()) {
                this.sttUrl = DEFAULT_STT_URL;
            }
            if (this.sttApiKey == null) {
                this.sttApiKey = "";
            }
            if (this.sttModel == null || this.sttModel.isBlank()) {
                this.sttModel = DEFAULT_STT_MODEL;
            }
            if (this.sttLanguage == null || this.sttLanguage.isBlank()) {
                this.sttLanguage = "zh";
            }
            if (this.ttsUrl == null || this.ttsUrl.isBlank()) {
                this.ttsUrl = DEFAULT_TTS_URL;
            }
            if (this.ttsApiKey == null) {
                this.ttsApiKey = "";
            }
            if (this.ttsModel == null || this.ttsModel.isBlank()) {
                this.ttsModel = DEFAULT_TTS_MODEL;
            }
            if (this.ttsVoice == null || this.ttsVoice.isBlank()) {
                this.ttsVoice = DEFAULT_TTS_VOICE;
            }
            if (this.timeoutMs <= 0) {
                this.timeoutMs = 15000;
            }
            return this;
        }
    }
}

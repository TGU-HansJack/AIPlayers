package com.mcmod.aiplayers.client.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmod.aiplayers.AIPlayersMod;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class AIPlayersVoiceClient {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "aiplayers-voice.json");
    private static final String DEFAULT_STT_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String DEFAULT_STT_MODEL = "qwen3-asr-flash";
    private static final String DEFAULT_TTS_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    private static final String DEFAULT_TTS_MODEL = "qwen-tts";
    private static final String DEFAULT_TTS_VOICE = "Cherry";
    private static final String LEGACY_OPENAI_STT_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final String LEGACY_OPENAI_TTS_URL = "https://api.openai.com/v1/audio/speech";

    private static volatile VoiceConfig config = loadOrCreateConfig();
    private static volatile boolean recording;
    private static volatile String lastStatus = "待机";
    private static volatile TargetDataLine activeLine;
    private static volatile ByteArrayOutputStream activeBuffer;
    private static volatile Thread recorderThread;
    private static volatile Clip activeClip;

    private AIPlayersVoiceClient() {
    }

    public static void initialize() {
        config = loadOrCreateConfig();
    }

    public static String getStatusSummary() {
        return "语音：" + (config.enabled ? "已启用" : "未启用") + "，状态：" + lastStatus;
    }

    public static boolean isRecording() {
        return recording;
    }

    public static void toggleRecording(String targetName) {
        if (!config.enabled) {
            pushClientMessage("语音功能未启用，请先配置 config/aiplayers-voice.json");
            return;
        }
        if (recording) {
            stopRecordingAndSend(targetName);
        } else {
            startRecording();
        }
    }

    public static void handleSystemMessage(String message) {
        if (!config.enabled || !config.autoSpeakReplies || message == null || !message.startsWith("[")) {
            return;
        }
        int closing = message.indexOf(']');
        if (closing < 0 || closing + 1 >= message.length()) {
            return;
        }
        String text = message.substring(closing + 1).trim();
        if (!text.isBlank()) {
            synthesizeAndPlay(text);
        }
    }

    private static void startRecording() {
        try {
            AudioFormat format = new AudioFormat(16000.0F, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            TargetDataLine line = (TargetDataLine)AudioSystem.getLine(info);
            line.open(format);
            line.start();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            activeLine = line;
            activeBuffer = buffer;
            recording = true;
            recorderThread = new Thread(() -> captureLoop(line, buffer), "AIPlayers-VoiceRecorder");
            recorderThread.setDaemon(true);
            recorderThread.start();
            lastStatus = "录音中";
            pushClientMessage("已开始录音，再按一次按钮结束并发送。");
        } catch (LineUnavailableException ex) {
            lastStatus = "无法录音：" + ex.getMessage();
            AIPlayersMod.LOGGER.warn("Unable to start microphone capture", ex);
            pushClientMessage(lastStatus);
        }
    }

    private static void captureLoop(TargetDataLine line, ByteArrayOutputStream buffer) {
        byte[] bytes = new byte[4096];
        try {
            while (recording && line.isOpen()) {
                int read = line.read(bytes, 0, bytes.length);
                if (read > 0) {
                    buffer.write(bytes, 0, read);
                }
            }
        } catch (RuntimeException ex) {
            AIPlayersMod.LOGGER.warn("Voice capture loop failed", ex);
        }
    }

    private static void stopRecordingAndSend(String targetName) {
        TargetDataLine line = activeLine;
        ByteArrayOutputStream buffer = activeBuffer;
        activeLine = null;
        activeBuffer = null;
        recording = false;
        if (line != null) {
            line.stop();
            line.close();
        }
        if (buffer == null || buffer.size() == 0) {
            lastStatus = "没有录到声音";
            pushClientMessage(lastStatus);
            return;
        }

        byte[] wav = encodeWav(buffer.toByteArray());
        lastStatus = "识别中";
        pushClientMessage("正在识别语音...");
        transcribeAsync(wav).thenAccept(transcript -> {
            if (transcript == null || transcript.isBlank()) {
                pushClientMessage("语音识别失败，请检查 STT 配置或网络。");
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            String target = targetName == null || targetName.isBlank() ? config.defaultTarget : targetName;
            String chatLine = "@" + target + " " + transcript.trim();
            minecraft.execute(() -> {
                if (minecraft.getConnection() != null) {
                    minecraft.getConnection().sendChat(chatLine);
                    pushClientMessage("已发送语音命令：" + transcript.trim());
                }
            });
        });
    }

    private static byte[] encodeWav(byte[] pcmBytes) {
        AudioFormat format = new AudioFormat(16000.0F, 16, 1, true, false);
        try (ByteArrayInputStream input = new ByteArrayInputStream(pcmBytes);
                AudioInputStream audioInput = new AudioInputStream(input, format, pcmBytes.length / format.getFrameSize());
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            AudioSystem.write(audioInput, AudioFileFormat.Type.WAVE, output);
            return output.toByteArray();
        } catch (IOException ex) {
            AIPlayersMod.LOGGER.warn("Failed to encode wav", ex);
            return pcmBytes;
        }
    }

    private static CompletableFuture<String> transcribeAsync(byte[] wavBytes) {
        if (config.sttUrl == null || config.sttUrl.isBlank() || config.sttModel == null || config.sttModel.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        if (usesQwenAsr()) {
            return transcribeWithQwenAsync(wavBytes);
        }
        return transcribeWithLegacyMultipartAsync(wavBytes);
    }

    private static CompletableFuture<String> transcribeWithQwenAsync(byte[] wavBytes) {
        JsonObject root = new JsonObject();
        root.addProperty("model", config.sttModel);
        root.addProperty("stream", false);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");

        JsonArray content = new JsonArray();
        JsonObject audioPart = new JsonObject();
        audioPart.addProperty("type", "input_audio");
        JsonObject audioInput = new JsonObject();
        audioInput.addProperty("data", "data:audio/wav;base64," + Base64.getEncoder().encodeToString(wavBytes));
        audioPart.add("input_audio", audioInput);
        content.add(audioPart);

        user.add("content", content);
        JsonArray messages = new JsonArray();
        messages.add(user);
        root.add("messages", messages);

        if (config.sttLanguage != null && !config.sttLanguage.isBlank()) {
            JsonObject asrOptions = new JsonObject();
            asrOptions.addProperty("language", config.sttLanguage);
            root.add("asr_options", asrOptions);
        }

        HttpRequest.Builder builder = createAuthorizedRequestBuilder(config.sttUrl, config.timeoutMs, config.sttApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(root), StandardCharsets.UTF_8));

        return createHttpClient(config.timeoutMs)
                .sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, throwable) -> {
                    if (throwable != null || response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                        lastStatus = throwable != null ? throwable.getClass().getSimpleName() : "STT HTTP " + (response == null ? "null" : response.statusCode());
                        if (throwable != null) {
                            AIPlayersMod.LOGGER.warn("Qwen transcription failed: {}", lastStatus, throwable);
                        } else {
                            AIPlayersMod.LOGGER.warn("Qwen transcription failed: {}, body={}", lastStatus, summarizeResponseBody(response.body()));
                        }
                        return null;
                    }
                    return parseTranscript(response.body());
                });
    }

    private static CompletableFuture<String> transcribeWithLegacyMultipartAsync(byte[] wavBytes) {
        String boundary = "----AIPlayersBoundary" + UUID.randomUUID();
        byte[] multipart = buildLegacyMultipart(boundary, wavBytes);
        HttpRequest.Builder builder = createAuthorizedRequestBuilder(config.sttUrl, config.timeoutMs, config.sttApiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart));

        return createHttpClient(config.timeoutMs)
                .sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, throwable) -> {
                    if (throwable != null || response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                        lastStatus = throwable != null ? throwable.getClass().getSimpleName() : "STT HTTP " + (response == null ? "null" : response.statusCode());
                        AIPlayersMod.LOGGER.warn("Legacy transcription failed: {}", lastStatus);
                        return null;
                    }
                    return parseTranscript(response.body());
                });
    }

    private static byte[] buildLegacyMultipart(String boundary, byte[] wavBytes) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            writeLegacyField(output, boundary, "model", config.sttModel);
            if (config.sttLanguage != null && !config.sttLanguage.isBlank()) {
                writeLegacyField(output, boundary, "language", config.sttLanguage);
            }
            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write("Content-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n".getBytes(StandardCharsets.UTF_8));
            output.write("Content-Type: audio/wav\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            output.write(wavBytes);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        } catch (IOException ex) {
            AIPlayersMod.LOGGER.warn("Failed to build STT multipart body", ex);
            return wavBytes;
        }
    }

    private static void writeLegacyField(ByteArrayOutputStream output, String boundary, String name, String value) throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String summarizeResponseBody(String body) {
        if (body == null) {
            return "<null>";
        }
        String normalized = body.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= 240) {
            return normalized;
        }
        return normalized.substring(0, 240) + "...";
    }

    private static String parseTranscript(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("text")) {
                lastStatus = "识别完成";
                return root.get("text").getAsString();
            }
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JsonObject choice = choices.get(0).getAsJsonObject();
                JsonObject message = choice.getAsJsonObject("message");
                if (message != null) {
                    JsonElement content = message.get("content");
                    String transcript = flattenMessageContent(content);
                    if (transcript != null && !transcript.isBlank()) {
                        lastStatus = "识别完成";
                        return transcript.trim();
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        lastStatus = "STT 返回无文本";
        return null;
    }

    private static String flattenMessageContent(JsonElement content) {
        if (content == null || content.isJsonNull()) {
            return null;
        }
        if (content.isJsonPrimitive()) {
            return content.getAsString();
        }
        if (content.isJsonArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonElement element : content.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject part = element.getAsJsonObject();
                if (part.has("text") && !part.get("text").isJsonNull()) {
                    if (!builder.isEmpty()) {
                        builder.append(' ');
                    }
                    builder.append(part.get("text").getAsString());
                }
            }
            return builder.isEmpty() ? null : builder.toString();
        }
        return null;
    }

    private static void synthesizeAndPlay(String text) {
        if (config.ttsUrl == null || config.ttsUrl.isBlank() || config.ttsModel == null || config.ttsModel.isBlank()) {
            return;
        }
        if (usesQwenTts()) {
            synthesizeWithQwenAsync(text);
        } else {
            synthesizeWithLegacyOpenAiAsync(text);
        }
    }

    private static void synthesizeWithQwenAsync(String text) {
        JsonObject root = new JsonObject();
        root.addProperty("model", config.ttsModel);

        JsonObject input = new JsonObject();
        input.addProperty("text", text);
        root.add("input", input);

        JsonObject parameters = new JsonObject();
        parameters.addProperty("voice", config.ttsVoice);
        parameters.addProperty("format", "wav");
        root.add("parameters", parameters);

        HttpRequest.Builder builder = createAuthorizedRequestBuilder(config.ttsUrl, config.timeoutMs, config.ttsApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(root), StandardCharsets.UTF_8));

        createHttpClient(config.timeoutMs)
                .sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenCompose(response -> {
                    if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                        AIPlayersMod.LOGGER.warn("Qwen voice synthesis failed: HTTP {}", response == null ? "null" : response.statusCode());
                        return CompletableFuture.completedFuture(null);
                    }
                    byte[] directAudio = extractQwenAudioBytes(response.body());
                    if (directAudio != null) {
                        return CompletableFuture.completedFuture(directAudio);
                    }
                    String audioUrl = extractQwenAudioUrl(response.body());
                    if (audioUrl == null || audioUrl.isBlank()) {
                        AIPlayersMod.LOGGER.warn("Qwen voice synthesis returned no audio url/body");
                        return CompletableFuture.completedFuture(null);
                    }
                    return downloadAudio(audioUrl, config.timeoutMs);
                })
                .thenAccept(audioBytes -> {
                    if (audioBytes != null && audioBytes.length > 0) {
                        playWav(audioBytes);
                    }
                })
                .exceptionally(throwable -> {
                    AIPlayersMod.LOGGER.warn("Voice synthesis failed", throwable);
                    return null;
                });
    }

    private static void synthesizeWithLegacyOpenAiAsync(String text) {
        JsonObject root = new JsonObject();
        root.addProperty("model", config.ttsModel);
        root.addProperty("voice", config.ttsVoice);
        root.addProperty("response_format", "wav");
        root.addProperty("input", text);

        HttpRequest.Builder builder = createAuthorizedRequestBuilder(config.ttsUrl, config.timeoutMs, config.ttsApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(root), StandardCharsets.UTF_8));

        createHttpClient(config.timeoutMs)
                .sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(response -> {
                    if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                        return;
                    }
                    playWav(response.body());
                })
                .exceptionally(throwable -> {
                    AIPlayersMod.LOGGER.warn("Voice synthesis failed", throwable);
                    return null;
                });
    }

    private static byte[] extractQwenAudioBytes(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject output = root.getAsJsonObject("output");
            if (output == null) {
                return null;
            }
            JsonObject audio = output.getAsJsonObject("audio");
            if (audio == null) {
                return null;
            }
            JsonElement data = audio.get("data");
            if (data == null || data.isJsonNull()) {
                return null;
            }
            return Base64.getDecoder().decode(data.getAsString());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String extractQwenAudioUrl(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject output = root.getAsJsonObject("output");
            if (output == null) {
                return null;
            }
            JsonObject audio = output.getAsJsonObject("audio");
            if (audio == null) {
                return null;
            }
            JsonElement url = audio.get("url");
            return url == null || url.isJsonNull() ? null : url.getAsString();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static CompletableFuture<byte[]> downloadAudio(String url, int timeoutMs) {
        String normalizedUrl = normalizeAudioUrl(url);
        HttpRequest request = HttpRequest.newBuilder(URI.create(normalizedUrl))
                .timeout(Duration.ofMillis(Math.max(2000, timeoutMs)))
                .GET()
                .build();
        return createHttpClient(timeoutMs)
                .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .handle((response, throwable) -> {
                    if (throwable != null || response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                        lastStatus = throwable != null ? "TTS 下载失败" : "TTS 下载 HTTP " + (response == null ? "null" : response.statusCode());
                        return null;
                    }
                    return response.body();
                });
    }

    private static String normalizeAudioUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        if (url.startsWith("http://dashscope-result")) {
            return "https://" + url.substring("http://".length());
        }
        return url;
    }

    private static void playWav(byte[] audioBytes) {
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audioBytes))) {
            Clip previous = activeClip;
            if (previous != null) {
                try {
                    previous.stop();
                    previous.close();
                } catch (RuntimeException ignored) {
                }
            }
            Clip clip = AudioSystem.getClip();
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    if (activeClip == clip) {
                        activeClip = null;
                    }
                    clip.close();
                }
            });
            clip.open(audio);
            activeClip = clip;
            lastStatus = "语音播报中";
            clip.start();
        } catch (Exception ex) {
            lastStatus = "语音播放失败";
            AIPlayersMod.LOGGER.warn("Unable to play synthesized voice", ex);
        }
    }

    private static boolean usesQwenAsr() {
        return config.sttUrl != null
                && config.sttUrl.contains("dashscope.aliyuncs.com")
                && config.sttUrl.contains("compatible-mode")
                && config.sttModel != null
                && config.sttModel.startsWith("qwen");
    }

    private static boolean usesQwenTts() {
        return config.ttsUrl != null
                && config.ttsUrl.contains("dashscope.aliyuncs.com")
                && config.ttsModel != null
                && config.ttsModel.startsWith("qwen");
    }

    private static HttpClient createHttpClient(int timeoutMs) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(2000, timeoutMs)))
                .build();
    }

    private static HttpRequest.Builder createAuthorizedRequestBuilder(String url, int timeoutMs, String apiKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(Math.max(2000, timeoutMs)));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder;
    }

    private static void pushClientMessage(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal("[AI Voice] " + message), false);
            }
        });
    }

    private static VoiceConfig loadOrCreateConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (!Files.exists(CONFIG_PATH)) {
                VoiceConfig template = VoiceConfig.createDefault();
                saveConfig(template);
                return template;
            }
            String content = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            VoiceConfig loaded = GSON.fromJson(content, VoiceConfig.class);
            if (loaded == null) {
                return VoiceConfig.createDefault();
            }
            VoiceConfig normalized = loaded.normalize();
            if (!GSON.toJson(normalized).equals(content)) {
                saveConfig(normalized);
            }
            return normalized;
        } catch (IOException ex) {
            AIPlayersMod.LOGGER.warn("Failed to load voice config", ex);
            return VoiceConfig.createDefault();
        }
    }

    private static void saveConfig(VoiceConfig value) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(value.normalize()), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            AIPlayersMod.LOGGER.warn("Failed to save voice config", ex);
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
            config.defaultTarget = "ai";
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
            if (this.defaultTarget == null || this.defaultTarget.isBlank()) {
                this.defaultTarget = "ai";
            }
            if (this.sttUrl == null || this.sttUrl.isBlank() || LEGACY_OPENAI_STT_URL.equalsIgnoreCase(this.sttUrl)) {
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
            if (this.ttsUrl == null || this.ttsUrl.isBlank() || LEGACY_OPENAI_TTS_URL.equalsIgnoreCase(this.ttsUrl)) {
                this.ttsUrl = DEFAULT_TTS_URL;
            }
            if (this.ttsApiKey == null) {
                this.ttsApiKey = "";
            }
            if (this.ttsModel == null || this.ttsModel.isBlank()) {
                this.ttsModel = DEFAULT_TTS_MODEL;
            }
            if (this.ttsVoice == null || this.ttsVoice.isBlank() || "alloy".equalsIgnoreCase(this.ttsVoice)) {
                this.ttsVoice = DEFAULT_TTS_VOICE;
            }
            if (this.timeoutMs <= 0) {
                this.timeoutMs = 15000;
            }
            return this;
        }
    }
}

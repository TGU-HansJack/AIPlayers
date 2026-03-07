package com.mcmod.aiplayers.client.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class AIPlayersVoiceClient {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "aiplayers-voice.json");
    private static volatile VoiceConfig config = loadOrCreateConfig();
    private static volatile boolean recording;
    private static volatile String lastStatus = "?????";
    private static volatile TargetDataLine activeLine;
    private static volatile ByteArrayOutputStream activeBuffer;
    private static volatile Thread recorderThread;

    private AIPlayersVoiceClient() {
    }

    public static void initialize() {
        config = loadOrCreateConfig();
    }

    public static String getStatusSummary() {
        return "???" + (config.enabled ? "???" : "???") + "????" + lastStatus;
    }

    public static boolean isRecording() {
        return recording;
    }

    public static void toggleRecording(String targetName) {
        if (!config.enabled) {
            pushClientMessage("?????????? config/aiplayers-voice.json");
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
            lastStatus = "???";
            pushClientMessage("???????????????????" );
        } catch (LineUnavailableException ex) {
            lastStatus = "???????" + ex.getMessage();
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
            lastStatus = "????";
            pushClientMessage(lastStatus);
            return;
        }

        byte[] wav = encodeWav(buffer.toByteArray());
        lastStatus = "???";
        pushClientMessage("?????...");
        transcribeAsync(wav).thenAccept(transcript -> {
            if (transcript == null || transcript.isBlank()) {
                pushClientMessage("?????????? STT ??????");
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            String target = targetName == null || targetName.isBlank() ? config.defaultTarget : targetName;
            String chatLine = "@" + target + " " + transcript.trim();
            minecraft.execute(() -> {
                if (minecraft.getConnection() != null) {
                    minecraft.getConnection().sendChat(chatLine);
                    pushClientMessage("????????" + transcript.trim());
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

        String boundary = "----AIPlayersBoundary" + UUID.randomUUID();
        byte[] multipart = buildMultipart(boundary, wavBytes);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.sttUrl))
                .timeout(Duration.ofMillis(Math.max(2000, config.timeoutMs)))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart));
        if (config.sttApiKey != null && !config.sttApiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + config.sttApiKey);
        }

        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(2000, config.timeoutMs)))
                .build()
                .sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, throwable) -> {
                    if (throwable != null || response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                        lastStatus = throwable != null ? throwable.getClass().getSimpleName() : "STT HTTP " + (response == null ? "null" : response.statusCode());
                        AIPlayersMod.LOGGER.warn("Transcription failed: {}", lastStatus);
                        return null;
                    }
                    return parseTranscript(response.body());
                });
    }

    private static byte[] buildMultipart(String boundary, byte[] wavBytes) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            writeField(output, boundary, "model", config.sttModel);
            if (config.sttLanguage != null && !config.sttLanguage.isBlank()) {
                writeField(output, boundary, "language", config.sttLanguage);
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

    private static void writeField(ByteArrayOutputStream output, String boundary, String name, String value) throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String parseTranscript(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("text")) {
                lastStatus = "????";
                return root.get("text").getAsString();
            }
        } catch (RuntimeException ignored) {
        }
        lastStatus = "STT ??????";
        return null;
    }

    private static void synthesizeAndPlay(String text) {
        if (config.ttsUrl == null || config.ttsUrl.isBlank() || config.ttsModel == null || config.ttsModel.isBlank()) {
            return;
        }

        JsonObject root = new JsonObject();
        root.addProperty("model", config.ttsModel);
        root.addProperty("voice", config.ttsVoice);
        root.addProperty("response_format", "wav");
        root.addProperty("input", text);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.ttsUrl))
                .timeout(Duration.ofMillis(Math.max(2000, config.timeoutMs)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(root), StandardCharsets.UTF_8));
        if (config.ttsApiKey != null && !config.ttsApiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + config.ttsApiKey);
        }

        HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(2000, config.timeoutMs)))
                .build()
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

    private static void playWav(byte[] audioBytes) {
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audioBytes))) {
            Clip clip = AudioSystem.getClip();
            clip.open(audio);
            clip.start();
        } catch (Exception ex) {
            AIPlayersMod.LOGGER.warn("Unable to play synthesized voice", ex);
        }
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
                Files.writeString(CONFIG_PATH, GSON.toJson(template), StandardCharsets.UTF_8);
                return template;
            }
            VoiceConfig loaded = GSON.fromJson(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8), VoiceConfig.class);
            return loaded == null ? VoiceConfig.createDefault() : loaded.normalize();
        } catch (IOException ex) {
            AIPlayersMod.LOGGER.warn("Failed to load voice config", ex);
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
            config.defaultTarget = "ai";
            config.sttUrl = "https://api.openai.com/v1/audio/transcriptions";
            config.sttApiKey = "";
            config.sttModel = "";
            config.sttLanguage = "zh";
            config.ttsUrl = "https://api.openai.com/v1/audio/speech";
            config.ttsApiKey = "";
            config.ttsModel = "";
            config.ttsVoice = "alloy";
            config.timeoutMs = 12000;
            return config;
        }

        private VoiceConfig normalize() {
            if (this.defaultTarget == null || this.defaultTarget.isBlank()) {
                this.defaultTarget = "ai";
            }
            if (this.ttsVoice == null || this.ttsVoice.isBlank()) {
                this.ttsVoice = "alloy";
            }
            if (this.timeoutMs <= 0) {
                this.timeoutMs = 12000;
            }
            return this;
        }
    }
}

package com.mcmod.aiplayers.mindcraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mcmod.aiplayers.AIPlayersMod;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class MindcraftSidecarManager {
    private static final Object LOCK = new Object();
    private static volatile Process managedProcess;
    private static volatile String lastLogLine = "";
    private static volatile String lastHealthError = "";

    private MindcraftSidecarManager() {
    }

    public static void ensureRunning(MinecraftServer server) throws IOException, InterruptedException {
        if (isHealthy()) {
            return;
        }
        synchronized (LOCK) {
            if (isHealthy()) {
                return;
            }
            startManagedProcess(server);
            waitForHealth(Duration.ofSeconds(25));
        }
    }

    public static void stopManagedProcess() {
        synchronized (LOCK) {
            if (managedProcess != null && managedProcess.isAlive()) {
                managedProcess.destroy();
            }
            managedProcess = null;
        }
    }

    public static String lastLogLine() {
        return lastLogLine;
    }

    public static Path resolveRuntimeDir() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path direct = cwd.resolve("embedded").resolve("mindcraft");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path parent = cwd.resolve("..").normalize().resolve("embedded").resolve("mindcraft");
        if (Files.isDirectory(parent)) {
            return parent;
        }
        return direct;
    }

    public static Path resolveDataRoot(MinecraftServer server) {
        MindcraftConfigManager.Config config = MindcraftConfigManager.getConfig();
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        return worldRoot.resolve(config.dataRoot()).normalize();
    }

    private static boolean isHealthy() {
        try {
            MindcraftBridgeClient.HealthResponse response = MindcraftBridgeClient.health();
            lastHealthError = "";
            return response != null && response.ok;
        } catch (Exception exception) {
            lastHealthError = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            return false;
        }
    }

    private static void startManagedProcess(MinecraftServer server) throws IOException {
        MindcraftConfigManager.Config config = MindcraftConfigManager.getConfig();
        if (!config.enabled()) {
            throw new IOException("Mindcraft sidecar is disabled in config/aiplayers-mindcraft.json");
        }
        Path runtimeDir = resolveRuntimeDir();
        if (!Files.isDirectory(runtimeDir.resolve("src"))) {
            throw new IOException("Embedded mindcraft runtime not found: " + runtimeDir);
        }
        if (!Files.isDirectory(runtimeDir.resolve("node_modules"))) {
            throw new IOException("Embedded mindcraft dependencies are missing. Run install in " + runtimeDir);
        }
        Path dataRoot = resolveDataRoot(server);
        Files.createDirectories(dataRoot.resolve("bots"));

        lastLogLine = "";
        lastHealthError = "";

        ProcessBuilder builder = new ProcessBuilder(config.nodePath(), "main.js");
        builder.directory(runtimeDir.toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("SETTINGS_JSON", buildSettingsJson(config));
        builder.environment().put("AIPLAYERS_BOTS_ROOT", dataRoot.resolve("bots").toAbsolutePath().normalize().toString());

        managedProcess = builder.start();
        startLogPump(managedProcess);
    }

    private static void startLogPump(Process process) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lastLogLine = line;
                    AIPlayersMod.LOGGER.info("[Mindcraft] {}", line);
                }
            } catch (IOException ignored) {
            }
        }, "AIPlayers-MindcraftSidecar");
        thread.setDaemon(true);
        thread.start();
    }

    private static void waitForHealth(Duration timeout) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (isHealthy()) {
                return;
            }
            Thread.sleep(500L);
        }
        String detail = lastHealthError == null || lastHealthError.isBlank() ? lastLogLine : lastHealthError + " | last log: " + lastLogLine;
        throw new IOException("Mindcraft sidecar failed to start. " + detail);
    }

    private static String buildSettingsJson(MindcraftConfigManager.Config config) {
        JsonObject root = new JsonObject();
        root.addProperty("minecraft_version", "auto");
        root.addProperty("host", config.host());
        root.addProperty("port", -1);
        root.addProperty("auth", "offline");
        root.addProperty("mindserver_port", config.mindserverPort());
        root.addProperty("auto_open_ui", config.autoOpenPanel());
        root.addProperty("base_profile", "assistant");
        root.add("profiles", new JsonArray());
        root.addProperty("load_memory", false);
        root.addProperty("init_message", "AIPlayers embedded mindcraft sidecar ready.");
        root.add("only_chat_with", new JsonArray());
        root.addProperty("speak", false);
        root.addProperty("chat_ingame", true);
        root.addProperty("language", "zh");
        root.addProperty("render_bot_view", true);
        root.addProperty("allow_insecure_coding", false);
        root.addProperty("allow_vision", false);
        root.add("blocked_actions", new JsonArray());
        root.addProperty("max_messages", 15);
        root.addProperty("num_examples", 2);
        root.addProperty("max_commands", -1);
        root.addProperty("show_command_syntax", "shortened");
        root.addProperty("narrate_behavior", true);
        root.addProperty("chat_bot_messages", true);
        root.addProperty("spawn_timeout", 30);
        root.addProperty("block_place_delay", 0);
        root.addProperty("log_all_prompts", false);
        return root.toString();
    }
}
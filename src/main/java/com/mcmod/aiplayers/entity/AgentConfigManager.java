package com.mcmod.aiplayers.entity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AgentConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "aiplayers-agent.json");
    private static volatile Config config = loadOrCreate();

    private AgentConfigManager() {
    }

    public static void initialize() {
        config = loadOrCreate();
    }

    public static void reload() {
        config = loadOrCreate();
    }

    public static Config getConfig() {
        return config;
    }

    public static void setDebugLogsEnabled(boolean enabled) {
        Config next = config == null ? Config.createDefault() : config.normalize();
        next.debugLogsEnabled = enabled;
        config = next;
        save(next);
    }

    private static Config loadOrCreate() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (!Files.exists(CONFIG_PATH)) {
                Config value = Config.createDefault();
                save(value);
                return value;
            }
            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            Config value = GSON.fromJson(json, Config.class);
            if (value == null) {
                value = Config.createDefault();
            }
            value = value.normalize();
            if (!GSON.toJson(value).equals(json)) {
                save(value);
            }
            return value;
        } catch (IOException ignored) {
            return Config.createDefault();
        }
    }

    private static void save(Config value) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(value.normalize()), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static final class Config {
        private int worldScanIntervalTicks;
        private int plannerIntervalTicks;
        private int executorIntervalTicks;
        private int combatIntervalTicks;
        private int telemetryIntervalTicks;
        private int pathCacheTtlSeconds;
        private int resourceCacheTtlSeconds;
        private String movementProfile;
        private Boolean debugLogsEnabled;

        private static Config createDefault() {
            Config config = new Config();
            config.worldScanIntervalTicks = 40;
            config.plannerIntervalTicks = 20;
            config.executorIntervalTicks = 10;
            config.combatIntervalTicks = 5;
            config.telemetryIntervalTicks = 20;
            config.pathCacheTtlSeconds = 10;
            config.resourceCacheTtlSeconds = 3;
            config.movementProfile = "player_like";
            config.debugLogsEnabled = true;
            return config;
        }

        private Config normalize() {
            if (this.worldScanIntervalTicks <= 0) {
                this.worldScanIntervalTicks = 40;
            } else if (this.worldScanIntervalTicks == 100) {
                this.worldScanIntervalTicks = 40;
            }
            if (this.plannerIntervalTicks <= 0) {
                this.plannerIntervalTicks = 20;
            }
            if (this.executorIntervalTicks <= 0) {
                this.executorIntervalTicks = 10;
            }
            if (this.combatIntervalTicks <= 0) {
                this.combatIntervalTicks = 5;
            }
            if (this.telemetryIntervalTicks <= 0) {
                this.telemetryIntervalTicks = 20;
            }
            if (this.pathCacheTtlSeconds <= 0) {
                this.pathCacheTtlSeconds = 10;
            }
            if (this.resourceCacheTtlSeconds <= 0) {
                this.resourceCacheTtlSeconds = 3;
            }
            if (this.movementProfile == null || this.movementProfile.isBlank()) {
                this.movementProfile = "player_like";
            }
            if (this.debugLogsEnabled == null) {
                this.debugLogsEnabled = true;
            }
            return this;
        }

        public int worldScanIntervalTicks() { return this.worldScanIntervalTicks; }
        public int plannerIntervalTicks() { return this.plannerIntervalTicks; }
        public int executorIntervalTicks() { return this.executorIntervalTicks; }
        public int combatIntervalTicks() { return this.combatIntervalTicks; }
        public int telemetryIntervalTicks() { return this.telemetryIntervalTicks; }
        public int pathCacheTtlSeconds() { return this.pathCacheTtlSeconds; }
        public int resourceCacheTtlSeconds() { return this.resourceCacheTtlSeconds; }
        public String movementProfile() { return this.movementProfile; }
        public boolean debugLogsEnabled() { return this.debugLogsEnabled == null || this.debugLogsEnabled; }
    }
}

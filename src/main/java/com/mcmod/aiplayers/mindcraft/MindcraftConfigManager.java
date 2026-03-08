package com.mcmod.aiplayers.mindcraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MindcraftConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "aiplayers-mindcraft.json");
    private static volatile Config config = loadOrCreate();

    private MindcraftConfigManager() {
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

    private static Config loadOrCreate() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (!Files.exists(CONFIG_PATH)) {
                Config created = Config.createDefault();
                save(created);
                return created;
            }
            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            Config loaded = GSON.fromJson(json, Config.class);
            if (loaded == null) {
                loaded = Config.createDefault();
            }
            loaded = loaded.normalize();
            if (!GSON.toJson(loaded).equals(json)) {
                save(loaded);
            }
            return loaded;
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
        private boolean enabled;
        private String nodePath;
        private int mindserverPort;
        private String host;
        private String portMode;
        private String defaultProfile;
        private boolean autoOpenPanel;
        private String dataRoot;
        private boolean offlineAuthOnly;

        private static Config createDefault() {
            Config config = new Config();
            config.enabled = true;
            config.nodePath = "node";
            config.mindserverPort = 8080;
            config.host = "127.0.0.1";
            config.portMode = "current_server";
            config.defaultProfile = "./andy.json";
            config.autoOpenPanel = false;
            config.dataRoot = "aiplayers-mindcraft";
            config.offlineAuthOnly = true;
            return config;
        }

        private Config normalize() {
            if (this.nodePath == null || this.nodePath.isBlank()) {
                this.nodePath = "node";
            }
            if (this.mindserverPort <= 0) {
                this.mindserverPort = 8080;
            }
            if (this.host == null || this.host.isBlank()) {
                this.host = "127.0.0.1";
            }
            if (this.portMode == null || this.portMode.isBlank()) {
                this.portMode = "current_server";
            }
            if (this.defaultProfile == null || this.defaultProfile.isBlank()) {
                this.defaultProfile = "./andy.json";
            }
            if (this.dataRoot == null || this.dataRoot.isBlank()) {
                this.dataRoot = "aiplayers-mindcraft";
            }
            return this;
        }

        public boolean enabled() {
            return this.enabled;
        }

        public String nodePath() {
            return this.nodePath;
        }

        public int mindserverPort() {
            return this.mindserverPort;
        }

        public String host() {
            return this.host;
        }

        public String portMode() {
            return this.portMode;
        }

        public String defaultProfile() {
            return this.defaultProfile;
        }

        public boolean autoOpenPanel() {
            return this.autoOpenPanel;
        }

        public String dataRoot() {
            return this.dataRoot;
        }

        public boolean offlineAuthOnly() {
            return this.offlineAuthOnly;
        }
    }
}

package com.mcmod.aiplayers.mindcraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mcmod.aiplayers.AIPlayersMod;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public final class MindcraftSessionSavedData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORE_TYPE = new TypeToken<Map<String, MindcraftBotSession>>() {}.getType();
    private static final Map<Path, StoreCache> CACHES = new ConcurrentHashMap<>();

    private final Path path;

    private MindcraftSessionSavedData(Path path) {
        this.path = path;
    }

    public static MindcraftSessionSavedData get(MinecraftServer server) {
        return new MindcraftSessionSavedData(resolvePath(server));
    }

    public Collection<MindcraftBotSession> sessions() {
        synchronized (getLock()) {
            return java.util.List.copyOf(loadCache().sessions.values());
        }
    }

    public MindcraftBotSession get(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        synchronized (getLock()) {
            return loadCache().sessions.get(name.toLowerCase());
        }
    }

    public void upsert(MindcraftBotInfo info, UUID ownerUuid, String ownerName) {
        if (info == null || info.name == null || info.name.isBlank()) {
            return;
        }
        synchronized (getLock()) {
            StoreCache cache = loadCache();
            MindcraftBotSession session = cache.sessions.computeIfAbsent(info.name.toLowerCase(), ignored -> new MindcraftBotSession());
            session.name = info.name;
            if (ownerUuid != null) {
                session.ownerUuid = ownerUuid.toString();
            } else if (info.ownerUuid != null && !info.ownerUuid.isBlank()) {
                session.ownerUuid = info.ownerUuid;
            }
            if (ownerName != null && !ownerName.isBlank()) {
                session.ownerName = ownerName;
            } else if (info.ownerName != null && !info.ownerName.isBlank()) {
                session.ownerName = info.ownerName;
            }
            session.status = info.displayStatus();
            session.viewerPort = info.viewerPort;
            session.mindserverPort = info.mindserverPort;
            session.lastStateJson = info.stateJson();
            session.lastError = info.lastError == null ? "" : info.lastError;
            session.lastOutput = info.lastOutput == null ? "" : info.lastOutput;
            cache.dirty = true;
            saveIfDirty(cache);
        }
    }

    public void remove(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        synchronized (getLock()) {
            StoreCache cache = loadCache();
            if (cache.sessions.remove(name.toLowerCase()) != null) {
                cache.dirty = true;
                saveIfDirty(cache);
            }
        }
    }

    private Object getLock() {
        return this.path.toAbsolutePath().normalize().toString().intern();
    }

    private StoreCache loadCache() {
        return CACHES.computeIfAbsent(this.path.toAbsolutePath().normalize(), this::readCache);
    }

    private StoreCache readCache(Path path) {
        StoreCache cache = new StoreCache();
        try {
            Files.createDirectories(Objects.requireNonNull(path.getParent()));
            if (!Files.exists(path)) {
                return cache;
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Map<String, MindcraftBotSession> loaded = GSON.fromJson(json, STORE_TYPE);
            if (loaded != null) {
                cache.sessions.putAll(loaded);
            }
        } catch (IOException ex) {
            AIPlayersMod.LOGGER.warn("Failed to read mindcraft session store {}", path, ex);
        }
        return cache;
    }

    private void saveIfDirty(StoreCache cache) {
        if (!cache.dirty) {
            return;
        }
        try {
            Files.createDirectories(Objects.requireNonNull(this.path.getParent()));
            Files.writeString(this.path, GSON.toJson(cache.sessions), StandardCharsets.UTF_8);
            cache.dirty = false;
        } catch (IOException ex) {
            AIPlayersMod.LOGGER.warn("Failed to write mindcraft session store {}", this.path, ex);
        }
    }

    private static Path resolvePath(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        return root.resolve("data").resolve(AIPlayersMod.MODID).resolve("mindcraft_sessions.json");
    }

    private static final class StoreCache {
        private final Map<String, MindcraftBotSession> sessions = new LinkedHashMap<>();
        private boolean dirty;
    }

    public static final class MindcraftBotSession {
        private String name = "";
        private String ownerUuid = "";
        private String ownerName = "";
        private String status = "离线";
        private int viewerPort;
        private int mindserverPort;
        private String lastStateJson = "";
        private String lastError = "";
        private String lastOutput = "";

        public boolean isOwnedBy(ServerPlayer player) {
            return player != null && (this.ownerUuid == null || this.ownerUuid.isBlank() || this.ownerUuid.equals(player.getUUID().toString()));
        }

        public String name() {
            return this.name;
        }

        public String ownerName() {
            return this.ownerName;
        }

        public int viewerPort() {
            return this.viewerPort;
        }

        public int mindserverPort() {
            return this.mindserverPort;
        }

        public String status() {
            return this.status;
        }

        public String lastOutput() {
            return this.lastOutput;
        }

        public String lastError() {
            return this.lastError;
        }
    }
}

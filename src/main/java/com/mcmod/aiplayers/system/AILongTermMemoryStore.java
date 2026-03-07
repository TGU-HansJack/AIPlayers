package com.mcmod.aiplayers.system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mcmod.aiplayers.AIPlayersMod;
import com.mcmod.aiplayers.entity.AIPlayerEntity;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class AILongTermMemoryStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORE_TYPE = new TypeToken<Map<String, MemoryProfile>>() {}.getType();
    private static final Map<Path, StoreCache> CACHES = new ConcurrentHashMap<>();
    private static final int MAX_EVENTS = 64;

    private AILongTermMemoryStore() {
    }

    public static void record(AIPlayerEntity entity, String type, String detail) {
        Path path = resolvePath(entity);
        if (path == null) {
            return;
        }

        synchronized (getLock(path)) {
            StoreCache cache = loadCache(path);
            MemoryProfile profile = cache.profiles.computeIfAbsent(buildProfileKey(entity), unused -> new MemoryProfile());
            profile.events.add(type + "\uff1a" + detail);
            while (profile.events.size() > MAX_EVENTS) {
                profile.events.remove(0);
            }
            profile.counters.merge(type, 1, Integer::sum);
            profile.notes.put("last_name", entity.getAIName());
            profile.notes.put("last_mode", entity.getMode().commandName());
            profile.notes.put("last_observation", entity.getObservationSummary());
            profile.lastUpdatedEpochMs = System.currentTimeMillis();
            cache.dirty = true;
            saveIfDirty(path, cache);
        }
    }

    public static void updateNote(AIPlayerEntity entity, String key, String value) {
        Path path = resolvePath(entity);
        if (path == null || key == null || key.isBlank()) {
            return;
        }

        synchronized (getLock(path)) {
            StoreCache cache = loadCache(path);
            MemoryProfile profile = cache.profiles.computeIfAbsent(buildProfileKey(entity), unused -> new MemoryProfile());
            if (value == null || value.isBlank()) {
                profile.notes.remove(key);
            } else {
                profile.notes.put(key, value);
            }
            profile.lastUpdatedEpochMs = System.currentTimeMillis();
            cache.dirty = true;
            saveIfDirty(path, cache);
        }
    }

    public static String getSummary(AIPlayerEntity entity) {
        Path path = resolvePath(entity);
        if (path == null) {
            return "\u6682\u65e0\u957f\u671f\u8bb0\u5fc6";
        }

        synchronized (getLock(path)) {
            MemoryProfile profile = loadCache(path).profiles.get(buildProfileKey(entity));
            if (profile == null) {
                return "\u6682\u65e0\u957f\u671f\u8bb0\u5fc6";
            }

            List<String> parts = new ArrayList<>();
            if (!profile.counters.isEmpty()) {
                String topType = profile.counters.entrySet().stream()
                        .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                        .map(entry -> entry.getKey() + "x" + entry.getValue())
                        .findFirst()
                        .orElse(null);
                if (topType != null) {
                    parts.add("\u504f\u597d=" + topType);
                }
            }

            String goal = profile.notes.get("goal");
            if (goal != null && !goal.isBlank()) {
                parts.add("\u76ee\u6807=" + goal);
            }

            String blueprint = profile.notes.get("blueprint");
            if (blueprint != null && !blueprint.isBlank()) {
                parts.add("\u84dd\u56fe=" + blueprint);
            }

            if (!profile.events.isEmpty()) {
                parts.add("\u6700\u8fd1=" + profile.events.get(profile.events.size() - 1));
            }
            return parts.isEmpty() ? "\u6682\u65e0\u957f\u671f\u8bb0\u5fc6" : String.join("\uff1b", parts);
        }
    }

    public static List<String> getRecentEvents(AIPlayerEntity entity, int limit) {
        Path path = resolvePath(entity);
        if (path == null) {
            return List.of();
        }

        synchronized (getLock(path)) {
            MemoryProfile profile = loadCache(path).profiles.get(buildProfileKey(entity));
            if (profile == null || profile.events.isEmpty()) {
                return List.of();
            }
            int start = Math.max(0, profile.events.size() - Math.max(1, limit));
            return List.copyOf(profile.events.subList(start, profile.events.size()));
        }
    }

    private static Object getLock(Path path) {
        return path.toAbsolutePath().normalize().toString().intern();
    }

    private static String buildProfileKey(AIPlayerEntity entity) {
        String owner = entity.getOwnerIdString();
        String name = entity.getAIName().toLowerCase(Locale.ROOT).trim();
        return owner == null || owner.isBlank()
                ? "unowned|" + name + "|" + entity.getStringUUID()
                : owner + "|" + name;
    }

    private static Path resolvePath(AIPlayerEntity entity) {
        if (entity.level().isClientSide() || entity.level().getServer() == null) {
            return null;
        }
        MinecraftServer server = entity.level().getServer();
        Path root = server.getWorldPath(LevelResource.ROOT);
        return root.resolve("data").resolve(AIPlayersMod.MODID).resolve("long_term_memory.json");
    }

    private static StoreCache loadCache(Path path) {
        return CACHES.computeIfAbsent(path.toAbsolutePath().normalize(), AILongTermMemoryStore::readCache);
    }

    private static StoreCache readCache(Path path) {
        StoreCache cache = new StoreCache();
        try {
            Files.createDirectories(Objects.requireNonNull(path.getParent()));
            if (!Files.exists(path)) {
                return cache;
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Map<String, MemoryProfile> loaded = GSON.fromJson(json, STORE_TYPE);
            if (loaded != null) {
                cache.profiles.putAll(loaded);
            }
        } catch (IOException ex) {
            AIPlayersMod.LOGGER.warn("Failed to read long term memory store {}", path, ex);
        }
        return cache;
    }

    private static void saveIfDirty(Path path, StoreCache cache) {
        if (!cache.dirty) {
            return;
        }
        try {
            Files.createDirectories(Objects.requireNonNull(path.getParent()));
            Files.writeString(path, GSON.toJson(cache.profiles), StandardCharsets.UTF_8);
            cache.dirty = false;
        } catch (IOException ex) {
            AIPlayersMod.LOGGER.warn("Failed to write long term memory store {}", path, ex);
        }
    }

    private static final class StoreCache {
        private final Map<String, MemoryProfile> profiles = new LinkedHashMap<>();
        private boolean dirty;
    }

    private static final class MemoryProfile {
        private final List<String> events = new ArrayList<>();
        private final Map<String, Integer> counters = new LinkedHashMap<>();
        private final Map<String, String> notes = new LinkedHashMap<>();
        private long lastUpdatedEpochMs;
    }
}

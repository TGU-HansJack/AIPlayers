package com.mcmod.aiplayers.entity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mcmod.aiplayers.AIPlayersMod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class TeamMemoryService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Path, StoreCache> CACHES = new ConcurrentHashMap<>();

    private TeamMemoryService() {
    }

    public static void syncFromWorldModel(AIPlayerEntity entity, WorldModelSnapshot snapshot) {
        if (entity == null || snapshot == null) {
            return;
        }
        Path path = resolvePath(entity);
        if (path == null) {
            return;
        }
        synchronized (getLock(path)) {
            StoreCache cache = loadCache(path);
            TeamProfile profile = cache.profiles.computeIfAbsent(resolveTeamKey(entity), ignored -> new TeamProfile());
            profile.lastUpdatedEpochMs = System.currentTimeMillis();
            storeChunk(profile, entity.blockPosition());
            storeFacts(profile.resourceLocations, snapshot.resources());
            storeFacts(profile.resourceLocations, snapshot.structures());
            storePositions(profile.dangerZones, snapshot.dangers().stream().map(WorldModelSnapshot.SpatialFact::pos).toList(), 24);
            if (snapshot.ownerPos() != null) {
                storePositions(profile.baseLocations, List.of(snapshot.ownerPos()), 8);
            }
            cache.dirty = true;
            saveIfDirty(path, cache);
        }
    }

    public static void recordFailure(AIPlayerEntity entity, String detail, BlockPos pos) {
        mutate(entity, profile -> appendText(profile.recentFailures, pos == null ? detail : detail + "@" + fmt(pos), 16));
    }

    public static void recordTaskOutcome(AIPlayerEntity entity, String detail) {
        mutate(entity, profile -> appendText(profile.taskOutcomes, detail, 24));
    }

    public static SharedMemorySnapshot snapshot(AIPlayerEntity entity) {
        Path path = resolvePath(entity);
        if (path == null) {
            return SharedMemorySnapshot.empty();
        }
        synchronized (getLock(path)) {
            TeamProfile profile = loadCache(path).profiles.get(resolveTeamKey(entity));
            if (profile == null) {
                return SharedMemorySnapshot.empty();
            }
            Map<String, List<BlockPos>> resources = new LinkedHashMap<>();
            for (Map.Entry<String, List<StoredPos>> entry : profile.resourceLocations.entrySet()) {
                resources.put(entry.getKey(), entry.getValue().stream().map(StoredPos::toBlockPos).toList());
            }
            return new SharedMemorySnapshot(
                    resolveTeamKey(entity),
                    resources,
                    profile.dangerZones.stream().map(StoredPos::toBlockPos).toList(),
                    profile.baseLocations.stream().map(StoredPos::toBlockPos).toList(),
                    List.copyOf(profile.exploredChunks),
                    List.copyOf(profile.recentFailures),
                    List.copyOf(profile.taskOutcomes));
        }
    }

    private static void mutate(AIPlayerEntity entity, java.util.function.Consumer<TeamProfile> change) {
        if (entity == null || change == null) {
            return;
        }
        Path path = resolvePath(entity);
        if (path == null) {
            return;
        }
        synchronized (getLock(path)) {
            StoreCache cache = loadCache(path);
            TeamProfile profile = cache.profiles.computeIfAbsent(resolveTeamKey(entity), ignored -> new TeamProfile());
            change.accept(profile);
            profile.lastUpdatedEpochMs = System.currentTimeMillis();
            cache.dirty = true;
            saveIfDirty(path, cache);
        }
    }

    private static void storeFacts(Map<String, List<StoredPos>> bucket, List<WorldModelSnapshot.SpatialFact> facts) {
        if (bucket == null || facts == null) {
            return;
        }
        for (WorldModelSnapshot.SpatialFact fact : facts) {
            if (fact == null || fact.pos() == null) {
                continue;
            }
            String key = fact.kind() == null ? "unknown" : fact.kind().toLowerCase(Locale.ROOT);
            List<StoredPos> values = bucket.computeIfAbsent(key, ignored -> new ArrayList<>());
            storePositions(values, List.of(fact.pos()), 24);
        }
    }

    private static void storePositions(List<StoredPos> target, List<BlockPos> positions, int limit) {
        if (target == null || positions == null) {
            return;
        }
        for (BlockPos pos : positions) {
            if (pos == null) {
                continue;
            }
            boolean exists = target.stream().anyMatch(existing -> existing.toBlockPos().distSqr(pos) <= 4.0D);
            if (!exists) {
                target.add(new StoredPos(pos));
            }
        }
        while (target.size() > Math.max(1, limit)) {
            target.removeFirst();
        }
    }

    private static void appendText(List<String> target, String value, int limit) {
        if (target == null || value == null || value.isBlank()) {
            return;
        }
        if (!target.isEmpty() && value.equals(target.getLast())) {
            return;
        }
        target.add(value);
        while (target.size() > Math.max(1, limit)) {
            target.removeFirst();
        }
    }

    private static void storeChunk(TeamProfile profile, BlockPos pos) {
        if (profile == null || pos == null) {
            return;
        }
        String value = (pos.getX() >> 4) + "," + (pos.getZ() >> 4);
        appendText(profile.exploredChunks, value, 64);
    }

    private static String resolveTeamKey(AIPlayerEntity entity) {
        UUID ownerId = entity.getRuntimeOwnerId();
        return ownerId == null ? entity.getStringUUID() : ownerId.toString();
    }

    private static Path resolvePath(AIPlayerEntity entity) {
        if (entity == null || entity.level().isClientSide()) {
            return null;
        }
        MinecraftServer server = entity.level().getServer();
        if (server == null) {
            return null;
        }
        Path root = server.getWorldPath(LevelResource.ROOT);
        return root.resolve("data").resolve(AIPlayersMod.MODID).resolve("team_memory.json");
    }

    private static Object getLock(Path path) {
        return path.toAbsolutePath().normalize().toString().intern();
    }

    private static StoreCache loadCache(Path path) {
        return CACHES.computeIfAbsent(path.toAbsolutePath().normalize(), TeamMemoryService::readCache);
    }

    private static StoreCache readCache(Path path) {
        StoreCache cache = new StoreCache();
        try {
            Files.createDirectories(Objects.requireNonNull(path.getParent()));
            if (!Files.exists(path)) {
                return cache;
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            TeamStore store = GSON.fromJson(json, TeamStore.class);
            if (store != null && store.profiles != null) {
                cache.profiles.putAll(store.profiles);
            }
        } catch (IOException ex) {
            AIPlayersMod.LOGGER.warn("Failed to read team memory store {}", path, ex);
        }
        return cache;
    }

    private static void saveIfDirty(Path path, StoreCache cache) {
        if (!cache.dirty) {
            return;
        }
        try {
            Files.createDirectories(Objects.requireNonNull(path.getParent()));
            TeamStore store = new TeamStore();
            store.profiles.putAll(cache.profiles);
            Files.writeString(path, GSON.toJson(store), StandardCharsets.UTF_8);
            cache.dirty = false;
        } catch (IOException ex) {
            AIPlayersMod.LOGGER.warn("Failed to write team memory store {}", path, ex);
        }
    }

    private static String fmt(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static final class StoreCache {
        private final Map<String, TeamProfile> profiles = new LinkedHashMap<>();
        private boolean dirty;
    }

    private static final class TeamStore {
        private final Map<String, TeamProfile> profiles = new LinkedHashMap<>();
    }

    private static final class TeamProfile {
        private final Map<String, List<StoredPos>> resourceLocations = new LinkedHashMap<>();
        private final List<StoredPos> dangerZones = new ArrayList<>();
        private final List<StoredPos> baseLocations = new ArrayList<>();
        private final List<String> exploredChunks = new ArrayList<>();
        private final List<String> recentFailures = new ArrayList<>();
        private final List<String> taskOutcomes = new ArrayList<>();
        private long lastUpdatedEpochMs;
    }

    private static final class StoredPos {
        private int x;
        private int y;
        private int z;

        private StoredPos() {
        }

        private StoredPos(BlockPos pos) {
            this.x = pos.getX();
            this.y = pos.getY();
            this.z = pos.getZ();
        }

        private BlockPos toBlockPos() {
            return new BlockPos(this.x, this.y, this.z);
        }
    }
}

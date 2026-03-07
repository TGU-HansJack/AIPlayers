package com.mcmod.aiplayers.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public final class PathCache {
    private static final Map<CacheKey, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private PathCache() {
    }

    public static List<PathNode> getOrCompute(AIPlayerEntity entity, BlockPos target, String movementProfile, Supplier<List<PathNode>> supplier) {
        String dimension = entity.level().dimension().toString();
        ChunkPos start = new ChunkPos(entity.blockPosition());
        ChunkPos end = new ChunkPos(target);
        CacheKey key = new CacheKey(dimension, start.x, start.z, end.x, end.z, movementProfile);
        long now = entity.level().getGameTime();
        long ttl = AgentConfigManager.getConfig().pathCacheTtlSeconds() * 20L;
        CacheEntry cached = CACHE.get(key);
        if (cached != null && now - cached.createdTick <= ttl) {
            return new ArrayList<>(cached.nodes);
        }
        List<PathNode> nodes = supplier.get();
        CACHE.put(key, new CacheEntry(now, List.copyOf(nodes)));
        return new ArrayList<>(nodes);
    }

    public static void invalidateAt(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        String dimension = level.dimension().toString();
        ChunkPos chunkPos = new ChunkPos(pos);
        CACHE.keySet().removeIf(key -> key.dimension.equals(dimension)
                && ((key.startChunkX == chunkPos.x && key.startChunkZ == chunkPos.z)
                || (key.endChunkX == chunkPos.x && key.endChunkZ == chunkPos.z)));
    }

    private record CacheKey(String dimension, int startChunkX, int startChunkZ, int endChunkX, int endChunkZ, String profile) {
    }

    private record CacheEntry(long createdTick, List<PathNode> nodes) {
    }
}

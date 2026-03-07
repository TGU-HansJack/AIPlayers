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
        BlockPos startPos = entity.blockPosition();
        String dimension = entity.level().dimension().toString();
        ChunkPos startChunk = new ChunkPos(startPos);
        ChunkPos endChunk = new ChunkPos(target);
        CacheKey key = new CacheKey(
                dimension,
                startPos.getX(),
                startPos.getY(),
                startPos.getZ(),
                target.getX(),
                target.getY(),
                target.getZ(),
                startChunk.x,
                startChunk.z,
                endChunk.x,
                endChunk.z,
                movementProfile);
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
        CACHE.keySet().removeIf(key -> key.touchesChunk(dimension, chunkPos));
    }

    private record CacheKey(
            String dimension,
            int startX,
            int startY,
            int startZ,
            int endX,
            int endY,
            int endZ,
            int startChunkX,
            int startChunkZ,
            int endChunkX,
            int endChunkZ,
            String profile) {
        private boolean touchesChunk(String dimension, ChunkPos chunkPos) {
            return this.dimension.equals(dimension)
                    && ((this.startChunkX == chunkPos.x && this.startChunkZ == chunkPos.z)
                    || (this.endChunkX == chunkPos.x && this.endChunkZ == chunkPos.z));
        }
    }

    private record CacheEntry(long createdTick, List<PathNode> nodes) {
    }
}

package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.vendor.baritone.pathing.path.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public final class PathReuseCache {
    private static final Map<CacheKey, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_TICKS = 40L;

    private PathReuseCache() {
    }

    public static Path get(String dimension, BlockPos start, BlockPos target, long snapshotSignature, long gameTime) {
        if (dimension == null || start == null || target == null) {
            return null;
        }
        CacheEntry entry = CACHE.get(new CacheKey(dimension, start.immutable(), target.immutable(), snapshotSignature));
        if (entry == null || gameTime - entry.createdTick() > CACHE_TTL_TICKS) {
            return null;
        }
        return entry.path();
    }

    public static void put(String dimension, BlockPos start, BlockPos target, long snapshotSignature, long gameTime, Path path) {
        if (dimension == null || start == null || target == null || path == null) {
            return;
        }
        CACHE.put(new CacheKey(dimension, start.immutable(), target.immutable(), snapshotSignature), new CacheEntry(gameTime, path));
    }

    public static void invalidateAt(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        String dimension = level.dimension().toString();
        ChunkPos chunk = new ChunkPos(pos);
        CACHE.keySet().removeIf(key -> key.dimension().equals(dimension)
                && (new ChunkPos(key.start()).equals(chunk) || new ChunkPos(key.target()).equals(chunk)));
    }

    private record CacheKey(String dimension, BlockPos start, BlockPos target, long snapshotSignature) {
    }

    private record CacheEntry(long createdTick, Path path) {
    }
}

package com.mcmod.aiplayers.entity;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public final class ChunkSnapshotCache {
    private static final int DEFAULT_CHUNK_MARGIN = 1;
    private static final int DEFAULT_VERTICAL_MARGIN = 16;
    private static final long SNAPSHOT_TTL_TICKS = 40L;
    private static final BlockState MISSING_STATE = Blocks.BEDROCK.defaultBlockState();

    private static final Map<SnapshotKey, ChunkSnapshot> CACHE = new ConcurrentHashMap<>();
    private static final Map<ChunkKey, Long> CHUNK_VERSIONS = new ConcurrentHashMap<>();

    private ChunkSnapshotCache() {
    }

    public static RegionSnapshot capture(ServerLevel level, BlockPos start, BlockPos target) {
        return capture(level, start, target, DEFAULT_CHUNK_MARGIN, DEFAULT_VERTICAL_MARGIN);
    }

    public static RegionSnapshot capture(ServerLevel level, BlockPos start, BlockPos target, int chunkMargin, int verticalMargin) {
        if (level == null || start == null) {
            return RegionSnapshot.empty();
        }
        BlockPos effectiveTarget = target == null ? start : target;
        int minChunkX = Math.min(start.getX() >> 4, effectiveTarget.getX() >> 4) - Math.max(0, chunkMargin);
        int maxChunkX = Math.max(start.getX() >> 4, effectiveTarget.getX() >> 4) + Math.max(0, chunkMargin);
        int minChunkZ = Math.min(start.getZ() >> 4, effectiveTarget.getZ() >> 4) - Math.max(0, chunkMargin);
        int maxChunkZ = Math.max(start.getZ() >> 4, effectiveTarget.getZ() >> 4) + Math.max(0, chunkMargin);

        int rawMinY = Math.min(start.getY(), effectiveTarget.getY()) - Math.max(0, verticalMargin);
        int rawMaxY = Math.max(start.getY(), effectiveTarget.getY()) + Math.max(0, verticalMargin);
        int levelMinY = level.dimensionType().minY();
        int levelMaxY = levelMinY + level.dimensionType().height() - 1;
        int minY = Math.max(levelMinY, floorTo16(rawMinY));
        int maxY = Math.min(levelMaxY, ceilTo16(rawMaxY + 1) - 1);

        Map<ChunkKey, ChunkSnapshot> chunks = new java.util.HashMap<>();
        long signature = 1469598103934665603L;
        long gameTime = level.getGameTime();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkKey chunkKey = new ChunkKey(level.dimension().toString(), chunkX, chunkZ);
                long version = CHUNK_VERSIONS.getOrDefault(chunkKey, 0L);
                SnapshotKey snapshotKey = new SnapshotKey(chunkKey.dimension(), chunkX, chunkZ, minY, maxY, version);
                ChunkSnapshot snapshot = CACHE.get(snapshotKey);
                if (snapshot == null || gameTime - snapshot.createdTick() > SNAPSHOT_TTL_TICKS) {
                    snapshot = captureChunk(level, chunkKey, minY, maxY, version, gameTime);
                    CACHE.put(snapshotKey, snapshot);
                }
                chunks.put(chunkKey, snapshot);
                signature = mix(signature, chunkX);
                signature = mix(signature, chunkZ);
                signature = mix(signature, version);
            }
        }
        signature = mix(signature, minY);
        signature = mix(signature, maxY);
        return new RegionSnapshot(level.dimension().toString(), minY, maxY, Map.copyOf(chunks), signature);
    }

    public static void invalidateAt(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        ChunkKey key = new ChunkKey(level.dimension().toString(), pos.getX() >> 4, pos.getZ() >> 4);
        CHUNK_VERSIONS.merge(key, 1L, Long::sum);
        CACHE.keySet().removeIf(snapshotKey -> snapshotKey.dimension().equals(key.dimension())
                && snapshotKey.chunkX() == key.chunkX()
                && snapshotKey.chunkZ() == key.chunkZ());
    }

    private static ChunkSnapshot captureChunk(ServerLevel level, ChunkKey key, int minY, int maxY, long version, long gameTime) {
        int height = maxY - minY + 1;
        BlockState[] blocks = new BlockState[16 * 16 * height];
        ChunkPos chunkPos = new ChunkPos(key.chunkX(), key.chunkZ());
        if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
            java.util.Arrays.fill(blocks, MISSING_STATE);
            return new ChunkSnapshot(key.dimension(), key.chunkX(), key.chunkZ(), minY, maxY, version, gameTime, false, blocks);
        }
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = (key.chunkX() << 4) + localX;
                int worldZ = (key.chunkZ() << 4) + localZ;
                for (int y = minY; y <= maxY; y++) {
                    int index = indexOf(localX, localZ, y - minY, height);
                    blocks[index] = level.getBlockState(new BlockPos(worldX, y, worldZ));
                }
            }
        }
        return new ChunkSnapshot(key.dimension(), key.chunkX(), key.chunkZ(), minY, maxY, version, gameTime, true, blocks);
    }

    private static int indexOf(int localX, int localZ, int yIndex, int height) {
        return ((localX * 16) + localZ) * height + yIndex;
    }

    private static int floorTo16(int value) {
        return Math.floorDiv(value, 16) * 16;
    }

    private static int ceilTo16(int value) {
        return Math.floorDiv(value + 15, 16) * 16;
    }

    private static long mix(long hash, long value) {
        hash ^= value + 0x9e3779b97f4a7c15L + (hash << 6) + (hash >> 2);
        return hash;
    }

    private record ChunkKey(String dimension, int chunkX, int chunkZ) {
    }

    private record SnapshotKey(String dimension, int chunkX, int chunkZ, int minY, int maxY, long version) {
    }

    private record ChunkSnapshot(
            String dimension,
            int chunkX,
            int chunkZ,
            int minY,
            int maxY,
            long version,
            long createdTick,
            boolean loaded,
            BlockState[] blocks) {
        private BlockState getBlockState(BlockPos pos) {
            if (!this.loaded || pos == null || pos.getY() < this.minY || pos.getY() > this.maxY) {
                return MISSING_STATE;
            }
            int localX = pos.getX() & 15;
            int localZ = pos.getZ() & 15;
            int height = this.maxY - this.minY + 1;
            int index = indexOf(localX, localZ, pos.getY() - this.minY, height);
            return index >= 0 && index < this.blocks.length ? this.blocks[index] : MISSING_STATE;
        }
    }

    public record RegionSnapshot(String dimension, int minY, int maxY, Map<ChunkKey, ChunkSnapshot> chunks, long signature) {
        private static final RegionSnapshot EMPTY = new RegionSnapshot("", 0, 0, Map.of(), 0L);

        public static RegionSnapshot empty() {
            return EMPTY;
        }

        public boolean isLoaded(BlockPos pos) {
            if (pos == null || this.chunks.isEmpty() || pos.getY() < this.minY || pos.getY() > this.maxY) {
                return false;
            }
            ChunkSnapshot snapshot = this.chunks.get(new ChunkKey(this.dimension, pos.getX() >> 4, pos.getZ() >> 4));
            return snapshot != null && snapshot.loaded();
        }

        public BlockState getBlockState(BlockPos pos) {
            if (pos == null) {
                return MISSING_STATE;
            }
            ChunkSnapshot snapshot = this.chunks.get(new ChunkKey(this.dimension, pos.getX() >> 4, pos.getZ() >> 4));
            return snapshot == null ? MISSING_STATE : snapshot.getBlockState(pos);
        }

        public FluidState getFluidState(BlockPos pos) {
            return this.getBlockState(pos).getFluidState();
        }
    }
}

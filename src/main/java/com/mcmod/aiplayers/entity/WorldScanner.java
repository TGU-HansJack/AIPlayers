package com.mcmod.aiplayers.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class WorldScanner {
    private static final Map<ChunkKey, ChunkCacheEntry> CACHE = new ConcurrentHashMap<>();

    private WorldScanner() {
    }

    public static PerceptionResult scanFor(AIPlayerEntity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return PerceptionResult.empty();
        }
        Set<ChunkPos> activeChunks = collectActiveChunks(entity);
        int focusY = entity.blockPosition().getY();
        long gameTime = serverLevel.getGameTime();
        for (ChunkPos chunkPos : activeChunks) {
            ensureFresh(serverLevel, chunkPos, focusY, gameTime);
        }

        BlockPos origin = entity.blockPosition();
        ResourceNode wood = findNearest(serverLevel, activeChunks, ResourceType.WOOD, origin);
        ResourceNode ore = findNearest(serverLevel, activeChunks, ResourceType.ORE, origin);
        ResourceNode crop = findNearest(serverLevel, activeChunks, ResourceType.CROP, origin);
        ResourceNode bed = findNearest(serverLevel, activeChunks, ResourceType.BED, origin);
        ResourceNode chest = findNearest(serverLevel, activeChunks, ResourceType.CHEST, origin);
        ResourceNode crafting = findNearest(serverLevel, activeChunks, ResourceType.CRAFTING, origin);
        ResourceNode furnace = findNearest(serverLevel, activeChunks, ResourceType.FURNACE, origin);
        ResourceNode hazard = findNearest(serverLevel, activeChunks, ResourceType.HAZARD, origin);

        if (wood != null) TeamKnowledge.reportResource(entity, ResourceType.WOOD, wood.pos());
        if (ore != null) TeamKnowledge.reportResource(entity, ResourceType.ORE, ore.pos());
        if (crop != null) TeamKnowledge.reportResource(entity, ResourceType.CROP, crop.pos());
        if (bed != null) TeamKnowledge.reportResource(entity, ResourceType.BED, bed.pos());
        if (chest != null) TeamKnowledge.reportResource(entity, ResourceType.CHEST, chest.pos());
        if (crafting != null) TeamKnowledge.reportResource(entity, ResourceType.CRAFTING, crafting.pos());
        if (furnace != null) TeamKnowledge.reportResource(entity, ResourceType.FURNACE, furnace.pos());

        List<String> notices = new ArrayList<>();
        if (wood != null) notices.add("木头@" + formatPos(wood.pos()));
        if (ore != null) notices.add("矿石@" + formatPos(ore.pos()));
        if (crop != null) notices.add("作物@" + formatPos(crop.pos()));
        if (bed != null) notices.add("床位@" + formatPos(bed.pos()));
        if (crafting != null) notices.add("工作台@" + formatPos(crafting.pos()));
        if (furnace != null) notices.add("熔炉@" + formatPos(furnace.pos()));
        if (chest != null) notices.add("储物@" + formatPos(chest.pos()));
        if (hazard != null) notices.add("危险液体@" + formatPos(hazard.pos()));
        return new PerceptionResult(wood, ore, crop, bed, chest, crafting, furnace, hazard != null, notices);
    }

    public static void invalidateAt(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        String dimension = level.dimension().toString();
        ChunkPos chunkPos = new ChunkPos(pos);
        CACHE.keySet().removeIf(key -> key.dimension.equals(dimension) && key.chunkX == chunkPos.x && key.chunkZ == chunkPos.z);
        PathCache.invalidateAt(level, pos);
    }

    private static Set<ChunkPos> collectActiveChunks(AIPlayerEntity entity) {
        Set<ChunkPos> result = new HashSet<>();
        addChunksAround(result, entity.blockPosition(), 1);
        Player owner = entity.getRuntimeOwnerPlayer();
        if (owner != null) {
            addChunksAround(result, owner.blockPosition(), 1);
        }
        return result;
    }

    private static void addChunksAround(Set<ChunkPos> result, BlockPos center, int radius) {
        ChunkPos origin = new ChunkPos(center);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                result.add(new ChunkPos(origin.x + dx, origin.z + dz));
            }
        }
    }

    private static void ensureFresh(ServerLevel level, ChunkPos chunkPos, int focusY, long gameTime) {
        ChunkKey key = new ChunkKey(level.dimension().toString(), chunkPos.x, chunkPos.z);
        ChunkCacheEntry cached = CACHE.get(key);
        long ttl = AgentConfigManager.getConfig().resourceCacheTtlSeconds() * 20L;
        if (cached != null && gameTime - cached.scannedTick <= ttl) {
            return;
        }
        CACHE.put(key, rescanChunk(level, chunkPos, focusY, gameTime));
    }

    private static ChunkCacheEntry rescanChunk(ServerLevel level, ChunkPos chunkPos, int focusY, long gameTime) {
        EnumMap<ResourceType, List<BlockPos>> resources = new EnumMap<>(ResourceType.class);
        int minY = Math.max(level.dimensionType().minY(), focusY - 16);
        int maxY = Math.min(level.dimensionType().minY() + level.dimensionType().height() - 1, focusY + 24);
        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    ResourceType type = classify(state);
                    if (type != null) {
                        resources.computeIfAbsent(type, ignored -> new ArrayList<>()).add(pos.immutable());
                    }
                }
            }
        }
        return new ChunkCacheEntry(gameTime, resources);
    }

    private static ResourceNode findNearest(ServerLevel level, Set<ChunkPos> chunks, ResourceType type, BlockPos origin) {
        double bestDistance = Double.MAX_VALUE;
        BlockPos bestPos = null;
        for (ChunkPos chunkPos : chunks) {
            ChunkCacheEntry entry = CACHE.get(new ChunkKey(level.dimension().toString(), chunkPos.x, chunkPos.z));
            if (entry == null) {
                continue;
            }
            List<BlockPos> nodes = entry.resources.get(type);
            if (nodes == null) {
                continue;
            }
            for (BlockPos pos : nodes) {
                double distance = Vec3.atCenterOf(pos).distanceToSqr(Vec3.atCenterOf(origin));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestPos = pos;
                }
            }
        }
        if (bestPos == null) {
            return null;
        }
        return new ResourceNode(type, bestPos, bestDistance, type.name());
    }

    private static ResourceType classify(BlockState state) {
        if (state == null || state.isAir()) {
            return null;
        }
        if (state.is(BlockTags.LOGS)) {
            return ResourceType.WOOD;
        }
        if (isInterestingOre(state)) {
            return ResourceType.ORE;
        }
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            return ResourceType.CROP;
        }
        if (state.getBlock() instanceof BedBlock) {
            return ResourceType.BED;
        }
        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL)) {
            return ResourceType.CHEST;
        }
        if (state.is(Blocks.CRAFTING_TABLE)) {
            return ResourceType.CRAFTING;
        }
        if (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)) {
            return ResourceType.FURNACE;
        }
        if (!state.getFluidState().isEmpty() && (state.getFluidState().isSource() || state.getFluidState().getAmount() >= 6)) {
            return ResourceType.HAZARD;
        }
        return null;
    }

    private static boolean isInterestingOre(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.contains("ore") || path.contains("ancient_debris");
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public record PerceptionResult(ResourceNode wood, ResourceNode ore, ResourceNode crop, ResourceNode bed, ResourceNode chest, ResourceNode crafting, ResourceNode furnace, boolean hazardNearby, List<String> notices) {
        public static PerceptionResult empty() {
            return new PerceptionResult(null, null, null, null, null, null, null, false, List.of());
        }
    }

    private record ChunkKey(String dimension, int chunkX, int chunkZ) {
    }

    private record ChunkCacheEntry(long scannedTick, Map<ResourceType, List<BlockPos>> resources) {
    }
}

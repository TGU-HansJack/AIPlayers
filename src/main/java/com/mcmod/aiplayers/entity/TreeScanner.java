package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.knowledge.KnowledgeManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

final class TreeScanner implements ResourceScanner<TreeTarget> {
    private static final int SCAN_RADIUS = 16;
    private static final int SCAN_DOWN = 4;
    private static final int SCAN_UP = 20;
    private static final int MAX_TRUNK_HEIGHT = 28;

    @Override
    public List<TreeTarget> scan(AIPlayerEntity entity, WorldModelSnapshot worldModel) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) {
            return List.of();
        }
        BlockPos origin = entity.blockPosition();
        Set<BlockPos> visitedBases = new HashSet<>();
        List<TreeTarget> trees = new ArrayList<>();
        BlockPos min = origin.offset(-SCAN_RADIUS, -SCAN_DOWN, -SCAN_RADIUS);
        BlockPos max = origin.offset(SCAN_RADIUS, SCAN_UP, SCAN_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!isTreeLog(level.getBlockState(pos))) {
                continue;
            }
            TreeTarget target = scanTree(level, entity, pos.immutable());
            if (target == null || !visitedBases.add(target.base())) {
                continue;
            }
            trees.add(target);
        }
        trees.sort(Comparator.comparingDouble(target -> entity.distanceToSqr(target.base().getX() + 0.5D, target.base().getY() + 0.5D, target.base().getZ() + 0.5D)));
        return trees;
    }

    TreeTarget scanTree(ServerLevel level, AIPlayerEntity entity, BlockPos seed) {
        if (level == null || seed == null) {
            return null;
        }
        BlockPos base = resolveTreeBase(level, seed);
        if (!isTreeLog(level.getBlockState(base))) {
            return null;
        }
        List<BlockPos> trunk = collectVerticalTrunk(level, base);
        if (trunk.isEmpty()) {
            return null;
        }
        List<BlockPos> leaves = collectCanopyLeaves(level, trunk.getLast());
        if (leaves.size() < 2) {
            return null;
        }
        List<BlockPos> stands = findStandPositions(entity, base, trunk);
        BoundingBox canopyBounds = buildBounds(trunk, leaves);
        return new TreeTarget(base, stands, trunk, leaves, canopyBounds);
    }

    private BlockPos resolveTreeBase(ServerLevel level, BlockPos seed) {
        BlockPos base = seed.immutable();
        int guard = 0;
        while (guard++ < 8 && isTreeLog(level.getBlockState(base.below()))) {
            base = base.below();
        }
        return base;
    }

    private List<BlockPos> collectVerticalTrunk(ServerLevel level, BlockPos base) {
        List<BlockPos> trunk = new ArrayList<>();
        BlockPos cursor = base;
        int guard = 0;
        while (guard++ < MAX_TRUNK_HEIGHT && isTreeLog(level.getBlockState(cursor))) {
            trunk.add(cursor.immutable());
            cursor = cursor.above();
        }
        return trunk;
    }

    private List<BlockPos> collectCanopyLeaves(ServerLevel level, BlockPos topLog) {
        List<BlockPos> leaves = new ArrayList<>();
        BlockPos canopyCenter = topLog.above();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos candidate = canopyCenter.offset(x, y, z);
                    if (KnowledgeManager.isTreeLeaves(level.getBlockState(candidate))) {
                        leaves.add(candidate.immutable());
                    }
                }
            }
        }
        return leaves;
    }

    private List<BlockPos> findStandPositions(AIPlayerEntity entity, BlockPos base, List<BlockPos> trunk) {
        List<BlockPos> stands = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = base.relative(direction);
            BlockPos resolved = entity.runtimeResolveMovementTarget(candidate);
            if (resolved == null) {
                resolved = candidate;
            }
            if (entity.runtimeCanStandAt(resolved, true)) {
                stands.add(resolved.immutable());
            }
        }
        if (stands.isEmpty() && !trunk.isEmpty()) {
            BlockPos approach = entity.runtimeFindApproachPosition(trunk.getFirst());
            if (approach != null) {
                stands.add(approach.immutable());
            }
        }
        return stands;
    }

    private BoundingBox buildBounds(List<BlockPos> trunk, List<BlockPos> leaves) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : trunk) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        for (BlockPos pos : leaves) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private boolean isTreeLog(BlockState state) {
        return state != null && !state.isAir() && (KnowledgeManager.isTreeLog(state) || state.is(BlockTags.LOGS));
    }
}

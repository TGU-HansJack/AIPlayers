package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.knowledge.KnowledgeManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public final class TreeChopper {
    private static final int MAX_HORIZONTAL_RADIUS = 5;
    private static final int MAX_UP_SCAN = 28;
    private static final int MAX_DOWN_SCAN = 6;
    private static final int MAX_VISITED = 768;

    private TreeChopper() {
    }

    public static List<BlockPos> collectTreeLogs(ServerLevel level, BlockPos seed) {
        if (level == null || seed == null) {
            return List.of();
        }

        BlockPos base = resolveTreeBase(level, seed);
        if (!isTreeLog(level.getBlockState(base))) {
            return List.of();
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> logs = new ArrayList<>();

        queue.add(base);
        visited.add(base);

        while (!queue.isEmpty() && visited.size() < MAX_VISITED) {
            BlockPos current = queue.poll();
            BlockState currentState = level.getBlockState(current);
            if (!isTreeLog(currentState)) {
                continue;
            }
            logs.add(current.immutable());

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (Math.abs(next.getX() - base.getX()) > MAX_HORIZONTAL_RADIUS
                        || Math.abs(next.getZ() - base.getZ()) > MAX_HORIZONTAL_RADIUS) {
                    continue;
                }
                if (next.getY() < base.getY() - MAX_DOWN_SCAN || next.getY() > base.getY() + MAX_UP_SCAN) {
                    continue;
                }
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }

        logs.sort(Comparator
                .comparingInt((BlockPos pos) -> pos.getY())
                .thenComparingInt(pos -> Math.abs(pos.getX() - base.getX()) + Math.abs(pos.getZ() - base.getZ()))
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return logs;
    }

    public static BlockPos nextTreeLog(ServerLevel level, BlockPos seed, Predicate<BlockPos> filter) {
        List<BlockPos> logs = collectTreeLogs(level, seed);
        if (logs.isEmpty()) {
            return null;
        }
        Predicate<BlockPos> resolvedFilter = filter == null ? pos -> true : filter;
        for (BlockPos candidate : logs) {
            if (resolvedFilter.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static BlockPos resolveTreeBase(ServerLevel level, BlockPos seed) {
        BlockPos anchor = findNearbyLog(level, seed);
        BlockPos base = anchor;
        int guard = 0;
        while (guard < 32 && isTreeLog(level.getBlockState(base.below()))) {
            base = base.below();
            guard++;
        }
        return base;
    }

    private static BlockPos findNearbyLog(ServerLevel level, BlockPos seed) {
        if (isTreeLog(level.getBlockState(seed))) {
            return seed.immutable();
        }
        BlockPos best = seed.immutable();
        double bestDistance = Double.MAX_VALUE;
        for (int x = -2; x <= 2; x++) {
            for (int y = -3; y <= 4; y++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos candidate = seed.offset(x, y, z);
                    if (!isTreeLog(level.getBlockState(candidate))) {
                        continue;
                    }
                    double distance = seed.distSqr(candidate);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate.immutable();
                    }
                }
            }
        }
        return best;
    }

    private static boolean isTreeLog(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return KnowledgeManager.isTreeLog(state) || state.is(BlockTags.LOGS);
    }
}

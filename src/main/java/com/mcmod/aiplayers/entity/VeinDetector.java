package com.mcmod.aiplayers.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

final class VeinDetector {
    List<ResourceCluster> detectVeins(AIPlayerEntity entity, List<BlockPos> oreNodes) {
        if (entity == null || oreNodes == null || oreNodes.isEmpty() || !(entity.level() instanceof ServerLevel level)) {
            return List.of();
        }
        Set<BlockPos> visited = new HashSet<>();
        List<ResourceCluster> clusters = new ArrayList<>();
        for (BlockPos node : oreNodes) {
            if (node == null || !visited.add(node)) {
                continue;
            }
            ResourceCluster cluster = bfs(level, entity, node, visited);
            if (cluster != null && !cluster.blocks().isEmpty()) {
                clusters.add(cluster);
            }
        }
        return clusters;
    }

    private ResourceCluster bfs(ServerLevel level, AIPlayerEntity entity, BlockPos seed, Set<BlockPos> visited) {
        BlockState seedState = level.getBlockState(seed);
        String blockId = BuiltInRegistries.BLOCK.getKey(seedState.getBlock()).toString();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> blocks = new ArrayList<>();
        Set<BlockPos> standPositions = new HashSet<>();
        int exposedFaces = 0;
        int blockingBlocks = 0;
        queue.add(seed);
        while (!queue.isEmpty() && blocks.size() < 96) {
            BlockPos current = queue.poll();
            BlockState state = level.getBlockState(current);
            String currentId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if (!blockId.equals(currentId)) {
                continue;
            }
            blocks.add(current.immutable());
            boolean exposed = false;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.relative(direction);
                BlockState neighborState = level.getBlockState(neighbor);
                String neighborId = BuiltInRegistries.BLOCK.getKey(neighborState.getBlock()).toString();
                if (blockId.equals(neighborId)) {
                    if (visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                    continue;
                }
                if (neighborState.isAir() || neighborState.getCollisionShape(level, neighbor).isEmpty()) {
                    exposedFaces++;
                    exposed = true;
                    BlockPos stand = entity.runtimeResolveMovementTarget(neighbor);
                    if (stand != null && entity.runtimeCanStandAt(stand, true)) {
                        standPositions.add(stand.immutable());
                    }
                } else {
                    blockingBlocks++;
                    BlockPos stand = entity.runtimeFindApproachPosition(neighbor);
                    if (stand != null) {
                        standPositions.add(stand.immutable());
                    }
                }
            }
            if (!exposed) {
                blockingBlocks++;
            }
        }
        return new ResourceCluster(ResourceKind.ORE, seed, blocks, List.copyOf(standPositions), exposedFaces, blockingBlocks);
    }
}

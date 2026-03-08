package com.mcmod.aiplayers.entity;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;

final class OreSelector implements ResourceSelector<ResourceCluster> {
    @Override
    public ResourceCluster select(AIPlayerEntity entity, List<ResourceCluster> candidates, SharedMemorySnapshot sharedMemory) {
        if (entity == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .sorted(Comparator
                        .comparing((ResourceCluster cluster) -> cluster.standPositions().isEmpty())
                        .thenComparing(Comparator.comparingInt(ResourceCluster::exposedFaces).reversed())
                        .thenComparingInt(ResourceCluster::blockingBlocks)
                        .thenComparingInt(cluster -> failureCount(sharedMemory, cluster.seed()))
                        .thenComparingDouble(cluster -> entity.distanceToSqr(cluster.seed().getX() + 0.5D, cluster.seed().getY() + 0.5D, cluster.seed().getZ() + 0.5D)))
                .findFirst()
                .orElse(null);
    }

    private int failureCount(SharedMemorySnapshot sharedMemory, BlockPos target) {
        if (sharedMemory == null || target == null) {
            return 0;
        }
        int failures = 0;
        String needle = target.getX() + "," + target.getY() + "," + target.getZ();
        for (String note : sharedMemory.recentFailures()) {
            if (note != null && note.contains(needle)) {
                failures++;
            }
        }
        return failures;
    }
}

package com.mcmod.aiplayers.entity;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;

final class TreeSelector implements ResourceSelector<TreeTarget> {
    @Override
    public TreeTarget select(AIPlayerEntity entity, List<TreeTarget> candidates, SharedMemorySnapshot sharedMemory) {
        if (entity == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .sorted(Comparator
                        .comparing((TreeTarget target) -> target.standPositions().isEmpty())
                        .thenComparingDouble(target -> entity.distanceToSqr(target.base().getX() + 0.5D, target.base().getY() + 0.5D, target.base().getZ() + 0.5D))
                        .thenComparing(Comparator.comparingInt(TreeTarget::height).reversed())
                        .thenComparingInt(target -> failureCount(sharedMemory, target.base())))
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

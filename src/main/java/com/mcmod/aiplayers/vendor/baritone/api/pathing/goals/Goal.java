package com.mcmod.aiplayers.vendor.baritone.api.pathing.goals;

import net.minecraft.core.BlockPos;

// Upstream reference: baritone-1.21.11/src/api/java/baritone/api/pathing/goals/Goal.java
public interface Goal {
    boolean isInGoal(int x, int y, int z);

    double heuristic(int x, int y, int z);

    default boolean isInGoal(BlockPos pos) {
        return pos != null && this.isInGoal(pos.getX(), pos.getY(), pos.getZ());
    }

    default double heuristic(BlockPos pos) {
        return pos == null ? Double.MAX_VALUE : this.heuristic(pos.getX(), pos.getY(), pos.getZ());
    }

    default double heuristic() {
        return 0.0D;
    }
}

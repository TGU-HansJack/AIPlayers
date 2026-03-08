package com.mcmod.aiplayers.vendor.baritone.api.pathing.goals;

import net.minecraft.core.BlockPos;

// Upstream reference: baritone-1.21.11/src/api/java/baritone/api/pathing/goals/GoalNear.java
public class GoalNear implements PathGoal {
    public final int x;
    public final int y;
    public final int z;
    public final int rangeSq;

    public GoalNear(BlockPos pos, int range) {
        this(pos.getX(), pos.getY(), pos.getZ(), range * range);
    }

    public GoalNear(int x, int y, int z, int rangeSq) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.rangeSq = Math.max(0, rangeSq);
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        int dx = x - this.x;
        int dy = y - this.y;
        int dz = z - this.z;
        return dx * dx + dy * dy + dz * dz <= this.rangeSq;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        if (this.isInGoal(x, y, z)) {
            return 0.0D;
        }
        return GoalBlock.calculate(x - this.x, y - this.y, z - this.z);
    }

    public BlockPos getGoalPos() {
        return new BlockPos(this.x, this.y, this.z);
    }
}

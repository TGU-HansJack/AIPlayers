package com.mcmod.aiplayers.vendor.baritone.api.pathing.goals;

import net.minecraft.core.BlockPos;

// Upstream reference: baritone-1.21.11/src/api/java/baritone/api/pathing/goals/GoalBlock.java
public class GoalBlock implements PathGoal {
    public final int x;
    public final int y;
    public final int z;

    public GoalBlock(BlockPos pos) {
        this(pos.getX(), pos.getY(), pos.getZ());
    }

    public GoalBlock(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return x == this.x && y == this.y && z == this.z;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        return calculate(x - this.x, y - this.y, z - this.z);
    }

    public BlockPos getGoalPos() {
        return new BlockPos(this.x, this.y, this.z);
    }

    public static double calculate(int xDiff, int yDiff, int zDiff) {
        int dx = Math.abs(xDiff);
        int dz = Math.abs(zDiff);
        int dy = Math.abs(yDiff);
        int diagonal = Math.min(dx, dz);
        int straight = Math.max(dx, dz) - diagonal;
        return diagonal * 1.4142D + straight + dy * 1.45D;
    }
}

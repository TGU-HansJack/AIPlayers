package com.mcmod.aiplayers.vendor.baritone.pathing.movement;

import com.mcmod.aiplayers.entity.BaritoneEntityContextAdapter;
import com.mcmod.aiplayers.entity.BaritoneMovementExecutorAdapter;
import net.minecraft.core.BlockPos;

// Upstream reference: baritone-1.21.11/src/main/java/baritone/pathing/movement/Movement.java
public abstract class Movement {
    protected final BlockPos src;
    protected final BlockPos dest;
    protected final BlockPos actionPos;
    protected final double cost;

    protected Movement(BlockPos src, BlockPos dest, BlockPos actionPos, double cost) {
        this.src = src == null ? BlockPos.ZERO : src.immutable();
        this.dest = dest == null ? BlockPos.ZERO : dest.immutable();
        this.actionPos = actionPos == null ? null : actionPos.immutable();
        this.cost = cost;
    }

    public BlockPos getSrc() {
        return this.src;
    }

    public BlockPos getDest() {
        return this.dest;
    }

    public BlockPos getActionPos() {
        return this.actionPos;
    }

    public double getCost() {
        return this.cost;
    }

    public boolean jumpRequired() {
        return false;
    }

    public String name() {
        return this.getClass().getSimpleName();
    }

    protected MovementStatus moveTowards(BaritoneMovementExecutorAdapter executor, BlockPos pos, double speed, boolean jump) {
        if (executor.hasReached(pos)) {
            return MovementStatus.SUCCESS;
        }
        executor.moveToward(pos, speed, jump);
        return MovementStatus.RUNNING;
    }

    public void reset() {
    }

    public abstract MovementStatus update(BaritoneEntityContextAdapter context, BaritoneMovementExecutorAdapter executor);
}

package com.mcmod.aiplayers.vendor.baritone.pathing.movement.movements;

import com.mcmod.aiplayers.entity.BaritoneEntityContextAdapter;
import com.mcmod.aiplayers.entity.BaritoneMovementExecutorAdapter;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.Movement;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.MovementStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

// Upstream reference: baritone-1.21.11/src/main/java/baritone/pathing/movement/movements/MovementPlace.java
public class MovementPlaceAndTraverse extends Movement {
    private static final long ACTION_RETRY_TICKS = 4L;

    private final boolean jumpRequired;
    private long lastActionTick = Long.MIN_VALUE;

    public MovementPlaceAndTraverse(BlockPos src, BlockPos dest, BlockPos supportPos, double cost, boolean jumpRequired) {
        super(src, dest, supportPos, cost);
        this.jumpRequired = jumpRequired;
    }

    @Override
    public boolean jumpRequired() {
        return this.jumpRequired;
    }

    @Override
    public void reset() {
        this.lastActionTick = Long.MIN_VALUE;
    }

    @Override
    public MovementStatus update(BaritoneEntityContextAdapter context, BaritoneMovementExecutorAdapter executor) {
        if (executor.hasReached(this.dest)) {
            return MovementStatus.SUCCESS;
        }
        if (!this.hasSupport(context)) {
            if (!executor.isWithin(this.src, 4.0D)) {
                executor.moveToward(this.src, 0.88D, false);
                return MovementStatus.RUNNING;
            }
            if (!executor.canPlaceSupport(this.actionPos)) {
                return MovementStatus.BLOCKED;
            }
            executor.lookAt(this.actionPos);
            if (context.gameTime() - this.lastActionTick >= ACTION_RETRY_TICKS) {
                executor.placeSupport(this.actionPos);
                this.lastActionTick = context.gameTime();
            }
            if (!this.hasSupport(context)) {
                return MovementStatus.RUNNING;
            }
        }
        return this.moveTowards(executor, this.dest, 0.96D, this.jumpRequired);
    }

    private boolean hasSupport(BaritoneEntityContextAdapter context) {
        if (this.actionPos == null) {
            return true;
        }
        BlockState state = context.level().getBlockState(this.actionPos);
        return !state.isAir() && !state.getCollisionShape(context.level(), this.actionPos).isEmpty();
    }
}

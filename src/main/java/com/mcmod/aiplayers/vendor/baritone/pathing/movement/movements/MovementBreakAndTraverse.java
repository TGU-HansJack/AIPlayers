package com.mcmod.aiplayers.vendor.baritone.pathing.movement.movements;

import com.mcmod.aiplayers.entity.BaritoneEntityContextAdapter;
import com.mcmod.aiplayers.entity.BaritoneMovementExecutorAdapter;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.CalculationContext;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.Movement;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.MovementStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

// Upstream reference: baritone-1.21.11/src/main/java/baritone/pathing/movement/movements/MovementBreak.java
public class MovementBreakAndTraverse extends Movement {
    private static final long ACTION_RETRY_TICKS = 4L;
    private long lastActionTick = Long.MIN_VALUE;

    public MovementBreakAndTraverse(BlockPos src, BlockPos dest, BlockPos breakPos, double cost) {
        super(src, dest, breakPos, cost);
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
        if (!this.isCleared(context)) {
            if (!executor.isWithin(this.src, 4.0D)) {
                executor.moveToward(this.src, 0.9D, false);
                return MovementStatus.RUNNING;
            }
            if (!executor.canBreakBlock(this.actionPos)) {
                return MovementStatus.BLOCKED;
            }
            executor.lookAt(this.actionPos);
            if (context.gameTime() - this.lastActionTick >= ACTION_RETRY_TICKS) {
                executor.breakBlock(this.actionPos);
                this.lastActionTick = context.gameTime();
            }
            return this.isCleared(context) ? MovementStatus.RUNNING : MovementStatus.RUNNING;
        }
        return this.moveTowards(executor, this.dest, 1.0D, false);
    }

    private boolean isCleared(BaritoneEntityContextAdapter context) {
        if (this.actionPos == null) {
            return true;
        }
        BlockState state = context.level().getBlockState(this.actionPos);
        return state.isAir() || state.getCollisionShape(context.level(), this.actionPos).isEmpty();
    }
}

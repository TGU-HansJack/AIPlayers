package com.mcmod.aiplayers.vendor.baritone.pathing.movement.movements;

import com.mcmod.aiplayers.entity.BaritoneEntityContextAdapter;
import com.mcmod.aiplayers.entity.BaritoneMovementExecutorAdapter;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.Movement;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.MovementStatus;
import net.minecraft.core.BlockPos;

// Upstream reference: baritone-1.21.11/src/main/java/baritone/pathing/movement/movements/MovementAscend.java
public class MovementAscend extends Movement {
    public MovementAscend(BlockPos src, BlockPos dest, double cost) {
        super(src, dest, null, cost);
    }

    @Override
    public boolean jumpRequired() {
        return true;
    }

    @Override
    public MovementStatus update(BaritoneEntityContextAdapter context, BaritoneMovementExecutorAdapter executor) {
        return this.moveTowards(executor, this.dest, 1.0D, true);
    }
}

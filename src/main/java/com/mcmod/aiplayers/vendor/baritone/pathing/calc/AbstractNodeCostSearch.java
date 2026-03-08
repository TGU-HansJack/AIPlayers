package com.mcmod.aiplayers.vendor.baritone.pathing.calc;

import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.Goal;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.CalculationContext;
import net.minecraft.core.BlockPos;

// Upstream reference: baritone-1.21.11/src/main/java/baritone/pathing/calc/AbstractNodeCostSearch.java
public abstract class AbstractNodeCostSearch {
    protected final BlockPos start;
    protected final Goal goal;
    protected final CalculationContext context;
    protected boolean cancelRequested;

    protected AbstractNodeCostSearch(BlockPos start, Goal goal, CalculationContext context) {
        this.start = start == null ? BlockPos.ZERO : start.immutable();
        this.goal = goal;
        this.context = context;
    }

    public PathCalculationResult calculate(long primaryTimeoutMs, long failureTimeoutMs) {
        if (this.cancelRequested) {
            return new PathCalculationResult(PathCalculationResult.Type.CANCELLED, null);
        }
        return this.calculate0(primaryTimeoutMs, failureTimeoutMs);
    }

    public void cancel() {
        this.cancelRequested = true;
    }

    protected abstract PathCalculationResult calculate0(long primaryTimeoutMs, long failureTimeoutMs);
}

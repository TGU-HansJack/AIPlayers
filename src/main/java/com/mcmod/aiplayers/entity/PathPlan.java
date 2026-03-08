package com.mcmod.aiplayers.entity;

import java.util.List;
import net.minecraft.core.BlockPos;

public record PathPlan(BlockPos target, List<PathStep> steps, String status) {
    public PathPlan {
        target = target == null ? null : target.immutable();
        steps = steps == null ? List.of() : List.copyOf(steps);
        status = status == null ? "idle" : status;
    }

    public boolean isEmpty() {
        return this.steps.isEmpty();
    }
}

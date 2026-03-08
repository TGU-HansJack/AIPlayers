package com.mcmod.aiplayers.entity;

import net.minecraft.core.BlockPos;

public record ResourcePlan(
        GatherResourceTask task,
        ResourcePlanStep step,
        String status,
        int gatheredAmount,
        BlockPos focusPos) {
    public ResourcePlan {
        task = task == null ? new GatherResourceTask(ResourceKind.TREE, 1, "", "", false, "brain") : task;
        step = step == null ? ResourcePlanStep.SCAN : step;
        status = status == null ? "" : status;
        gatheredAmount = Math.max(0, gatheredAmount);
        focusPos = focusPos == null ? null : focusPos.immutable();
    }
}

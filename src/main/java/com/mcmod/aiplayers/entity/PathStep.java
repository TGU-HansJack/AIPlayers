package com.mcmod.aiplayers.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public record PathStep(
        Vec3 position,
        PathPrimitive primitive,
        BlockPos actionPos,
        boolean jumpRequired) {
    public PathStep {
        primitive = primitive == null ? PathPrimitive.WALK : primitive;
        actionPos = actionPos == null ? null : actionPos.immutable();
    }
}

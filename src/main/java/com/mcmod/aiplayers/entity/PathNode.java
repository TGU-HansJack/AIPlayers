package com.mcmod.aiplayers.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public record PathNode(Vec3 position, PathNodeAction action, BlockPos actionPos, boolean jumpRequired) {
    public PathNode {
        action = action == null ? PathNodeAction.NONE : action;
        actionPos = actionPos == null ? null : actionPos.immutable();
    }

    public PathNode(Vec3 position) {
        this(position, PathNodeAction.NONE, null, false);
    }

    public static PathNode move(Vec3 position) {
        return new PathNode(position, PathNodeAction.NONE, null, false);
    }

    public static PathNode withAction(Vec3 position, PathNodeAction action, BlockPos actionPos, boolean jumpRequired) {
        return new PathNode(position, action, actionPos, jumpRequired);
    }
}

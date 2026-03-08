package com.mcmod.aiplayers.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class BaritoneEntityContextAdapter {
    private final AIPlayerEntity entity;
    private final BaritoneBlockStateInterfaceAdapter blockStateInterface;
    private final BaritonePlayerControllerAdapter playerController;

    public BaritoneEntityContextAdapter(AIPlayerEntity entity) {
        this.entity = entity;
        this.blockStateInterface = new BaritoneBlockStateInterfaceAdapter(entity);
        this.playerController = new BaritonePlayerControllerAdapter(entity);
    }

    public AIPlayerEntity entity() {
        return this.entity;
    }

    public ServerLevel level() {
        return (ServerLevel) this.entity.level();
    }

    public BlockPos playerFeet() {
        return this.entity.blockPosition();
    }

    public Vec3 position() {
        return this.entity.position();
    }

    public Vec3 velocity() {
        return this.entity.getDeltaMovement();
    }

    public float yaw() {
        return this.entity.getYRot();
    }

    public boolean onGround() {
        return this.entity.onGround();
    }

    public boolean isInWater() {
        return this.entity.isInWater() || this.entity.isUnderWater();
    }

    public boolean isInLava() {
        return this.entity.isInLava();
    }

    public boolean horizontalCollision() {
        return this.entity.horizontalCollision;
    }

    public long gameTime() {
        return this.entity.level().getGameTime();
    }

    public BlockPos resolveMovementTarget(BlockPos pos) {
        return this.entity.runtimeResolveMovementTarget(pos);
    }

    public boolean canStandAt(BlockPos pos, boolean allowWater) {
        return this.entity.runtimeCanStandAt(pos, allowWater);
    }

    public boolean hasLowCeiling() {
        return this.entity.runtimeHasLowCeiling();
    }

    public BlockPos findNearbyDryStandPosition(BlockPos center, int horizontalRadius, int verticalRadius) {
        return this.entity.runtimeFindNearbyDryStandPosition(center, horizontalRadius, verticalRadius);
    }

    public BaritoneBlockStateInterfaceAdapter blockStateInterface() {
        return this.blockStateInterface;
    }

    public BaritonePlayerControllerAdapter playerController() {
        return this.playerController;
    }
}

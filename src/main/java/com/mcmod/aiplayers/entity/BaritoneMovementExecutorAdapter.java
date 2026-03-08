package com.mcmod.aiplayers.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class BaritoneMovementExecutorAdapter {
    private final BaritoneEntityContextAdapter context;
    private final BaritonePlayerControllerAdapter controller;
    private double speedScale = 1.0D;

    public BaritoneMovementExecutorAdapter(BaritoneEntityContextAdapter context, BaritonePlayerControllerAdapter controller) {
        this.context = context;
        this.controller = controller;
    }

    public void setSpeedScale(double speedScale) {
        this.speedScale = Math.max(0.25D, Math.min(2.0D, speedScale));
    }

    public double speedScale() {
        return this.speedScale;
    }

    public void moveToward(BlockPos pos, double speed, boolean jump) {
        this.controller.moveToward(Vec3.atCenterOf(pos), this.applySpeedScale(speed), jump);
    }

    public void moveToward(Vec3 pos, double speed, boolean jump) {
        this.controller.moveToward(pos, this.applySpeedScale(speed), jump);
    }

    public void lookAt(BlockPos pos) {
        this.controller.lookAt(pos);
    }

    public boolean breakBlock(BlockPos pos) {
        return this.controller.breakBlock(pos);
    }

    public boolean placeSupport(BlockPos pos) {
        return this.controller.placeSupport(pos);
    }

    public boolean canBreakBlock(BlockPos pos) {
        return this.controller.canBreakBlock(pos);
    }

    public boolean canPlaceSupport(BlockPos pos) {
        return this.controller.canPlaceSupport(pos);
    }

    public boolean isWithin(BlockPos pos, double distanceSqr) {
        return this.controller.isWithin(pos, distanceSqr);
    }

    public boolean hasReached(BlockPos pos) {
        return this.context.entity().runtimeIsWithin(pos, 0.64D);
    }

    public void clearInput() {
        this.controller.clearInput();
    }

    private double applySpeedScale(double speed) {
        return Math.max(0.1D, speed * this.speedScale);
    }
}

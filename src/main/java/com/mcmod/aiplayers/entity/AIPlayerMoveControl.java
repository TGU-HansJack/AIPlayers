package com.mcmod.aiplayers.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class AIPlayerMoveControl extends MoveControl {
    private static final float MAX_TURN_DEGREES = 16.0F;
    private static final float BRAKE_TURN_DEGREES = 62.0F;
    private static final float NEAR_TARGET_SLOWDOWN = 0.72F;
    private static final float VERY_NEAR_TARGET_SLOWDOWN = 0.48F;
    private static final float MIN_FORWARD_INPUT = 0.18F;
    private static final float MAX_STRAFE_INPUT = 0.82F;
    private static final float FORWARD_LERP = 0.38F;
    private static final float STRAFE_LERP = 0.30F;
    private static final float SPEED_LERP = 0.16F;
    private static final double JUMP_HORIZONTAL_DISTANCE = 1.45D;
    private static final double FRONT_PROBE_DISTANCE = 0.75D;
    private static final double WATER_ASCENT_SPEED = 0.04D;
    private static final float WALK_SPEED_CAP = 0.10F;
    private static final float SPRINT_SPEED_CAP = 0.13F;
    private static final float SNEAK_SPEED_CAP = 0.05F;
    private static final float WATER_SPEED_CAP = 0.065F;

    private final AIPlayerEntity companion;
    private float smoothedForward;
    private float smoothedStrafe;
    private float smoothedSpeed;

    AIPlayerMoveControl(AIPlayerEntity companion) {
        super(companion);
        this.companion = companion;
    }

    @Override
    public void tick() {
        this.companion.setXxa(0.0F);
        this.companion.setZza(0.0F);

        if (this.operation != Operation.MOVE_TO) {
            this.smoothedForward = Mth.lerp(0.35F, this.smoothedForward, 0.0F);
            this.smoothedStrafe = Mth.lerp(0.35F, this.smoothedStrafe, 0.0F);
            this.smoothedSpeed = Mth.lerp(0.4F, this.smoothedSpeed, 0.0F);
            this.companion.setSpeed(this.smoothedSpeed);
            this.companion.setSprinting(false);
            return;
        }

        this.operation = Operation.WAIT;
        double dx = this.wantedX - this.companion.getX();
        double dy = this.wantedY - this.companion.getY();
        double dz = this.wantedZ - this.companion.getZ();
        double horizontalDistanceSqr = dx * dx + dz * dz;
        double distanceSqr = horizontalDistanceSqr + dy * dy;
        if (distanceSqr < 2.5E-3D) {
            this.smoothedForward = 0.0F;
            this.smoothedStrafe = 0.0F;
            this.smoothedSpeed = 0.0F;
            this.companion.setSpeed(0.0F);
            this.companion.setSprinting(false);
            return;
        }

        float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0F / (float) Math.PI)) - 90.0F;
        float currentYaw = this.companion.getYRot();
        float nextYaw = this.rotlerp(currentYaw, targetYaw, MAX_TURN_DEGREES);
        this.companion.setYRot(nextYaw);
        this.companion.setYHeadRot(nextYaw);
        this.companion.setYBodyRot(nextYaw);
        this.companion.getLookControl().setLookAt(this.wantedX, this.wantedY + 0.6D, this.wantedZ, 30.0F, 30.0F);

        double attributeSpeed = this.companion.getAttributeValue(Attributes.MOVEMENT_SPEED);
        float baseSpeed = (float) Mth.clamp(this.speedModifier * attributeSpeed, MIN_SPEED, 0.18D);
        float yawDelta = Mth.wrapDegrees(targetYaw - nextYaw);
        float absYawDelta = Math.abs(yawDelta);
        float turnFactor = Mth.clamp(1.0F - absYawDelta / 110.0F, 0.22F, 1.0F);
        float targetSpeed = baseSpeed * turnFactor;
        float maxAllowedSpeed = this.speedModifier > 1.08D ? SPRINT_SPEED_CAP : WALK_SPEED_CAP;
        if (this.companion.isShiftKeyDown()) {
            maxAllowedSpeed = SNEAK_SPEED_CAP;
        }
        if (this.companion.isInWater() || this.companion.isUnderWater() || this.companion.isInLava()) {
            maxAllowedSpeed = Math.min(maxAllowedSpeed, WATER_SPEED_CAP);
        }
        if (horizontalDistanceSqr < 1.25D) {
            targetSpeed *= NEAR_TARGET_SLOWDOWN;
        }
        if (horizontalDistanceSqr < 0.45D) {
            targetSpeed *= VERY_NEAR_TARGET_SLOWDOWN;
        }
        if (absYawDelta > BRAKE_TURN_DEGREES) {
            targetSpeed *= 0.58F;
        }
        targetSpeed = Math.min(targetSpeed, maxAllowedSpeed);

        float radians = yawDelta * ((float) Math.PI / 180.0F);
        float targetForward = Mth.clamp(Mth.cos(radians), MIN_FORWARD_INPUT, 1.0F);
        float targetStrafe = Mth.clamp(-Mth.sin(radians), -MAX_STRAFE_INPUT, MAX_STRAFE_INPUT);
        if (absYawDelta > BRAKE_TURN_DEGREES) {
            targetForward *= 0.45F;
            targetStrafe *= 0.55F;
        }

        this.smoothedSpeed = Mth.lerp(SPEED_LERP, this.smoothedSpeed, targetSpeed);
        this.smoothedForward = Mth.lerp(FORWARD_LERP, this.smoothedForward, targetForward);
        this.smoothedStrafe = Mth.lerp(STRAFE_LERP, this.smoothedStrafe, targetStrafe);

        this.companion.setSpeed(this.smoothedSpeed);
        this.companion.setZza(this.smoothedForward);
        this.companion.setXxa(this.smoothedStrafe);
        double horizontalDistance = Math.sqrt(horizontalDistanceSqr);
        this.companion.setSprinting(this.speedModifier > 1.08D
                && absYawDelta < 28.0F
                && horizontalDistance > 4.0D
                && !(this.companion.isInWater() || this.companion.isUnderWater() || this.companion.isInLava()));

        if (shouldJump(dx, dy, dz, horizontalDistance)) {
            this.companion.getJumpControl().jump();
        }

        handleLiquidAscent(dy);
    }

    private boolean shouldJump(double dx, double dy, double dz, double horizontalDistance) {
        if (!this.companion.onGround()) {
            return false;
        }
        if (this.companion.isInWater() || this.companion.isUnderWater() || this.companion.isInLava()) {
            return false;
        }
        if (dy > this.companion.maxUpStep() - 0.1D && horizontalDistance <= JUMP_HORIZONTAL_DISTANCE) {
            return true;
        }
        if (horizontalDistance > JUMP_HORIZONTAL_DISTANCE) {
            return false;
        }

        Vec3 horizontal = new Vec3(dx, 0.0D, dz);
        if (horizontal.lengthSqr() < 1.0E-4D) {
            return this.companion.horizontalCollision && !this.companion.minorHorizontalCollision;
        }

        Vec3 forward = horizontal.normalize().scale(FRONT_PROBE_DISTANCE);
        BlockPos feetPos = BlockPos.containing(this.companion.getX() + forward.x, this.companion.getY() + 0.05D, this.companion.getZ() + forward.z);
        BlockPos bodyPos = feetPos.above();
        BlockState feetState = this.companion.level().getBlockState(feetPos);
        BlockState bodyState = this.companion.level().getBlockState(bodyPos);
        boolean feetBlocked = !feetState.getCollisionShape(this.companion.level(), feetPos).isEmpty();
        boolean bodyBlocked = !bodyState.getCollisionShape(this.companion.level(), bodyPos).isEmpty();
        if (feetBlocked && !bodyBlocked) {
            return true;
        }
        return this.companion.horizontalCollision && !this.companion.minorHorizontalCollision;
    }

    private void handleLiquidAscent(double dy) {
        if (!(this.companion.isInWater() || this.companion.isUnderWater() || this.companion.isInLava())) {
            return;
        }
        if (dy > -0.05D || this.companion.horizontalCollision || this.companion.isUnderWater()) {
            Vec3 motion = this.companion.getDeltaMovement();
            this.companion.setDeltaMovement(motion.x * 0.9D, Math.max(motion.y, WATER_ASCENT_SPEED), motion.z * 0.9D);
        }
    }
}

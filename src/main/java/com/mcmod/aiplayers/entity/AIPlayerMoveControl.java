package com.mcmod.aiplayers.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class AIPlayerMoveControl extends MoveControl {
    private static final float MAX_TURN_DEGREES = 14.0F;
    private static final float NEAR_TARGET_SLOWDOWN = 0.72F;
    private static final float MIN_FORWARD_INPUT = 0.3F;
    private static final double JUMP_HORIZONTAL_DISTANCE = 1.45D;
    private static final double FRONT_PROBE_DISTANCE = 0.75D;
    private static final double WATER_ASCENT_SPEED = 0.08D;

    private final AIPlayerEntity companion;

    AIPlayerMoveControl(AIPlayerEntity companion) {
        super(companion);
        this.companion = companion;
    }

    @Override
    public void tick() {
        this.companion.setXxa(0.0F);
        this.companion.setZza(0.0F);

        if (this.operation != Operation.MOVE_TO) {
            this.companion.setSpeed(0.0F);
            return;
        }

        this.operation = Operation.WAIT;
        double dx = this.wantedX - this.companion.getX();
        double dy = this.wantedY - this.companion.getY();
        double dz = this.wantedZ - this.companion.getZ();
        double horizontalDistanceSqr = dx * dx + dz * dz;
        double distanceSqr = horizontalDistanceSqr + dy * dy;
        if (distanceSqr < 2.5E-3D) {
            this.companion.setSpeed(0.0F);
            return;
        }

        float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0F / (float) Math.PI)) - 90.0F;
        float currentYaw = this.companion.getYRot();
        float nextYaw = this.rotlerp(currentYaw, targetYaw, MAX_TURN_DEGREES);
        this.companion.setYRot(nextYaw);
        this.companion.setYHeadRot(nextYaw);
        this.companion.setYBodyRot(nextYaw);

        double attributeSpeed = this.companion.getAttributeValue(Attributes.MOVEMENT_SPEED);
        float baseSpeed = (float) Math.max(MIN_SPEED, this.speedModifier * attributeSpeed);
        float yawDelta = Mth.wrapDegrees(targetYaw - nextYaw);
        float absYawDelta = Math.abs(yawDelta);
        float turnFactor = Mth.clamp(1.0F - absYawDelta / 105.0F, 0.28F, 1.0F);
        float moveSpeed = baseSpeed * turnFactor;
        if (horizontalDistanceSqr < 1.25D) {
            moveSpeed *= NEAR_TARGET_SLOWDOWN;
        }

        this.companion.setSpeed(moveSpeed);
        this.companion.setZza(Mth.clamp(turnFactor + 0.08F, MIN_FORWARD_INPUT, 1.0F));

        double horizontalDistance = Math.sqrt(horizontalDistanceSqr);
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
            this.companion.setDeltaMovement(motion.x, Math.max(motion.y, WATER_ASCENT_SPEED), motion.z);
        }
    }
}

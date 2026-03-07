package com.mcmod.aiplayers.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class PlayerMovementController {
    private final AIPlayerEntity entity;
    private List<PathNode> activePath = List.of();
    private BlockPos targetPos;
    private int pathIndex;
    private double speedModifier = 1.0D;
    private String pathStatus = "\u7a7a\u95f2";
    private Vec3 lastSample = Vec3.ZERO;
    private int stuckTicks;

    PlayerMovementController(AIPlayerEntity entity) {
        this.entity = entity;
    }

    boolean requestPathTo(BlockPos target, double speedModifier) {
        BlockPos resolvedTarget = this.entity.runtimeResolveMovementTarget(target);
        if (resolvedTarget == null) {
            this.pathStatus = "\u76ee\u6807\u4e0d\u53ef\u8fbe";
            return false;
        }
        if (this.targetPos != null && this.targetPos.distSqr(resolvedTarget) <= 1.0D && !this.activePath.isEmpty()) {
            this.speedModifier = speedModifier;
            return true;
        }

        this.targetPos = resolvedTarget.immutable();
        this.speedModifier = speedModifier;
        long gameTime = this.entity.level().getGameTime();
        boolean budgetGranted = TeamKnowledge.tryAcquirePathBudget(this.entity, gameTime);
        if (!budgetGranted && !this.activePath.isEmpty()) {
            this.pathStatus = "\u56e2\u961f\u8def\u5f84\u9884\u7b97\u5fd9\uff0c\u6cbf\u7528\u5f53\u524d\u8def\u5f84";
            return true;
        }

        this.activePath = budgetGranted
                ? AgentPathPlanner.plan(this.entity, this.targetPos)
                : List.of(new PathNode(Vec3.atCenterOf(this.targetPos)));
        this.pathIndex = 0;
        this.pathStatus = this.activePath.size() > 1 ? "\u8def\u5f84\u5df2\u89c4\u5212" : (budgetGranted ? "\u76f4\u7ebf\u63a5\u8fd1" : "\u9884\u7b97\u5fd9\uff0c\u5148\u76f4\u7ebf\u9760\u8fd1");
        this.lastSample = this.entity.position();
        this.stuckTicks = 0;
        return !this.activePath.isEmpty();
    }

    void clear() {
        this.activePath = List.of();
        this.targetPos = null;
        this.pathIndex = 0;
        this.pathStatus = "\u7a7a\u95f2";
        this.stuckTicks = 0;
        this.entity.setZza(0.0F);
        this.entity.setXxa(0.0F);
        this.entity.setSpeed(0.0F);
    }

    void tick() {
        if (this.targetPos == null) {
            return;
        }
        if (this.hasReachedTarget(this.targetPos)) {
            finishAtTarget();
            return;
        }
        if (this.activePath.isEmpty()) {
            this.requestPathTo(this.targetPos, this.speedModifier);
            if (this.activePath.isEmpty()) {
                this.pathStatus = "\u65e0\u53ef\u7528\u8def\u5f84";
                return;
            }
        }
        if (this.pathIndex >= this.activePath.size()) {
            this.pathIndex = this.activePath.size() - 1;
        }

        Vec3 waypoint = this.activePath.get(this.pathIndex).position();
        if (this.entity.distanceToSqr(waypoint) <= 1.4D && this.pathIndex < this.activePath.size() - 1) {
            this.pathIndex++;
            waypoint = this.activePath.get(this.pathIndex).position();
        }

        detectStuckAndRecover();
        double distanceToWaypoint = this.entity.distanceToSqr(waypoint);
        double appliedSpeed = distanceToWaypoint <= 3.0D ? Math.min(this.speedModifier, 0.95D) : this.speedModifier;
        this.entity.getMoveControl().setWantedPosition(waypoint.x, waypoint.y, waypoint.z, appliedSpeed);
        this.entity.setSprinting(appliedSpeed > 1.18D);
        this.pathStatus = (this.entity.isInWater() || this.entity.isUnderWater() || this.entity.isInLava())
                ? "\u6db2\u4f53\u8131\u56f0\u63a8\u8fdb\u4e2d"
                : (this.pathIndex < this.activePath.size() - 1 ? "\u6cbf\u8def\u5f84\u63a8\u8fdb" : "\u6700\u7ec8\u63a5\u8fd1\u76ee\u6807");
        if (this.entity.isInWater() || this.entity.isUnderWater() || this.entity.isInLava()) {
            Vec3 motion = this.entity.getDeltaMovement();
            this.entity.setDeltaMovement(motion.x, Math.max(motion.y, 0.08D), motion.z);
        }
    }

    boolean hasReachedTarget(BlockPos target) {
        return target != null && this.entity.distanceToSqr(Vec3.atCenterOf(target)) <= 4.0D;
    }

    boolean isPathActive() {
        return this.targetPos != null;
    }

    boolean isIdle() {
        return this.targetPos == null;
    }

    String getPathStatus() {
        return this.pathStatus;
    }

    private void finishAtTarget() {
        this.activePath = List.of();
        this.targetPos = null;
        this.pathIndex = 0;
        this.stuckTicks = 0;
        this.entity.setZza(0.0F);
        this.entity.setXxa(0.0F);
        this.entity.setSpeed(0.0F);
        this.pathStatus = "\u5df2\u5230\u8fbe";
    }

    private void detectStuckAndRecover() {
        Vec3 current = this.entity.position();
        if (current.distanceToSqr(this.lastSample) < 0.035D) {
            this.stuckTicks++;
        } else {
            this.stuckTicks = 0;
            this.lastSample = current;
        }
        if (this.stuckTicks < 12) {
            return;
        }

        this.pathStatus = "\u5c40\u90e8\u907f\u969c\u4e2d";
        if (this.entity.onGround()) {
            this.entity.getJumpControl().jump();
        }
        float yaw = this.entity.getYRot() * ((float) Math.PI / 180.0F);
        double side = (this.stuckTicks / 12) % 2 == 0 ? 0.65D : -0.65D;
        double offsetX = Mth.cos(yaw) * side;
        double offsetZ = -Mth.sin(yaw) * side;
        Vec3 sidestep = this.entity.position().add(offsetX, 0.0D, offsetZ);
        this.entity.getMoveControl().setWantedPosition(sidestep.x, sidestep.y, sidestep.z, Math.max(0.9D, this.speedModifier));

        if (this.stuckTicks >= 22 && this.targetPos != null) {
            BlockPos localStandPos = this.entity.runtimeResolveMovementTarget(this.targetPos);
            if (localStandPos != null && !localStandPos.equals(this.targetPos)) {
                this.targetPos = localStandPos;
                this.activePath = List.of(new PathNode(Vec3.atCenterOf(localStandPos)));
                this.pathIndex = 0;
                this.pathStatus = "\u5207\u6362\u5230\u5c40\u90e8\u53ef\u7ad9\u7acb\u70b9";
            }
        }

        if (this.stuckTicks >= 30 && this.targetPos != null) {
            long gameTime = this.entity.level().getGameTime();
            if (TeamKnowledge.tryAcquirePathBudget(this.entity, gameTime)) {
                this.activePath = AgentPathPlanner.plan(this.entity, this.targetPos);
                this.pathIndex = 0;
                this.pathStatus = "\u8def\u5f84\u91cd\u7b97";
                this.stuckTicks = 0;
                this.lastSample = this.entity.position();
            } else {
                this.pathStatus = "\u56e2\u961f\u8def\u5f84\u9884\u7b97\u5fd9\uff0c\u7b49\u5f85\u91cd\u7b97\u7a97\u53e3";
            }
        }
    }
}

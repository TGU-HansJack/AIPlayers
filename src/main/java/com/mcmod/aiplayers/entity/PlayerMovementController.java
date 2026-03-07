package com.mcmod.aiplayers.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class PlayerMovementController {
    private static final int MAX_LOOKAHEAD_NODES = 4;
    private static final int LOCAL_RECOVERY_TICKS = 12;
    private static final int LOCAL_REPOSITION_TICKS = 20;
    private static final int REPLAN_TICKS = 30;
    private static final int REPLAN_BACKOFF_TICKS = 12;

    private final AIPlayerEntity entity;
    private List<PathNode> activePath = List.of();
    private BlockPos targetPos;
    private int pathIndex;
    private double speedModifier = 1.0D;
    private String pathStatus = "空闲";
    private Vec3 lastSample = Vec3.ZERO;
    private int stuckTicks;
    private long lastReplanTick;

    PlayerMovementController(AIPlayerEntity entity) {
        this.entity = entity;
    }

    boolean requestPathTo(BlockPos target, double speedModifier) {
        BlockPos resolvedTarget = this.entity.runtimeResolveMovementTarget(target);
        if (resolvedTarget == null) {
            this.pathStatus = "目标不可达";
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
            this.pathStatus = "团队路径预算忙，沿用当前路径";
            return true;
        }

        this.activePath = budgetGranted
                ? AgentPathPlanner.plan(this.entity, this.targetPos)
                : List.of(new PathNode(Vec3.atCenterOf(this.targetPos)));
        this.pathIndex = 0;
        this.pathStatus = describeFreshPath(budgetGranted);
        this.lastSample = this.entity.position();
        this.stuckTicks = 0;
        this.lastReplanTick = gameTime;
        return !this.activePath.isEmpty();
    }

    void clear() {
        this.activePath = List.of();
        this.targetPos = null;
        this.pathIndex = 0;
        this.pathStatus = "空闲";
        this.stuckTicks = 0;
        this.entity.setZza(0.0F);
        this.entity.setXxa(0.0F);
        this.entity.setSpeed(0.0F);
        this.entity.setSprinting(false);
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
                this.pathStatus = "无可用路径";
                return;
            }
        }
        if (this.pathIndex >= this.activePath.size()) {
            this.pathIndex = this.activePath.size() - 1;
        }

        Vec3 waypoint = chooseWaypoint();
        detectStuckAndRecover(waypoint);
        if (this.targetPos == null) {
            return;
        }

        Vec3 liquidEscape = selectLiquidEscapeWaypoint();
        if (liquidEscape != null) {
            waypoint = liquidEscape;
            this.pathStatus = "液体脱困：寻找落脚点";
        }

        double distanceToWaypoint = this.entity.distanceToSqr(waypoint);
        double appliedSpeed = computeAppliedSpeed(distanceToWaypoint);
        this.entity.getLookControl().setLookAt(waypoint.x, waypoint.y + 0.6D, waypoint.z, 30.0F, 30.0F);
        this.entity.getMoveControl().setWantedPosition(waypoint.x, waypoint.y, waypoint.z, appliedSpeed);
        this.entity.setSprinting(appliedSpeed > 1.12D && distanceToWaypoint > 10.0D);

        if (liquidEscape == null) {
            this.pathStatus = this.pathIndex < this.activePath.size() - 1
                    ? "沿路径推进（" + (this.pathIndex + 1) + "/" + this.activePath.size() + "）"
                    : "最终接近目标";
        }
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

    boolean isRecovering() {
        return this.stuckTicks >= LOCAL_RECOVERY_TICKS;
    }

    boolean isSeverelyStuck() {
        return this.stuckTicks >= REPLAN_TICKS;
    }

    String getPathStatus() {
        return this.pathStatus;
    }

    private String describeFreshPath(boolean budgetGranted) {
        if (this.activePath.isEmpty()) {
            return "无可用路径";
        }
        if (this.activePath.size() == 1) {
            return budgetGranted ? "直线接近" : "预算忙，先直线靠近";
        }
        return "路径已规划（" + this.activePath.size() + " 节点）";
    }

    private void finishAtTarget() {
        this.activePath = List.of();
        this.targetPos = null;
        this.pathIndex = 0;
        this.stuckTicks = 0;
        this.entity.setZza(0.0F);
        this.entity.setXxa(0.0F);
        this.entity.setSpeed(0.0F);
        this.entity.setSprinting(false);
        this.pathStatus = "已到达";
    }

    private Vec3 chooseWaypoint() {
        while (this.pathIndex < this.activePath.size() - 1 && this.entity.distanceToSqr(this.activePath.get(this.pathIndex).position()) <= 1.15D) {
            this.pathIndex++;
        }
        Vec3 anchor = this.entity.position();
        int bestIndex = this.pathIndex;
        int limit = Math.min(this.activePath.size() - 1, this.pathIndex + MAX_LOOKAHEAD_NODES);
        for (int probe = limit; probe > this.pathIndex; probe--) {
            if (this.entity.runtimeCanDirectlyTraverse(anchor, this.activePath.get(probe).position())) {
                bestIndex = probe;
                break;
            }
        }
        if (bestIndex > this.pathIndex) {
            this.pathIndex = bestIndex;
            this.pathStatus = "前瞻平滑：跳过中间节点";
        }
        return this.activePath.get(this.pathIndex).position();
    }

    private double computeAppliedSpeed(double distanceToWaypoint) {
        if (distanceToWaypoint <= 1.0D) {
            return Math.min(this.speedModifier, 0.82D);
        }
        if (distanceToWaypoint <= 3.0D) {
            return Math.min(this.speedModifier, 0.95D);
        }
        if (distanceToWaypoint >= 20.0D) {
            return Math.max(this.speedModifier, 1.2D);
        }
        return this.speedModifier;
    }

    private Vec3 selectLiquidEscapeWaypoint() {
        if (!(this.entity.isInWater() || this.entity.isUnderWater() || this.entity.isInLava())) {
            return null;
        }
        BlockPos dryStand = this.entity.runtimeFindNearbyDryStandPosition(this.entity.blockPosition(), 4, 4);
        if (dryStand == null) {
            return null;
        }
        if (this.entity.distanceToSqr(Vec3.atCenterOf(dryStand)) <= 2.25D) {
            return null;
        }
        return Vec3.atCenterOf(dryStand);
    }

    private void detectStuckAndRecover(Vec3 waypoint) {
        Vec3 current = this.entity.position();
        if (current.distanceToSqr(this.lastSample) < 0.028D) {
            this.stuckTicks++;
        } else {
            this.stuckTicks = 0;
            this.lastSample = current;
        }
        if (this.stuckTicks < LOCAL_RECOVERY_TICKS) {
            return;
        }

        this.pathStatus = "局部避障中";
        if (this.entity.onGround()) {
            this.entity.getJumpControl().jump();
        }
        float yaw = this.entity.getYRot() * ((float) Math.PI / 180.0F);
        double side = (this.stuckTicks / LOCAL_RECOVERY_TICKS) % 2 == 0 ? 0.65D : -0.65D;
        double offsetX = Mth.cos(yaw) * side;
        double offsetZ = -Mth.sin(yaw) * side;
        Vec3 sidestep = this.entity.position().add(offsetX, 0.0D, offsetZ);
        this.entity.getMoveControl().setWantedPosition(sidestep.x, sidestep.y, sidestep.z, Math.max(0.9D, this.speedModifier));

        if (this.stuckTicks >= LOCAL_REPOSITION_TICKS && this.targetPos != null) {
            BlockPos localStandPos = this.entity.runtimeResolveMovementTarget(this.targetPos);
            if (localStandPos != null && !localStandPos.equals(this.targetPos)) {
                this.targetPos = localStandPos;
                this.activePath = List.of(new PathNode(Vec3.atCenterOf(localStandPos)));
                this.pathIndex = 0;
                this.pathStatus = "切换到局部可站立点";
            }
        }

        if (this.stuckTicks >= REPLAN_TICKS && this.targetPos != null) {
            long gameTime = this.entity.level().getGameTime();
            if (gameTime - this.lastReplanTick < REPLAN_BACKOFF_TICKS) {
                this.pathStatus = "等待重算退避窗口";
                return;
            }
            if (TeamKnowledge.tryAcquirePathBudget(this.entity, gameTime)) {
                this.activePath = AgentPathPlanner.plan(this.entity, this.targetPos);
                this.pathIndex = 0;
                this.pathStatus = this.activePath.isEmpty() ? "重算失败" : "路径重算完成";
                this.stuckTicks = 0;
                this.lastSample = this.entity.position();
                this.lastReplanTick = gameTime;
                if (!this.activePath.isEmpty() && waypoint != null && this.entity.runtimeCanDirectlyTraverse(this.entity.position(), waypoint)) {
                    this.pathStatus = "重算后恢复推进";
                }
            } else {
                this.pathStatus = "团队路径预算忙，等待重算窗口";
            }
        }
    }
}

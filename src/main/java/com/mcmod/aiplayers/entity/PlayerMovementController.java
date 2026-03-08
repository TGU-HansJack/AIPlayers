package com.mcmod.aiplayers.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class PlayerMovementController {
    private static final int MAX_LOOKAHEAD_NODES = 4;
    private static final int LOCAL_RECOVERY_TICKS = 20;
    private static final int LOCAL_REPOSITION_TICKS = 40;
    private static final int REPLAN_TICKS = 60;
    private static final int REPLAN_BACKOFF_TICKS = 20;
    private static final int PATH_REPLAN_COOLDOWN_TICKS = 20;
    private static final int STUCK_CHECK_INTERVAL_TICKS = 5;
    private static final double STUCK_PROGRESS_THRESHOLD_SQR = 4.0E-4D;
    private static final double SPIN_PROGRESS_THRESHOLD_SQR = 1.0E-3D;
    private static final float SPIN_YAW_DELTA_THRESHOLD = 22.0F;
    private static final int SPIN_TRIGGER_TICKS = 20;
    private static final double ARRIVE_XZ_DISTANCE_SQR = 0.36D;
    private static final double ARRIVE_Y_ABS = 1.0D;
    private static final double WAYPOINT_ADVANCE_XZ_DISTANCE_SQR = 0.49D;
    private static final double WAYPOINT_ADVANCE_Y_ABS = 1.1D;
    private static final double LIQUID_ASCENT_SPEED = 0.04D;

    private final AIPlayerEntity entity;
    private List<PathNode> activePath = List.of();
    private BlockPos targetPos;
    private int pathIndex;
    private double speedModifier = 1.0D;
    private String pathStatus = "空闲";
    private Vec3 lastSample = Vec3.ZERO;
    private int stuckTicks;
    private long lastReplanTick;
    private long lastPathRequestTick;
    private long lastStuckCheckTick;
    private long lastNodeActionTick;
    private long lastRecoveryJumpTick;
    private Vec3 lastSpinSample = Vec3.ZERO;
    private float lastSpinYaw;
    private int spinTicks;
    private static final int NODE_ACTION_COOLDOWN_TICKS = 6;
    private static final int RECOVERY_JUMP_COOLDOWN_TICKS = 14;

    PlayerMovementController(AIPlayerEntity entity) {
        this.entity = entity;
    }

    boolean requestPathTo(BlockPos target, double speedModifier) {
        BlockPos resolvedTarget = this.entity.runtimeResolveMovementTarget(target);
        if (resolvedTarget == null) {
            this.pathStatus = "目标不可达";
            return false;
        }
        long gameTime = this.entity.level().getGameTime();
        boolean sameTargetRegion = this.targetPos != null && this.targetPos.distSqr(resolvedTarget) <= 4.0D;
        if (this.targetPos != null && this.targetPos.distSqr(resolvedTarget) <= 1.0D && !this.activePath.isEmpty()) {
            this.speedModifier = speedModifier;
            return true;
        }
        if (sameTargetRegion && gameTime - this.lastPathRequestTick < PATH_REPLAN_COOLDOWN_TICKS && !this.activePath.isEmpty()) {
            this.targetPos = resolvedTarget.immutable();
            this.speedModifier = speedModifier;
            this.pathStatus = "沿用冷却期内路径";
            return true;
        }
        if (sameTargetRegion && gameTime - this.lastPathRequestTick < PATH_REPLAN_COOLDOWN_TICKS && this.activePath.isEmpty()) {
            this.targetPos = resolvedTarget.immutable();
            this.speedModifier = speedModifier;
            this.activePath = List.of(PathNode.move(Vec3.atCenterOf(this.targetPos)));
            this.pathIndex = 0;
            this.pathStatus = "路径重算冷却中，先直控逼近";
            return true;
        }

        this.targetPos = resolvedTarget.immutable();
        this.speedModifier = speedModifier;
        this.lastPathRequestTick = gameTime;
        boolean budgetGranted = TeamKnowledge.tryAcquirePathBudget(this.entity, gameTime);
        if (!budgetGranted && !this.activePath.isEmpty()) {
            this.pathStatus = "团队路径预算忙，沿用当前路径";
            return true;
        }

        List<PathNode> planned = budgetGranted
                ? AgentPathPlanner.plan(this.entity, this.targetPos)
                : List.of(PathNode.move(Vec3.atCenterOf(this.targetPos)));
        if (planned.isEmpty()) {
            planned = List.of(PathNode.move(Vec3.atCenterOf(this.targetPos)));
            this.pathStatus = budgetGranted ? "路径缺失，切换直控逼近" : "预算忙，先直控逼近";
        } else {
            this.pathStatus = describeFreshPath(budgetGranted);
        }
        this.activePath = planned;
        this.pathIndex = 0;
        this.lastSample = this.entity.position();
        this.lastSpinSample = this.entity.position();
        this.lastSpinYaw = this.entity.getYRot();
        this.spinTicks = 0;
        this.stuckTicks = 0;
        this.lastReplanTick = gameTime;
        this.lastStuckCheckTick = gameTime;
        return true;
    }

    void clear() {
        this.activePath = List.of();
        this.targetPos = null;
        this.pathIndex = 0;
        this.pathStatus = "空闲";
        this.stuckTicks = 0;
        this.spinTicks = 0;
        this.lastStuckCheckTick = 0L;
        this.lastNodeActionTick = 0L;
        this.lastRecoveryJumpTick = 0L;
        this.lastSpinSample = Vec3.ZERO;
        this.lastSpinYaw = 0.0F;
        this.entity.runtimeClearWasdOverride();
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
            boolean requested = this.requestPathTo(this.targetPos, this.speedModifier);
            if (this.activePath.isEmpty()) {
                this.pathStatus = requested ? "无可用路径，切换直控逼近" : this.pathStatus;
                driveDirectFallback();
                return;
            }
        }
        if (this.pathIndex >= this.activePath.size()) {
            this.pathIndex = this.activePath.size() - 1;
        }

        PathNode currentNode = this.activePath.get(this.pathIndex);
        if (!tryHandleNodeAction(currentNode)) {
            return;
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
        applyWasdToward(waypoint, appliedSpeed, currentNode.jumpRequired() || liquidEscape != null);

        if (liquidEscape == null) {
            this.pathStatus = this.pathIndex < this.activePath.size() - 1
                    ? "沿路径推进（" + (this.pathIndex + 1) + "/" + this.activePath.size() + "）"
                    : "最终接近目标";
        }
        if (this.entity.isInWater() || this.entity.isUnderWater() || this.entity.isInLava()) {
            Vec3 motion = this.entity.getDeltaMovement();
            boolean needAscend = this.entity.isUnderWater()
                    || this.entity.horizontalCollision
                    || waypoint.y > this.entity.getY() + 0.55D;
            double minVertical = needAscend ? LIQUID_ASCENT_SPEED : 0.005D;
            this.entity.setDeltaMovement(motion.x * 0.96D, Math.max(motion.y, minVertical), motion.z * 0.96D);
        }
    }

    boolean hasReachedTarget(BlockPos target) {
        if (target == null) {
            return false;
        }
        Vec3 center = Vec3.atCenterOf(target);
        double dx = center.x - this.entity.getX();
        double dz = center.z - this.entity.getZ();
        return dx * dx + dz * dz <= ARRIVE_XZ_DISTANCE_SQR
                && Math.abs(center.y - this.entity.getY()) <= ARRIVE_Y_ABS;
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
        this.spinTicks = 0;
        this.lastStuckCheckTick = 0L;
        this.lastNodeActionTick = 0L;
        this.lastRecoveryJumpTick = 0L;
        this.lastSpinSample = Vec3.ZERO;
        this.lastSpinYaw = 0.0F;
        this.entity.runtimeClearWasdOverride();
        this.entity.setZza(0.0F);
        this.entity.setXxa(0.0F);
        this.entity.setSpeed(0.0F);
        this.entity.setSprinting(false);
        this.pathStatus = "已到达";
    }

    private Vec3 chooseWaypoint() {
        while (this.pathIndex < this.activePath.size() - 1 && this.isNearWaypoint(this.activePath.get(this.pathIndex).position(), WAYPOINT_ADVANCE_XZ_DISTANCE_SQR, WAYPOINT_ADVANCE_Y_ABS)) {
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
        if (this.entity.isInWater() || this.entity.isUnderWater() || this.entity.isInLava()) {
            return Math.min(this.speedModifier, 0.92D);
        }
        if (distanceToWaypoint <= 0.36D) {
            return Math.min(this.speedModifier, 0.72D);
        }
        if (distanceToWaypoint <= 2.25D) {
            return Math.min(this.speedModifier, 0.96D);
        }
        if (distanceToWaypoint <= 9.0D) {
            return Math.min(this.speedModifier, 1.08D);
        }
        if (distanceToWaypoint >= 64.0D) {
            return Mth.clamp(this.speedModifier, 1.05D, 1.32D);
        }
        return Math.min(this.speedModifier, 1.18D);
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
        long gameTime = this.entity.level().getGameTime();
        if (gameTime - this.lastStuckCheckTick < STUCK_CHECK_INTERVAL_TICKS) {
            return;
        }
        this.lastStuckCheckTick = gameTime;
        Vec3 current = this.entity.position();
        if (this.lastSpinSample == Vec3.ZERO) {
            this.lastSpinSample = current;
            this.lastSpinYaw = this.entity.getYRot();
        } else {
            float yawDelta = Math.abs(Mth.wrapDegrees(this.entity.getYRot() - this.lastSpinYaw));
            double moved = current.distanceToSqr(this.lastSpinSample);
            if (moved < SPIN_PROGRESS_THRESHOLD_SQR && yawDelta > SPIN_YAW_DELTA_THRESHOLD) {
                this.spinTicks += STUCK_CHECK_INTERVAL_TICKS;
            } else {
                this.spinTicks = Math.max(0, this.spinTicks - STUCK_CHECK_INTERVAL_TICKS);
            }
            this.lastSpinSample = current;
            this.lastSpinYaw = this.entity.getYRot();
            if (this.spinTicks >= SPIN_TRIGGER_TICKS) {
                this.handleSpinRecovery(gameTime);
            }
        }
        if (current.distanceToSqr(this.lastSample) < STUCK_PROGRESS_THRESHOLD_SQR) {
            this.stuckTicks += STUCK_CHECK_INTERVAL_TICKS;
        } else {
            this.stuckTicks = Math.max(0, this.stuckTicks - STUCK_CHECK_INTERVAL_TICKS);
            this.lastSample = current;
        }
        if (this.stuckTicks < LOCAL_RECOVERY_TICKS) {
            return;
        }

        this.pathStatus = "局部避障中";
        boolean climbNeeded = waypoint != null && waypoint.y > this.entity.getY() + 0.45D;
        if (this.entity.onGround()
                && !this.entity.runtimeHasLowCeiling()
                && (this.entity.horizontalCollision || climbNeeded)
                && gameTime - this.lastRecoveryJumpTick >= RECOVERY_JUMP_COOLDOWN_TICKS) {
            this.entity.getJumpControl().jump();
            this.lastRecoveryJumpTick = gameTime;
        }
        float yaw = this.entity.getYRot() * ((float) Math.PI / 180.0F);
        double side = (this.stuckTicks / LOCAL_RECOVERY_TICKS) % 2 == 0 ? 0.55D : -0.55D;
        double offsetX = Mth.cos(yaw) * side;
        double offsetZ = -Mth.sin(yaw) * side;
        Vec3 sidestep = this.entity.position().add(offsetX, 0.0D, offsetZ);
        float sidestepYaw = (float) (Mth.atan2(offsetZ, offsetX) * (180.0F / (float) Math.PI)) - 90.0F;
        this.entity.runtimeSetWasdControl(0.62F, side > 0 ? 0.28F : -0.28F, 0.078F, sidestepYaw, false, false);

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
                this.lastPathRequestTick = gameTime;
                if (!this.activePath.isEmpty() && waypoint != null && this.entity.runtimeCanDirectlyTraverse(this.entity.position(), waypoint)) {
                    this.pathStatus = "重算后恢复推进";
                }
            } else {
                this.pathStatus = "团队路径预算忙，等待重算窗口";
            }
        }
    }

    private boolean tryHandleNodeAction(PathNode node) {
        if (node == null || node.action() == PathNodeAction.NONE || node.actionPos() == null) {
            return true;
        }
        BlockPos actionPos = node.actionPos();
        boolean nearAction = this.entity.runtimeIsWithin(actionPos, 6.25D);
        if (!nearAction) {
            Vec3 center = Vec3.atCenterOf(actionPos);
            applyWasdToward(center, 0.85D, node.jumpRequired());
            this.pathStatus = "接近路径动作点";
            return false;
        }

        long now = this.entity.level().getGameTime();
        if (now - this.lastNodeActionTick < NODE_ACTION_COOLDOWN_TICKS) {
            this.pathStatus = "动作冷却中";
            applyWasdToward(Vec3.atCenterOf(actionPos), 0.32D, false);
            return false;
        }
        this.lastNodeActionTick = now;

        return switch (node.action()) {
            case BREAK_BLOCK -> {
                boolean done = !this.entity.runtimeCanBreakPathBlock(actionPos) || this.entity.runtimeBreakPathNavigationBlock(actionPos);
                if (!done) {
                    this.pathStatus = "路径清障中";
                }
                yield done;
            }
            case PLACE_SUPPORT -> {
                boolean done = !this.entity.runtimeCanPlacePathSupport(actionPos) || this.entity.runtimePlacePathSupport(actionPos);
                if (!done) {
                    this.pathStatus = "路径搭桥中";
                }
                yield done;
            }
            case JUMP_ASCEND -> true;
            case NONE -> true;
        };
    }

    private void applyWasdToward(Vec3 waypoint, double speedHint, boolean jump) {
        Vec3 current = this.entity.position();
        double dx = waypoint.x - current.x;
        double dz = waypoint.z - current.z;
        if (dx * dx + dz * dz < 1.0E-4D) {
            this.entity.runtimeSetWasdControl(0.0F, 0.0F, 0.0F, this.entity.getYRot(), false, false);
            return;
        }
        float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0F / (float) Math.PI)) - 90.0F;
        float yawDelta = Mth.wrapDegrees(targetYaw - this.entity.getYRot());
        float radians = yawDelta * ((float) Math.PI / 180.0F);
        float forward = Mth.clamp(Mth.cos(radians), 0.0F, 1.0F);
        float strafe = Mth.clamp(-Mth.sin(radians), -0.55F, 0.55F);
        float absYawDelta = Math.abs(yawDelta);
        if (absYawDelta > 120.0F) {
            forward = 0.0F;
            strafe = 0.0F;
        } else if (absYawDelta > 90.0F) {
            forward = Math.max(0.34F, forward * 0.62F);
            strafe = 0.0F;
        } else if (absYawDelta > 60.0F) {
            forward = Math.max(0.62F, forward * 0.88F);
            strafe *= 0.12F;
        } else if (absYawDelta > 35.0F) {
            forward = Math.max(0.78F, forward);
            strafe *= 0.25F;
        } else {
            forward = Math.max(0.88F, forward);
        }
        float speed = (float) Mth.clamp(speedHint <= 0.0D ? 0.28D : speedHint * 0.30D, 0.16D, 0.42D);
        boolean sprint = speed >= 0.24F
                && absYawDelta < 34.0F
                && forward > 0.75F
                && !this.entity.isInWater()
                && !this.entity.isUnderWater()
                && !this.entity.isInLava();
        boolean shouldJump = (jump && this.entity.onGround() && (waypoint.y - current.y > 0.2D || this.entity.horizontalCollision))
                || (waypoint.y - current.y > 0.52D && this.entity.onGround());
        if (this.entity.runtimeHasLowCeiling()) {
            shouldJump = false;
        }
        if (this.entity.isInWater() || this.entity.isUnderWater()) {
            boolean needsAscend = this.entity.isUnderWater()
                    || this.entity.horizontalCollision
                    || waypoint.y > current.y + 0.55D;
            shouldJump = needsAscend;
            forward = Math.max(forward, 0.92F);
            strafe = Mth.clamp(strafe, -0.08F, 0.08F);
            speed = Math.min(speed, 0.24F);
            sprint = false;
        }
        this.entity.runtimeSetWasdControl(forward, strafe, speed, targetYaw, sprint, shouldJump);
    }

    private void driveDirectFallback() {
        if (this.targetPos == null) {
            return;
        }
        BlockPos resolved = this.entity.runtimeResolveMovementTarget(this.targetPos);
        Vec3 fallback = Vec3.atCenterOf(resolved != null ? resolved : this.targetPos);
        detectStuckAndRecover(fallback);
        if (this.targetPos == null) {
            return;
        }
        double distance = this.entity.distanceToSqr(fallback);
        double speedHint = Math.max(0.82D, computeAppliedSpeed(distance));
        boolean shouldJump = this.entity.horizontalCollision || fallback.y - this.entity.getY() > 0.48D;
        applyWasdToward(fallback, speedHint, shouldJump);
        this.pathStatus = "路径空闲兜底：直控逼近目标";
    }

    private void handleSpinRecovery(long gameTime) {
        this.spinTicks = 0;
        if (this.targetPos == null) {
            return;
        }
        BlockPos resolved = this.entity.runtimeResolveMovementTarget(this.targetPos);
        if (resolved != null) {
            this.targetPos = resolved;
            this.activePath = List.of(PathNode.move(Vec3.atCenterOf(resolved)));
            this.pathIndex = 0;
            this.pathStatus = "检测到原地转圈，重置为局部直控路径";
        } else {
            this.activePath = List.of();
            this.pathStatus = "检测到原地转圈，等待重算路径";
        }
        this.stuckTicks = Math.max(this.stuckTicks, LOCAL_RECOVERY_TICKS);
        this.lastSample = this.entity.position();
        this.lastReplanTick = gameTime - REPLAN_BACKOFF_TICKS;
    }

    private boolean isNearWaypoint(Vec3 waypoint, double maxXzDistanceSqr, double maxYAbs) {
        double dx = waypoint.x - this.entity.getX();
        double dz = waypoint.z - this.entity.getZ();
        if (dx * dx + dz * dz > maxXzDistanceSqr) {
            return false;
        }
        return Math.abs(waypoint.y - this.entity.getY()) <= maxYAbs;
    }
}

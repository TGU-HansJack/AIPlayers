package com.mcmod.aiplayers.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class PathManager {
    private static final int MAX_LOOKAHEAD_NODES = 4;
    private static final int LOCAL_RECOVERY_TICKS = 20;
    private static final int LOCAL_REPOSITION_TICKS = 40;
    private static final int REPLAN_TICKS = 60;
    private static final int REPLAN_BACKOFF_TICKS = 20;
    private static final int PATH_REPLAN_COOLDOWN_TICKS = 20;
    private static final int PATH_TIMEOUT_TICKS = 200;
    private static final int STUCK_CHECK_INTERVAL_TICKS = 5;
    private static final double STUCK_PROGRESS_THRESHOLD_SQR = 4.0E-4D;
    private static final double SPIN_PROGRESS_THRESHOLD_SQR = 1.0E-3D;
    private static final float SPIN_YAW_DELTA_THRESHOLD = 22.0F;
    private static final int SPIN_TRIGGER_TICKS = 20;
    private static final double ARRIVE_XZ_DISTANCE_SQR = 0.36D;
    private static final double ARRIVE_Y_ABS = 1.0D;
    private static final double WAYPOINT_ADVANCE_XZ_DISTANCE_SQR = 0.49D;
    private static final double WAYPOINT_ADVANCE_Y_ABS = 1.1D;
    private static final int NODE_ACTION_COOLDOWN_TICKS = 6;
    private static final int RECOVERY_JUMP_COOLDOWN_TICKS = 14;
    private static final int DIGGABLE_RETRY_COOLDOWN_TICKS = 8;

    private final AIPlayerEntity entity;
    private final PathCooldown pathCooldown = new PathCooldown(PATH_REPLAN_COOLDOWN_TICKS, REPLAN_BACKOFF_TICKS);
    private final StuckDetector stuckDetector = new StuckDetector(
            STUCK_CHECK_INTERVAL_TICKS,
            STUCK_PROGRESS_THRESHOLD_SQR,
            SPIN_PROGRESS_THRESHOLD_SQR,
            SPIN_YAW_DELTA_THRESHOLD,
            SPIN_TRIGGER_TICKS);

    private List<PathNode> activePath = List.of();
    private BlockPos targetPos;
    private int pathIndex;
    private double speedModifier = 1.0D;
    private PathState pathState = PathState.IDLE;
    private String pathStatus = "空闲";
    private long activePathStartTick;
    private long lastNodeActionTick;
    private long lastRecoveryJumpTick;
    private long lastDiggableAttemptTick;

    PathManager(AIPlayerEntity entity) {
        this.entity = entity;
    }

    boolean requestPathTo(BlockPos target, double speedModifier) {
        BlockPos resolvedTarget = this.entity.runtimeResolveMovementTarget(target);
        if (resolvedTarget == null) {
            this.pathState = PathState.IDLE;
            this.pathStatus = "目标不可达";
            return false;
        }
        this.pathState = PathState.PATHING;
        long gameTime = this.entity.level().getGameTime();
        boolean sameTargetRegion = this.targetPos != null && this.targetPos.distSqr(resolvedTarget) <= 4.0D;
        boolean hasPath = !this.activePath.isEmpty();

        if (this.targetPos != null && this.targetPos.distSqr(resolvedTarget) <= 1.0D && hasPath) {
            this.targetPos = resolvedTarget.immutable();
            this.speedModifier = speedModifier;
            this.pathStatus = "沿用当前路径";
            return true;
        }
        if (this.pathCooldown.canReuseRecentPath(sameTargetRegion, hasPath, isSeverelyStuck(), gameTime)) {
            this.targetPos = resolvedTarget.immutable();
            this.speedModifier = speedModifier;
            this.pathStatus = "沿用冷却期内路径";
            return true;
        }
        if (sameTargetRegion && this.pathCooldown.inRequestCooldown(gameTime) && this.activePath.isEmpty()) {
            this.targetPos = resolvedTarget.immutable();
            this.speedModifier = speedModifier;
            applyNewPath(List.of(PathNode.move(Vec3.atCenterOf(this.targetPos))), gameTime);
            this.pathStatus = "路径重算冷却中，先直控逼近";
            return true;
        }

        this.targetPos = resolvedTarget.immutable();
        this.speedModifier = speedModifier;
        this.pathCooldown.markPathRequest(gameTime);
        boolean budgetGranted = TeamKnowledge.tryAcquirePathBudget(this.entity, gameTime);
        if (!budgetGranted && hasPath && !this.isSeverelyStuck()) {
            this.pathState = PathState.MOVING;
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
            this.pathStatus = describeFreshPath(planned, budgetGranted);
        }
        applyNewPath(planned, gameTime);
        return true;
    }

    void clear() {
        this.activePath = List.of();
        this.targetPos = null;
        this.pathIndex = 0;
        this.speedModifier = 1.0D;
        this.pathState = PathState.IDLE;
        this.pathStatus = "空闲";
        this.activePathStartTick = 0L;
        this.lastNodeActionTick = 0L;
        this.lastRecoveryJumpTick = 0L;
        this.lastDiggableAttemptTick = 0L;
        this.pathCooldown.clear();
        this.stuckDetector.clear();
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
        if (this.entity.runtimeIsMiningLocked()) {
            this.pathState = PathState.STUCK;
            this.pathStatus = "采掘锁定：暂停导航";
            return;
        }
        long gameTime = this.entity.level().getGameTime();
        if (this.hasReachedTarget(this.targetPos)) {
            finishAtTarget();
            return;
        }

        if (this.activePathTimedOut(gameTime)) {
            this.activePath = List.of();
            this.pathIndex = 0;
            this.pathState = PathState.REPATH;
            this.pathStatus = "路径超时，准备重算";
        }

        if (this.activePath.isEmpty()) {
            boolean requested = this.requestPathTo(this.targetPos, this.speedModifier);
            if (this.activePath.isEmpty()) {
                if (this.tryDiggableAdvance(gameTime, "路径缺失，尝试挖掘开路")) {
                    return;
                }
                this.pathStatus = requested ? "无可用路径，切换直控逼近" : this.pathStatus;
                driveDirectFallback(gameTime);
                return;
            }
        }

        if (this.pathIndex >= this.activePath.size()) {
            this.activePath = List.of();
            this.pathIndex = 0;
            this.pathState = PathState.REPATH;
            this.pathStatus = "路径节点耗尽，准备重算";
            if (this.tryDiggableAdvance(gameTime, "路径节点耗尽，尝试挖掘续航")) {
                return;
            }
            driveDirectFallback(gameTime);
            return;
        }

        PathNode currentNode = this.activePath.get(this.pathIndex);
        if (!tryHandleNodeAction(currentNode, gameTime)) {
            return;
        }
        Vec3 waypoint = chooseWaypoint();
        detectStuckAndRecover(waypoint, gameTime);
        if (this.targetPos == null) {
            return;
        }
        if (this.activePath.isEmpty()) {
            driveDirectFallback(gameTime);
            return;
        }

        Vec3 liquidEscape = selectLiquidEscapeWaypoint();
        if (liquidEscape != null) {
            waypoint = liquidEscape;
            this.pathStatus = "液体脱困：寻找落脚点";
        }

        double distanceToWaypoint = this.entity.distanceToSqr(waypoint);
        double appliedSpeed = computeAppliedSpeed(distanceToWaypoint);
        applyMoveToward(waypoint, appliedSpeed, currentNode.jumpRequired() || liquidEscape != null, gameTime);

        if (liquidEscape == null) {
            this.pathState = PathState.MOVING;
            this.pathStatus = this.pathIndex < this.activePath.size() - 1
                    ? "沿路径推进（" + (this.pathIndex + 1) + "/" + this.activePath.size() + "）"
                    : "最终接近目标";
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
        return this.stuckDetector.stuckTicks() >= LOCAL_RECOVERY_TICKS;
    }

    boolean isSeverelyStuck() {
        return this.stuckDetector.stuckTicks() >= REPLAN_TICKS;
    }

    String getPathStatus() {
        return this.pathState.name() + "|" + this.pathStatus;
    }

    BlockPos getActiveTargetPos() {
        return this.targetPos;
    }

    private void applyNewPath(List<PathNode> planned, long gameTime) {
        this.activePath = planned == null ? List.of() : planned;
        this.pathIndex = 0;
        this.activePathStartTick = gameTime;
        this.pathState = this.activePath.isEmpty() ? PathState.IDLE : PathState.MOVING;
        this.stuckDetector.reset(this.entity.position(), this.entity.getYRot(), gameTime);
    }

    private boolean activePathTimedOut(long gameTime) {
        return !this.activePath.isEmpty() && this.activePathStartTick > 0L && gameTime - this.activePathStartTick > PATH_TIMEOUT_TICKS;
    }

    private String describeFreshPath(List<PathNode> planned, boolean budgetGranted) {
        if (planned.isEmpty()) {
            return "无可用路径";
        }
        if (planned.size() == 1) {
            return budgetGranted ? "直线接近" : "预算忙，先直线靠近";
        }
        return "路径已规划（" + planned.size() + " 节点）";
    }

    private void finishAtTarget() {
        this.activePath = List.of();
        this.targetPos = null;
        this.pathIndex = 0;
        this.pathState = PathState.IDLE;
        this.activePathStartTick = 0L;
        this.lastNodeActionTick = 0L;
        this.lastRecoveryJumpTick = 0L;
        this.lastDiggableAttemptTick = 0L;
        this.stuckDetector.clear();
        this.entity.runtimeClearWasdOverride();
        this.entity.setZza(0.0F);
        this.entity.setXxa(0.0F);
        this.entity.setSpeed(0.0F);
        this.entity.setSprinting(false);
        this.pathStatus = "已到达";
    }

    private Vec3 chooseWaypoint() {
        while (this.pathIndex < this.activePath.size() - 1
                && this.isNearWaypoint(this.activePath.get(this.pathIndex).position(), WAYPOINT_ADVANCE_XZ_DISTANCE_SQR, WAYPOINT_ADVANCE_Y_ABS)) {
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
            this.pathState = PathState.MOVING;
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

    private void detectStuckAndRecover(Vec3 waypoint, long gameTime) {
        StuckDetector.SampleResult sample = this.stuckDetector.sample(this.entity.position(), this.entity.getYRot(), gameTime);
        if (!sample.checked()) {
            return;
        }
        if (sample.spinTriggered()) {
            handleSpinRecovery(gameTime);
            if (this.targetPos == null) {
                return;
            }
        }
        if (sample.stuckTicks() < LOCAL_RECOVERY_TICKS) {
            return;
        }

        this.pathStatus = "局部避障中";
        this.pathState = PathState.STUCK;
        if (sample.stuckTicks() >= LOCAL_RECOVERY_TICKS + STUCK_CHECK_INTERVAL_TICKS
                && this.tryDiggableAdvance(gameTime, "局部卡死，切换挖掘脱困")) {
            return;
        }
        boolean climbNeeded = waypoint != null && waypoint.y > this.entity.getY() + 0.45D;
        if (this.entity.onGround()
                && !this.entity.runtimeHasLowCeiling()
                && (this.entity.horizontalCollision || climbNeeded)
                && gameTime - this.lastRecoveryJumpTick >= RECOVERY_JUMP_COOLDOWN_TICKS) {
            this.entity.getJumpControl().jump();
            this.lastRecoveryJumpTick = gameTime;
        }

        float yawRadians = this.entity.getYRot() * ((float) Math.PI / 180.0F);
        double side = (sample.stuckTicks() / LOCAL_RECOVERY_TICKS) % 2 == 0 ? 0.55D : -0.55D;
        double offsetX = Mth.cos(yawRadians) * side;
        double offsetZ = -Mth.sin(yawRadians) * side;
        Vec3 sidestep = this.entity.position().add(offsetX, 0.0D, offsetZ);
        this.entity.runtimeClearWasdOverride();
        this.entity.getMoveControl().setWantedPosition(sidestep.x, sidestep.y, sidestep.z, 0.92D);

        if (sample.stuckTicks() >= LOCAL_REPOSITION_TICKS && this.targetPos != null) {
            BlockPos localStandPos = this.entity.runtimeResolveMovementTarget(this.targetPos);
            if (localStandPos != null && !localStandPos.equals(this.targetPos)) {
                this.targetPos = localStandPos.immutable();
                applyNewPath(List.of(PathNode.move(Vec3.atCenterOf(localStandPos))), gameTime);
                this.pathStatus = "切换到局部可站立点";
            }
        }

        if (sample.stuckTicks() >= REPLAN_TICKS && this.targetPos != null) {
            if (!this.pathCooldown.canAttemptSevereReplan(gameTime)) {
                this.pathStatus = "等待重算退避窗口";
                return;
            }
            if (TeamKnowledge.tryAcquirePathBudget(this.entity, gameTime)) {
                List<PathNode> replanned = AgentPathPlanner.plan(this.entity, this.targetPos);
                if (replanned.isEmpty()) {
                    replanned = List.of(PathNode.move(Vec3.atCenterOf(this.targetPos)));
                    this.pathStatus = "重算失败，改为直控逼近";
                } else {
                    this.pathStatus = "路径重算完成";
                }
                this.pathState = PathState.REPATH;
                applyNewPath(replanned, gameTime);
                this.pathCooldown.markSevereReplan(gameTime);
            } else {
                this.pathStatus = "团队路径预算忙，等待重算窗口";
            }
        }
    }

    private boolean tryHandleNodeAction(PathNode node, long gameTime) {
        if (node == null || node.action() == PathNodeAction.NONE || node.actionPos() == null) {
            return true;
        }
        BlockPos actionPos = node.actionPos();
        double actionDistanceSqr = node.action() == PathNodeAction.BREAK_BLOCK ? 20.25D : 16.0D;
        boolean nearAction = this.entity.runtimeIsWithin(actionPos, actionDistanceSqr);
        if (!nearAction) {
            applyMoveToward(Vec3.atCenterOf(actionPos), 0.92D, node.jumpRequired(), gameTime);
            this.pathStatus = "接近路径动作点";
            return false;
        }

        if (node.action() == PathNodeAction.BREAK_BLOCK && !this.entity.runtimeCanHarvestFromHere(actionPos)) {
            applyMoveToward(Vec3.atCenterOf(actionPos), 0.85D, true, gameTime);
            this.pathStatus = "清障点进入开挖距离";
            return false;
        }

        if (gameTime - this.lastNodeActionTick < NODE_ACTION_COOLDOWN_TICKS) {
            this.pathStatus = "动作冷却中";
            applyMoveToward(Vec3.atCenterOf(actionPos), 0.62D, false, gameTime);
            return false;
        }
        this.lastNodeActionTick = gameTime;

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

    private void applyMoveToward(Vec3 waypoint, double speedHint, boolean jump, long gameTime) {
        Vec3 current = this.entity.position();
        double dx = waypoint.x - current.x;
        double dz = waypoint.z - current.z;
        double horizontalDistanceSqr = dx * dx + dz * dz;
        if (horizontalDistanceSqr < 1.0E-4D) {
            this.entity.runtimeClearWasdOverride();
            this.entity.setZza(0.0F);
            this.entity.setXxa(0.0F);
            this.entity.setSpeed(0.0F);
            this.entity.setSprinting(false);
            return;
        }

        this.entity.runtimeClearWasdOverride();
        double moveSpeed = Mth.clamp(speedHint <= 0.0D ? 0.82D : speedHint, 0.55D, 1.35D);
        if (horizontalDistanceSqr < 0.20D) {
            moveSpeed = Math.min(moveSpeed, 0.72D);
        }
        this.entity.getMoveControl().setWantedPosition(waypoint.x, waypoint.y, waypoint.z, moveSpeed);

        boolean shouldJump = (jump && this.entity.onGround() && (waypoint.y - current.y > 0.2D || this.entity.horizontalCollision))
                || (waypoint.y - current.y > 0.52D && this.entity.onGround());
        if (this.entity.runtimeHasLowCeiling()) {
            shouldJump = false;
        }
        if (this.entity.isInWater() || this.entity.isUnderWater() || this.entity.isInLava()) {
            shouldJump = this.entity.isUnderWater()
                    || this.entity.horizontalCollision
                    || waypoint.y > current.y + 0.35D;
        }
        if (shouldJump && gameTime - this.lastRecoveryJumpTick >= RECOVERY_JUMP_COOLDOWN_TICKS / 2) {
            this.entity.getJumpControl().jump();
            this.lastRecoveryJumpTick = gameTime;
        }
    }

    private void driveDirectFallback(long gameTime) {
        if (this.targetPos == null) {
            return;
        }
        BlockPos resolved = this.entity.runtimeResolveMovementTarget(this.targetPos);
        Vec3 fallback = Vec3.atCenterOf(resolved != null ? resolved : this.targetPos);
        detectStuckAndRecover(fallback, gameTime);
        if (this.targetPos == null) {
            return;
        }
        double distance = this.entity.distanceToSqr(fallback);
        double speedHint = Math.max(0.82D, computeAppliedSpeed(distance));
        boolean shouldJump = this.entity.horizontalCollision || fallback.y - this.entity.getY() > 0.48D;
        applyMoveToward(fallback, speedHint, shouldJump, gameTime);
        this.pathState = PathState.MOVING;
        this.pathStatus = "路径空闲兜底：直控逼近目标";
    }

    private void handleSpinRecovery(long gameTime) {
        if (this.targetPos == null) {
            return;
        }
        BlockPos resolved = this.entity.runtimeResolveMovementTarget(this.targetPos);
        if (resolved != null) {
            this.targetPos = resolved.immutable();
            applyNewPath(List.of(PathNode.move(Vec3.atCenterOf(resolved))), gameTime);
            this.pathState = PathState.REPATH;
            this.pathStatus = "检测到原地转圈，重置为局部直控路径";
        } else {
            this.activePath = List.of();
            this.pathState = PathState.STUCK;
            this.pathStatus = "检测到原地转圈，等待重算路径";
        }
        this.stuckDetector.setStuckAtLeast(LOCAL_RECOVERY_TICKS);
    }

    private boolean tryDiggableAdvance(long gameTime, String statusMessage) {
        if (this.targetPos == null) {
            return false;
        }
        if (gameTime - this.lastDiggableAttemptTick < DIGGABLE_RETRY_COOLDOWN_TICKS) {
            return false;
        }
        this.lastDiggableAttemptTick = gameTime;
        if (!this.entity.runtimeTryDiggableAdvance(this.targetPos)) {
            return false;
        }
        this.pathState = PathState.STUCK;
        this.pathStatus = statusMessage;
        return true;
    }

    private boolean isNearWaypoint(Vec3 waypoint, double maxXzDistanceSqr, double maxYAbs) {
        double dx = waypoint.x - this.entity.getX();
        double dz = waypoint.z - this.entity.getZ();
        if (dx * dx + dz * dz > maxXzDistanceSqr) {
            return false;
        }
        return Math.abs(waypoint.y - this.entity.getY()) <= maxYAbs;
    }

    private enum PathState {
        IDLE,
        PATHING,
        MOVING,
        STUCK,
        REPATH
    }
}

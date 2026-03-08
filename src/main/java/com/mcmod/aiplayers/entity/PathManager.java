package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.PathGoal;
import com.mcmod.aiplayers.vendor.baritone.behavior.PathingBehavior;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.CalculationContext;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.Movement;
import com.mcmod.aiplayers.vendor.baritone.pathing.path.Path;
import com.mcmod.aiplayers.vendor.baritone.pathing.path.PathExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class PathManager {
    private static final int LOCAL_RECOVERY_TICKS = 8;
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

    private final AIPlayerEntity entity;
    private final PathCooldown pathCooldown = new PathCooldown(PATH_REPLAN_COOLDOWN_TICKS, REPLAN_BACKOFF_TICKS);
    private final StuckDetector stuckDetector = new StuckDetector(
            STUCK_CHECK_INTERVAL_TICKS,
            STUCK_PROGRESS_THRESHOLD_SQR,
            SPIN_PROGRESS_THRESHOLD_SQR,
            SPIN_YAW_DELTA_THRESHOLD,
            SPIN_TRIGGER_TICKS);
    private final AntiStuckSystem antiStuckSystem = new AntiStuckSystem(this.stuckDetector);
    private final BaritoneEntityContextAdapter baritoneContext;
    private final BaritoneMovementExecutorAdapter movementExecutor;
    private final PathingBehavior pathingBehavior;

    private PathPlan currentPlan = new PathPlan(null, java.util.List.of(), "idle");
    private PathGoal targetGoal;
    private BlockPos targetPos;
    private double speedModifier = 1.0D;
    private PathState pathState = PathState.IDLE;
    private String pathStatus = "空闲";
    private int recoveryTicks;
    private AntiStuckSystem.StuckSummary lastStuckSummary = new AntiStuckSystem.StuckSummary(0, 0, 0, 0, 0, false, false);

    PathManager(AIPlayerEntity entity) {
        this.entity = entity;
        this.baritoneContext = new BaritoneEntityContextAdapter(entity);
        this.movementExecutor = new BaritoneMovementExecutorAdapter(this.baritoneContext, this.baritoneContext.playerController());
        this.pathingBehavior = new PathingBehavior(this.baritoneContext, this.movementExecutor);
    }

    boolean requestPathTo(BlockPos target, double speedModifier) {
        return this.requestPath(BaritoneGoalAdapter.forBlock(target), speedModifier);
    }

    boolean requestPath(PathGoal goal, double speedModifier) {
        if (goal == null) {
            this.clear();
            this.pathStatus = "目标为空";
            return false;
        }
        long gameTime = this.entity.level().getGameTime();
        BlockPos estimatedTarget = BaritoneGoalAdapter.estimateTargetPos(goal, this.entity.blockPosition());
        this.movementExecutor.setSpeedScale(speedModifier);
        this.speedModifier = speedModifier;

        if (goal.isInGoal(this.entity.blockPosition())) {
            this.targetGoal = null;
            this.targetPos = estimatedTarget == null ? null : estimatedTarget.immutable();
            this.pathState = PathState.IDLE;
            this.pathStatus = "已在目标范围";
            this.pathingBehavior.cancelEverything();
            this.updatePlanFromBehavior();
            return false;
        }

        boolean sameTargetRegion = this.targetPos != null && estimatedTarget != null && this.targetPos.distSqr(estimatedTarget) <= 4.0D;
        if (sameTargetRegion && this.pathingBehavior.isPathing() && !this.isSeverelyStuck()) {
            this.targetGoal = goal;
            this.targetPos = estimatedTarget.immutable();
            this.pathState = PathState.MOVING;
            this.pathStatus = "沿用 vendor 路径";
            this.updatePlanFromBehavior();
            return true;
        }
        if (this.pathCooldown.canReuseRecentPath(sameTargetRegion, this.pathingBehavior.getCurrent() != null, this.isSeverelyStuck(), gameTime)) {
            this.targetGoal = goal;
            this.targetPos = estimatedTarget == null ? null : estimatedTarget.immutable();
            this.pathState = PathState.MOVING;
            this.pathStatus = "沿用冷却期 vendor 路径";
            this.updatePlanFromBehavior();
            return true;
        }

        boolean budgetGranted = TeamKnowledge.tryAcquirePathBudget(this.entity, gameTime);
        if (!budgetGranted && this.pathingBehavior.isPathing() && !this.isSeverelyStuck()) {
            this.targetGoal = goal;
            this.targetPos = estimatedTarget == null ? null : estimatedTarget.immutable();
            this.pathState = PathState.MOVING;
            this.pathStatus = "团队路径预算忙，沿用当前 vendor 路径";
            this.updatePlanFromBehavior();
            return true;
        }

        this.targetGoal = goal;
        this.targetPos = estimatedTarget == null ? null : estimatedTarget.immutable();
        this.pathState = PathState.PATHING;
        this.pathCooldown.markPathRequest(gameTime);
        boolean started = this.pathingBehavior.secretInternalSetGoalAndPath(goal, new CalculationContext(this.baritoneContext));
        this.pathStatus = started ? "Baritone 路径已生成" : "Baritone 路径计算失败";
        this.updatePlanFromBehavior();
        return started || this.pathingBehavior.getCurrentPath() != null;
    }

    void clear() {
        this.targetGoal = null;
        this.targetPos = null;
        this.speedModifier = 1.0D;
        this.pathState = PathState.IDLE;
        this.pathStatus = "空闲";
        this.recoveryTicks = 0;
        this.currentPlan = new PathPlan(null, java.util.List.of(), "idle");
        this.pathCooldown.clear();
        this.stuckDetector.clear();
        this.antiStuckSystem.clear();
        this.lastStuckSummary = new AntiStuckSystem.StuckSummary(0, 0, 0, 0, 0, false, false);
        this.pathingBehavior.cancelEverything();
        this.movementExecutor.clearInput();
    }

    void tick() {
        if (this.targetGoal == null) {
            this.pathState = PathState.IDLE;
            this.pathStatus = "空闲";
            this.currentPlan = new PathPlan(null, java.util.List.of(), "idle");
            return;
        }
        long gameTime = this.entity.level().getGameTime();
        this.movementExecutor.setSpeedScale(this.speedModifier);

        if (this.targetGoal.isInGoal(this.entity.blockPosition())) {
            this.finishAtGoal();
            return;
        }

        if (this.recoveryTicks > 0) {
            this.tickRecovery();
            this.updatePlanFromBehavior();
            return;
        }

        this.pathingBehavior.tick();
        this.sampleAntiStuck(gameTime);

        if (this.lastStuckSummary.severe() && this.pathCooldown.canAttemptSevereReplan(gameTime)) {
            this.beginRecovery("严重卡住，准备 vendor 重算");
            this.pathCooldown.markSevereReplan(gameTime);
        } else if (this.lastStuckSummary.recoverNeeded() && this.recoveryTicks == 0) {
            this.beginRecovery("检测到抖动，执行微退恢复");
        } else if (this.pathingBehavior.failed() && this.pathCooldown.canAttemptSevereReplan(gameTime)) {
            this.beginRecovery("movement 失败，准备重新规划");
            this.pathCooldown.markSevereReplan(gameTime);
        }

        if (this.targetGoal != null && this.targetGoal.isInGoal(this.entity.blockPosition())) {
            this.finishAtGoal();
            return;
        }

        this.updatePlanFromBehavior();
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
        return this.targetGoal != null;
    }

    boolean isIdle() {
        return this.targetGoal == null;
    }

    boolean isRecovering() {
        return this.recoveryTicks > 0 || this.lastStuckSummary.recoverNeeded();
    }

    boolean isSeverelyStuck() {
        return this.lastStuckSummary.severe();
    }

    String getPathStatus() {
        return this.pathState.name() + "|" + this.pathStatus;
    }

    PathPlan getCurrentPlan() {
        return this.currentPlan;
    }

    BlockPos getActiveTargetPos() {
        return this.targetPos;
    }

    private void finishAtGoal() {
        this.pathingBehavior.cancelEverything();
        this.movementExecutor.clearInput();
        this.pathState = PathState.IDLE;
        this.pathStatus = "已到达目标";
        this.currentPlan = new PathPlan(this.targetPos, java.util.List.of(), "arrived");
        this.targetGoal = null;
        this.targetPos = null;
        this.recoveryTicks = 0;
    }

    private void sampleAntiStuck(long gameTime) {
        PathExecutor executor = this.pathingBehavior.getCurrent();
        Movement movement = executor == null ? null : executor.currentMovement();
        PathNode currentNode = this.toPathNode(movement);
        boolean breakProgress = movement == null
                || movement.getActionPos() == null
                || this.entity.level().getBlockState(movement.getActionPos()).isAir();
        this.lastStuckSummary = this.antiStuckSystem.sample(
                this.entity.position(),
                this.entity.getYRot(),
                gameTime,
                currentNode,
                breakProgress,
                this.pathingBehavior.isRepathing());
    }

    private void beginRecovery(String statusMessage) {
        this.recoveryTicks = LOCAL_RECOVERY_TICKS;
        this.pathState = PathState.STUCK;
        this.pathStatus = statusMessage;
        this.movementExecutor.clearInput();
    }

    private void tickRecovery() {
        this.pathState = PathState.STUCK;
        if (this.recoveryTicks > 2) {
            Vec3 target = this.targetPos == null ? this.entity.position().add(this.entity.getLookAngle()) : Vec3.atCenterOf(this.targetPos);
            double dx = target.x - this.entity.getX();
            double dz = target.z - this.entity.getZ();
            float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
            this.entity.runtimeSetWasdControl(-0.65F, 0.0F, 0.2F, targetYaw, false, this.entity.horizontalCollision);
            this.pathStatus = "AntiStuck：微退一步";
        } else if (this.recoveryTicks == 2) {
            this.movementExecutor.clearInput();
            this.pathingBehavior.forceRepath("anti_stuck_repath");
            this.pathState = PathState.REPATH;
            this.pathStatus = "AntiStuck：重新规划路径";
        } else {
            this.pathStatus = "AntiStuck：恢复完成";
        }
        this.recoveryTicks--;
        if (this.recoveryTicks <= 0) {
            this.movementExecutor.clearInput();
        }
    }

    private void updatePlanFromBehavior() {
        Path path = this.pathingBehavior.getCurrentPath();
        String movementName = "none";
        PathExecutor executor = this.pathingBehavior.getCurrent();
        if (executor != null && executor.currentMovement() != null) {
            movementName = executor.currentMovement().name();
        }
        if (this.targetGoal == null) {
            this.currentPlan = new PathPlan(this.targetPos, java.util.List.of(), "idle");
            this.pathState = PathState.IDLE;
            return;
        }
        if (this.pathingBehavior.isRepathing()) {
            this.pathState = PathState.PATHING;
        } else if (this.recoveryTicks > 0 || this.lastStuckSummary.recoverNeeded()) {
            this.pathState = PathState.STUCK;
        } else if (executor != null && !executor.finished() && !executor.failed()) {
            this.pathState = PathState.MOVING;
        } else if (this.pathingBehavior.failed()) {
            this.pathState = PathState.STUCK;
        } else {
            this.pathState = PathState.PATHING;
        }
        this.pathStatus = "goal=" + (this.targetPos == null ? "none" : this.targetPos.toShortString())
                + ",movement=" + movementName
                + ",calc=" + this.pathingBehavior.lastCalculation().type().name()
                + ",repath=" + this.pathingBehavior.repathReason()
                + ",state=" + this.pathingBehavior.status();
        this.currentPlan = AgentPathPlanner.toPlan(this.targetPos, path, this.pathStatus);
    }

    private PathNode toPathNode(Movement movement) {
        if (movement == null) {
            return null;
        }
        return PathNode.withAction(
                Vec3.atCenterOf(movement.getDest()),
                this.resolveAction(movement),
                movement.getActionPos(),
                movement.jumpRequired());
    }

    private PathNodeAction resolveAction(Movement movement) {
        if (movement == null) {
            return PathNodeAction.NONE;
        }
        String name = movement.name();
        if (name.contains("Break")) {
            return PathNodeAction.BREAK_BLOCK;
        }
        if (name.contains("Place")) {
            return PathNodeAction.PLACE_SUPPORT;
        }
        if (movement.jumpRequired()) {
            return PathNodeAction.JUMP_ASCEND;
        }
        return PathNodeAction.NONE;
    }

    private enum PathState {
        IDLE,
        PATHING,
        MOVING,
        STUCK,
        REPATH
    }
}

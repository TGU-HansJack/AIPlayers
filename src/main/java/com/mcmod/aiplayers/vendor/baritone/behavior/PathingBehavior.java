package com.mcmod.aiplayers.vendor.baritone.behavior;

import com.mcmod.aiplayers.entity.AsyncPathfindingService;
import com.mcmod.aiplayers.entity.BaritoneEntityContextAdapter;
import com.mcmod.aiplayers.entity.BaritoneGoalAdapter;
import com.mcmod.aiplayers.entity.BaritoneMovementExecutorAdapter;
import com.mcmod.aiplayers.entity.ChunkSnapshotCache;
import com.mcmod.aiplayers.entity.PathReuseCache;
import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.PathGoal;
import com.mcmod.aiplayers.vendor.baritone.pathing.calc.PathCalculationResult;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.CalculationContext;
import com.mcmod.aiplayers.vendor.baritone.pathing.path.Path;
import com.mcmod.aiplayers.vendor.baritone.pathing.path.PathExecutor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import net.minecraft.core.BlockPos;

// Upstream reference: baritone-1.21.11/src/main/java/baritone/behavior/PathingBehavior.java
public final class PathingBehavior {
    private static final long REPATH_COOLDOWN_TICKS = 8L;
    private static final int SEARCH_NODE_LIMIT = 4096;

    private final BaritoneEntityContextAdapter entityContext;
    private final BaritoneMovementExecutorAdapter movementExecutor;

    private PathGoal goal;
    private CalculationContext calculationContext;
    private PathExecutor current;
    private PathCalculationResult lastCalculation = new PathCalculationResult(PathCalculationResult.Type.FAILURE, null);
    private String status = "idle";
    private String repathReason = "none";
    private long lastRepathTick = Long.MIN_VALUE;
    private boolean failed;
    private boolean repathing;
    private CompletableFuture<AsyncPathfindingService.PathTaskResult> inProgress;
    private long requestToken;
    private BlockPos pendingStart;
    private BlockPos pendingTarget;
    private long pendingSnapshotSignature;

    public PathingBehavior(BaritoneEntityContextAdapter entityContext, BaritoneMovementExecutorAdapter movementExecutor) {
        this.entityContext = entityContext;
        this.movementExecutor = movementExecutor;
        this.calculationContext = null;
    }

    public void tick() {
        if (!this.entityContext.isServerLevel()) {
            this.status = "client_inactive";
            return;
        }
        this.completeIfReady();
        if (this.goal == null) {
            this.status = "idle";
            return;
        }
        if (this.goal.isInGoal(this.entityContext.playerFeet())) {
            this.current = null;
            this.failed = false;
            this.repathing = false;
            this.status = "at_goal";
            this.movementExecutor.clearInput();
            return;
        }
        if (this.current == null) {
            if (this.inProgress == null) {
                this.scheduleCalculation("missing_path");
            } else {
                this.status = "calc_pending:" + this.repathReason;
            }
            return;
        }
        this.repathing = false;
        this.failed = false;
        this.current.onTick();
        this.status = this.current.status();
        if (this.current.failed()) {
            this.failed = true;
            this.current = null;
            if (this.canRepath()) {
                this.scheduleCalculation("movement_failed");
            }
            return;
        }
        if (this.current.finished()) {
            if (this.goal.isInGoal(this.entityContext.playerFeet()) || this.goal.isInGoal(this.current.getPath().getDest())) {
                this.current = null;
                this.status = "at_goal";
                this.movementExecutor.clearInput();
            } else if (this.canRepath()) {
                this.current = null;
                this.scheduleCalculation("segment_finished");
            }
        }
    }

    public void secretInternalSetGoal(PathGoal goal) {
        this.goal = goal;
    }

    public boolean secretInternalSetGoalAndPath(PathGoal goal, CalculationContext calculationContext) {
        this.goal = goal;
        this.calculationContext = calculationContext;
        this.current = null;
        this.failed = false;
        this.cancelPendingCalculation();
        if (!this.entityContext.isServerLevel()) {
            this.status = "client_inactive";
            return false;
        }
        if (this.goal == null || this.goal.isInGoal(this.entityContext.playerFeet())) {
            this.status = this.goal == null ? "idle" : "at_goal";
            return false;
        }
        return this.scheduleCalculation("goal_update");
    }

    public boolean forceRepath(String reason) {
        if (this.goal == null || !this.entityContext.isServerLevel()) {
            return false;
        }
        this.current = null;
        this.cancelPendingCalculation();
        return this.scheduleCalculation(reason == null ? "manual_repath" : reason);
    }

    public void cancelEverything() {
        this.goal = null;
        this.current = null;
        this.failed = false;
        this.repathing = false;
        this.status = "cancelled";
        this.repathReason = "cancelled";
        this.cancelPendingCalculation();
        this.movementExecutor.clearInput();
    }

    public PathGoal getGoal() {
        return this.goal;
    }

    public boolean isPathing() {
        return this.goal != null && ((this.current != null && !this.current.finished() && !this.current.failed()) || this.inProgress != null);
    }

    public boolean isRepathing() {
        return this.repathing || this.inProgress != null;
    }

    public boolean failed() {
        return this.failed;
    }

    public PathExecutor getCurrent() {
        return this.current;
    }

    public Path getCurrentPath() {
        return this.current == null ? this.lastCalculation.path() : this.current.getPath();
    }

    public String status() {
        return this.status;
    }

    public String repathReason() {
        return this.repathReason;
    }

    public PathCalculationResult lastCalculation() {
        return this.lastCalculation;
    }

    private boolean scheduleCalculation(String reason) {
        if (this.goal == null || !this.entityContext.isServerLevel()) {
            return false;
        }
        BlockPos start = this.entityContext.playerFeet().immutable();
        BlockPos target = BaritoneGoalAdapter.estimateTargetPos(this.goal, start);
        ChunkSnapshotCache.RegionSnapshot snapshot = ChunkSnapshotCache.capture(this.entityContext.level(), start, target);
        Path cached = target == null ? null : PathReuseCache.get(this.entityContext.dimensionId(), start, target, snapshot.signature(), this.entityContext.gameTime());
        if (cached != null) {
            this.current = new PathExecutor(this.entityContext, this.movementExecutor, cached);
            this.lastCalculation = new PathCalculationResult(cached.reachesGoal() ? PathCalculationResult.Type.SUCCESS_TO_GOAL : PathCalculationResult.Type.SUCCESS_SEGMENT, cached);
            this.status = "reuse_cache";
            this.repathReason = "reuse_cache";
            this.repathing = false;
            this.failed = false;
            return true;
        }
        this.repathReason = reason == null ? "repath" : reason;
        this.repathing = true;
        this.failed = false;
        this.lastRepathTick = this.entityContext.gameTime();
        this.pendingStart = start;
        this.pendingTarget = target == null ? start : target.immutable();
        this.pendingSnapshotSignature = snapshot.signature();
        this.calculationContext = new CalculationContext(this.entityContext, snapshot, SEARCH_NODE_LIMIT);
        long token = ++this.requestToken;
        this.inProgress = AsyncPathfindingService.submit(token, start, this.goal, this.calculationContext);
        this.status = "calc_pending:" + this.repathReason;
        return true;
    }

    private void completeIfReady() {
        if (this.inProgress == null || !this.inProgress.isDone()) {
            return;
        }
        CompletableFuture<AsyncPathfindingService.PathTaskResult> future = this.inProgress;
        this.inProgress = null;
        try {
            AsyncPathfindingService.PathTaskResult taskResult = future.get();
            if (taskResult.token() != this.requestToken) {
                return;
            }
            this.lastCalculation = taskResult.result();
            this.repathing = false;
            Path path = this.lastCalculation.path();
            if (path == null) {
                this.current = null;
                this.failed = true;
                this.status = "calc_failed:" + this.lastCalculation.type().name();
                return;
            }
            this.current = new PathExecutor(this.entityContext, this.movementExecutor, path);
            this.failed = false;
            this.status = "calc_ready:" + this.lastCalculation.type().name();
            if (this.pendingTarget != null) {
                PathReuseCache.put(this.entityContext.dimensionId(), this.pendingStart, this.pendingTarget, this.pendingSnapshotSignature, this.entityContext.gameTime(), path);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.failed = true;
            this.repathing = false;
            this.status = "calc_interrupted";
        } catch (ExecutionException exception) {
            this.failed = true;
            this.repathing = false;
            this.status = "calc_exception";
        }
    }

    private void cancelPendingCalculation() {
        if (this.inProgress != null) {
            this.inProgress.cancel(false);
            this.inProgress = null;
        }
    }

    private boolean canRepath() {
        return this.lastRepathTick == Long.MIN_VALUE || this.entityContext.gameTime() - this.lastRepathTick >= REPATH_COOLDOWN_TICKS;
    }
}

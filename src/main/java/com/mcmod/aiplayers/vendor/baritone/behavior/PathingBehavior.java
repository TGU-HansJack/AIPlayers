package com.mcmod.aiplayers.vendor.baritone.behavior;

import com.mcmod.aiplayers.entity.BaritoneEntityContextAdapter;
import com.mcmod.aiplayers.entity.BaritoneMovementExecutorAdapter;
import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.PathGoal;
import com.mcmod.aiplayers.vendor.baritone.pathing.calc.AStarPathFinder;
import com.mcmod.aiplayers.vendor.baritone.pathing.calc.PathCalculationResult;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.CalculationContext;
import com.mcmod.aiplayers.vendor.baritone.pathing.path.Path;
import com.mcmod.aiplayers.vendor.baritone.pathing.path.PathExecutor;
import java.util.Objects;

// Upstream reference: baritone-1.21.11/src/main/java/baritone/behavior/PathingBehavior.java
public final class PathingBehavior {
    private static final long PRIMARY_TIMEOUT_MS = 30L;
    private static final long FAILURE_TIMEOUT_MS = 60L;
    private static final long REPATH_COOLDOWN_TICKS = 8L;

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

    public PathingBehavior(BaritoneEntityContextAdapter entityContext, BaritoneMovementExecutorAdapter movementExecutor) {
        this.entityContext = entityContext;
        this.movementExecutor = movementExecutor;
        this.calculationContext = new CalculationContext(entityContext);
    }

    public void tick() {
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
            this.recalculate("missing_path");
            return;
        }
        this.repathing = false;
        this.failed = false;
        this.current.onTick();
        this.status = this.current.status();
        if (this.current.failed()) {
            this.failed = true;
            if (this.canRepath()) {
                this.recalculate("movement_failed");
            }
            return;
        }
        if (this.current.finished()) {
            if (this.goal.isInGoal(this.entityContext.playerFeet()) || this.goal.isInGoal(this.current.getPath().getDest())) {
                this.current = null;
                this.status = "at_goal";
                this.movementExecutor.clearInput();
            } else if (this.canRepath()) {
                this.recalculate("segment_finished");
            }
        }
    }

    public void secretInternalSetGoal(PathGoal goal) {
        this.goal = goal;
    }

    public boolean secretInternalSetGoalAndPath(PathGoal goal, CalculationContext calculationContext) {
        this.goal = goal;
        this.calculationContext = calculationContext == null ? new CalculationContext(this.entityContext) : calculationContext;
        if (this.goal == null || this.goal.isInGoal(this.entityContext.playerFeet())) {
            this.current = null;
            this.status = this.goal == null ? "idle" : "at_goal";
            return false;
        }
        return this.recalculate("goal_update");
    }

    public boolean forceRepath(String reason) {
        if (this.goal == null) {
            return false;
        }
        return this.recalculate(reason == null ? "manual_repath" : reason);
    }

    public void cancelEverything() {
        this.goal = null;
        this.current = null;
        this.failed = false;
        this.repathing = false;
        this.status = "cancelled";
        this.repathReason = "cancelled";
        this.movementExecutor.clearInput();
    }

    public PathGoal getGoal() {
        return this.goal;
    }

    public boolean isPathing() {
        return this.goal != null && this.current != null && !this.current.finished() && !this.current.failed();
    }

    public boolean isRepathing() {
        return this.repathing;
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

    private boolean recalculate(String reason) {
        if (this.goal == null) {
            return false;
        }
        this.repathing = true;
        this.repathReason = reason == null ? "repath" : reason;
        AStarPathFinder pathFinder = new AStarPathFinder(this.entityContext.playerFeet(), this.goal, this.calculationContext);
        this.lastCalculation = pathFinder.calculate(PRIMARY_TIMEOUT_MS, FAILURE_TIMEOUT_MS);
        this.lastRepathTick = this.entityContext.gameTime();
        Path path = this.lastCalculation.path();
        if (path == null) {
            this.current = null;
            this.failed = true;
            this.status = "calc_failed:" + this.lastCalculation.type().name();
            this.repathing = false;
            return false;
        }
        this.current = new PathExecutor(this.entityContext, this.movementExecutor, path);
        this.failed = false;
        this.status = "calc_ready:" + this.lastCalculation.type().name();
        this.repathing = false;
        return true;
    }

    private boolean canRepath() {
        return this.lastRepathTick == Long.MIN_VALUE || this.entityContext.gameTime() - this.lastRepathTick >= REPATH_COOLDOWN_TICKS;
    }
}

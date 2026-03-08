package com.mcmod.aiplayers.vendor.baritone.pathing.path;

import com.mcmod.aiplayers.entity.BaritoneEntityContextAdapter;
import com.mcmod.aiplayers.entity.BaritoneMovementExecutorAdapter;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.Movement;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.MovementStatus;
import net.minecraft.core.BlockPos;

// Upstream reference: baritone-1.21.11/src/main/java/baritone/pathing/path/PathExecutor.java
public class PathExecutor {
    private final BaritoneEntityContextAdapter context;
    private final BaritoneMovementExecutorAdapter executor;
    private final Path path;

    private int pathPosition;
    private boolean failed;
    private String status = "idle";

    public PathExecutor(BaritoneEntityContextAdapter context, BaritoneMovementExecutorAdapter executor, Path path) {
        this.context = context;
        this.executor = executor;
        this.path = path;
        this.pathPosition = 0;
    }

    public boolean onTick() {
        if (this.finished()) {
            this.executor.clearInput();
            this.status = "finished";
            return true;
        }
        Movement movement = this.currentMovement();
        if (movement == null) {
            this.pathPosition = this.path.length();
            this.status = "finished";
            this.executor.clearInput();
            return true;
        }
        if (this.executor.hasReached(movement.getDest())) {
            movement.reset();
            this.pathPosition++;
            this.status = "advanced";
            return true;
        }
        MovementStatus movementStatus = movement.update(this.context, this.executor);
        this.status = movement.name() + ':' + movementStatus.name();
        if (movementStatus == MovementStatus.SUCCESS) {
            movement.reset();
            this.pathPosition++;
            if (this.finished()) {
                this.executor.clearInput();
            }
            return true;
        }
        if (movementStatus == MovementStatus.FAILED || movementStatus == MovementStatus.BLOCKED) {
            this.failed = true;
            this.executor.clearInput();
            return true;
        }
        return false;
    }

    public Movement currentMovement() {
        if (this.pathPosition < 0 || this.pathPosition >= this.path.length()) {
            return null;
        }
        return this.path.movements().get(this.pathPosition);
    }

    public BlockPos currentDest() {
        Movement movement = this.currentMovement();
        return movement == null ? this.path.getDest() : movement.getDest();
    }

    public int currentIndex() {
        return this.pathPosition;
    }

    public boolean snipsnapifpossible() {
        return false;
    }

    public PathExecutor trySplice(PathExecutor next) {
        return this;
    }

    public Path getPath() {
        return this.path;
    }

    public boolean failed() {
        return this.failed;
    }

    public boolean finished() {
        return this.pathPosition >= this.path.length();
    }

    public String status() {
        return this.status;
    }
}

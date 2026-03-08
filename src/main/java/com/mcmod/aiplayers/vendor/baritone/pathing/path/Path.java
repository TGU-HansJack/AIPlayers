package com.mcmod.aiplayers.vendor.baritone.pathing.path;

import com.mcmod.aiplayers.vendor.baritone.pathing.movement.Movement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

public final class Path {
    private final BlockPos start;
    private final List<Movement> movements;
    private final boolean reachesGoal;
    private final int nodesConsidered;

    public Path(BlockPos start, List<Movement> movements, boolean reachesGoal) {
        this(start, movements, reachesGoal, 0);
    }

    public Path(BlockPos start, List<Movement> movements, boolean reachesGoal, int nodesConsidered) {
        this.start = start == null ? BlockPos.ZERO : start.immutable();
        this.movements = movements == null ? List.of() : List.copyOf(movements);
        this.reachesGoal = reachesGoal;
        this.nodesConsidered = Math.max(0, nodesConsidered);
    }

    public BlockPos getSrc() {
        return this.start;
    }

    public List<Movement> movements() {
        return this.movements;
    }

    public List<BlockPos> positions() {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(this.start);
        for (Movement movement : this.movements) {
            positions.add(movement.getDest());
        }
        return positions;
    }

    public int length() {
        return this.movements.size();
    }

    public BlockPos getDest() {
        return this.movements.isEmpty() ? this.start : this.movements.getLast().getDest();
    }

    public boolean reachesGoal() {
        return this.reachesGoal;
    }

    public int getNumNodesConsidered() {
        return this.nodesConsidered;
    }
}

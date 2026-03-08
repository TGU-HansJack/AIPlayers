package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.Goal;
import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.GoalBlock;
import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.GoalComposite;
import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.PathGoal;
import java.util.List;
import net.minecraft.core.BlockPos;

public final class GoalStandAt implements PathGoal {
    private final GoalComposite delegate;
    private final List<BlockPos> standPositions;

    public GoalStandAt(List<BlockPos> standPositions) {
        this.standPositions = standPositions == null ? List.of() : standPositions.stream().filter(pos -> pos != null).map(BlockPos::immutable).toList();
        Goal[] delegates = this.standPositions.stream().map(GoalBlock::new).toArray(Goal[]::new);
        this.delegate = new GoalComposite(delegates);
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return this.delegate.isInGoal(x, y, z);
    }

    @Override
    public double heuristic(int x, int y, int z) {
        return this.delegate.heuristic(x, y, z);
    }

    public List<BlockPos> standPositions() {
        return this.standPositions;
    }
}

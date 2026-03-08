package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.Goal;
import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.GoalBlock;
import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.GoalComposite;
import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.GoalNear;
import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.PathGoal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class BaritoneGoalAdapter {
    private BaritoneGoalAdapter() {
    }

    public static PathGoal forBlock(BlockPos pos) {
        return pos == null ? null : new GoalNear(pos, 1);
    }

    public static PathGoal forStandPositions(List<BlockPos> positions) {
        return new GoalStandAt(positions);
    }

    public static PathGoal forOwnerFollow(ServerPlayer owner, int range) {
        return owner == null ? null : new GoalFollowEntity(owner, range);
    }

    public static PathGoal forAvoid(List<BlockPos> hazards, int range) {
        return new GoalAvoid(hazards, range);
    }

    public static BlockPos estimateTargetPos(Goal goal, BlockPos fallback) {
        if (goal instanceof GoalBlock goalBlock) {
            return goalBlock.getGoalPos();
        }
        if (goal instanceof GoalNear goalNear) {
            return goalNear.getGoalPos();
        }
        if (goal instanceof GoalStandAt standAt && !standAt.standPositions().isEmpty()) {
            return standAt.standPositions().getFirst();
        }
        if (goal instanceof GoalComposite composite) {
            List<BlockPos> positions = new ArrayList<>();
            for (Goal inner : composite.goals()) {
                BlockPos estimated = estimateTargetPos(inner, null);
                if (estimated != null) {
                    positions.add(estimated);
                }
            }
            return positions.isEmpty() ? fallback : positions.getFirst();
        }
        return fallback;
    }
}

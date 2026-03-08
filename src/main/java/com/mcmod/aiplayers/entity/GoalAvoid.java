package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.PathGoal;
import java.util.List;
import net.minecraft.core.BlockPos;

public final class GoalAvoid implements PathGoal {
    private final List<BlockPos> hazards;
    private final int avoidRangeSq;

    public GoalAvoid(List<BlockPos> hazards, int avoidRange) {
        this.hazards = hazards == null ? List.of() : hazards.stream().filter(pos -> pos != null).map(BlockPos::immutable).toList();
        this.avoidRangeSq = Math.max(1, avoidRange * avoidRange);
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        for (BlockPos hazard : this.hazards) {
            int dx = x - hazard.getX();
            int dy = y - hazard.getY();
            int dz = z - hazard.getZ();
            if (dx * dx + dy * dy + dz * dz <= this.avoidRangeSq) {
                return false;
            }
        }
        return true;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        double pressure = 0.0D;
        for (BlockPos hazard : this.hazards) {
            int dx = x - hazard.getX();
            int dy = y - hazard.getY();
            int dz = z - hazard.getZ();
            int distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < this.avoidRangeSq) {
                pressure = Math.max(pressure, Math.sqrt(this.avoidRangeSq - distSq));
            }
        }
        return pressure;
    }
}

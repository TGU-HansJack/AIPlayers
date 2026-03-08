package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.GoalBlock;
import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.PathGoal;
import net.minecraft.world.entity.Entity;

public final class GoalFollowEntity implements PathGoal {
    private final Entity target;
    private final int rangeSq;

    public GoalFollowEntity(Entity target, int range) {
        this.target = target;
        this.rangeSq = Math.max(1, range * range);
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        if (this.target == null) {
            return false;
        }
        int dx = x - this.target.blockPosition().getX();
        int dy = y - this.target.blockPosition().getY();
        int dz = z - this.target.blockPosition().getZ();
        return dx * dx + dy * dy + dz * dz <= this.rangeSq;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        if (this.target == null) {
            return Double.MAX_VALUE;
        }
        return GoalBlock.calculate(
                x - this.target.blockPosition().getX(),
                y - this.target.blockPosition().getY(),
                z - this.target.blockPosition().getZ());
    }
}

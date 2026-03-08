package com.mcmod.aiplayers.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class BaritonePlayerControllerAdapter {
    private final AIPlayerEntity entity;

    public BaritonePlayerControllerAdapter(AIPlayerEntity entity) {
        this.entity = entity;
    }

    public void clearInput() {
        this.entity.runtimeClearWasdOverride();
    }

    public void lookAt(BlockPos pos) {
        if (pos != null) {
            this.entity.runtimeLookAt(Vec3.atCenterOf(pos), 8);
        }
    }

    public void moveToward(Vec3 waypoint, double speedHint, boolean jump) {
        if (waypoint == null) {
            this.clearInput();
            return;
        }
        Vec3 current = this.entity.position();
        double dx = waypoint.x - current.x;
        double dz = waypoint.z - current.z;
        double horizontalDistanceSqr = dx * dx + dz * dz;
        if (horizontalDistanceSqr < 1.0E-4D) {
            this.clearInput();
            return;
        }
        float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float speed = (float) Mth.clamp(speedHint <= 0.0D ? 0.28D : speedHint * 0.28D, 0.16D, 0.42D);
        boolean sprint = horizontalDistanceSqr > 4.0D && !this.entity.isInWater() && !this.entity.isUnderWater() && !this.entity.isInLava();
        this.entity.runtimeSetWasdControl(1.0F, 0.0F, speed, targetYaw, sprint, jump);
    }

    public boolean canBreakBlock(BlockPos pos) {
        return this.entity.runtimeCanBreakPathBlock(pos);
    }

    public boolean breakBlock(BlockPos pos) {
        return this.entity.runtimeBreakPathNavigationBlock(pos);
    }

    public boolean canPlaceSupport(BlockPos pos) {
        return this.entity.runtimeCanPlacePathSupport(pos);
    }

    public boolean placeSupport(BlockPos pos) {
        return this.entity.runtimePlacePathSupport(pos);
    }

    public boolean isWithin(BlockPos pos, double maxDistanceSqr) {
        return this.entity.runtimeIsWithin(pos, maxDistanceSqr);
    }

    public boolean canStandAt(BlockPos pos, boolean allowWater) {
        return this.entity.runtimeCanStandAt(pos, allowWater);
    }
}

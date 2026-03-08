package com.mcmod.aiplayers.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class StuckDetector {
    private final int checkIntervalTicks;
    private final double progressThresholdSqr;
    private final double spinProgressThresholdSqr;
    private final float spinYawDeltaThreshold;
    private final int spinTriggerTicks;

    private Vec3 lastSample = Vec3.ZERO;
    private Vec3 lastSpinSample = Vec3.ZERO;
    private float lastSpinYaw;
    private int stuckTicks;
    private int spinTicks;
    private long lastCheckTick;

    StuckDetector(
            int checkIntervalTicks,
            double progressThresholdSqr,
            double spinProgressThresholdSqr,
            float spinYawDeltaThreshold,
            int spinTriggerTicks) {
        this.checkIntervalTicks = Math.max(1, checkIntervalTicks);
        this.progressThresholdSqr = progressThresholdSqr;
        this.spinProgressThresholdSqr = spinProgressThresholdSqr;
        this.spinYawDeltaThreshold = spinYawDeltaThreshold;
        this.spinTriggerTicks = Math.max(this.checkIntervalTicks, spinTriggerTicks);
    }

    SampleResult sample(Vec3 currentPos, float currentYaw, long gameTime) {
        if (gameTime - this.lastCheckTick < this.checkIntervalTicks) {
            return new SampleResult(false, false, this.stuckTicks, this.spinTicks);
        }
        this.lastCheckTick = gameTime;

        boolean spinTriggered = false;
        if (this.lastSpinSample == Vec3.ZERO) {
            this.lastSpinSample = currentPos;
            this.lastSpinYaw = currentYaw;
        } else {
            float yawDelta = Math.abs(Mth.wrapDegrees(currentYaw - this.lastSpinYaw));
            double moved = currentPos.distanceToSqr(this.lastSpinSample);
            if (moved < this.spinProgressThresholdSqr && yawDelta > this.spinYawDeltaThreshold) {
                this.spinTicks += this.checkIntervalTicks;
            } else {
                this.spinTicks = Math.max(0, this.spinTicks - this.checkIntervalTicks);
            }
            this.lastSpinSample = currentPos;
            this.lastSpinYaw = currentYaw;
            if (this.spinTicks >= this.spinTriggerTicks) {
                spinTriggered = true;
                this.spinTicks = 0;
            }
        }

        if (this.lastSample == Vec3.ZERO) {
            this.lastSample = currentPos;
        } else if (currentPos.distanceToSqr(this.lastSample) < this.progressThresholdSqr) {
            this.stuckTicks += this.checkIntervalTicks;
        } else {
            this.stuckTicks = Math.max(0, this.stuckTicks - this.checkIntervalTicks);
            this.lastSample = currentPos;
        }

        return new SampleResult(true, spinTriggered, this.stuckTicks, this.spinTicks);
    }

    int stuckTicks() {
        return this.stuckTicks;
    }

    void setStuckAtLeast(int ticks) {
        this.stuckTicks = Math.max(this.stuckTicks, ticks);
    }

    void reset(Vec3 pos, float yaw, long gameTime) {
        this.lastSample = pos;
        this.lastSpinSample = pos;
        this.lastSpinYaw = yaw;
        this.stuckTicks = 0;
        this.spinTicks = 0;
        this.lastCheckTick = gameTime;
    }

    void clear() {
        this.lastSample = Vec3.ZERO;
        this.lastSpinSample = Vec3.ZERO;
        this.lastSpinYaw = 0.0F;
        this.stuckTicks = 0;
        this.spinTicks = 0;
        this.lastCheckTick = 0L;
    }

    record SampleResult(boolean checked, boolean spinTriggered, int stuckTicks, int spinTicks) {
    }
}

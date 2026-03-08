package com.mcmod.aiplayers.entity;

final class PathCooldown {
    private final int pathRequestCooldownTicks;
    private final int severeReplanBackoffTicks;
    private long lastPathRequestTick;
    private long lastSevereReplanTick;

    PathCooldown(int pathRequestCooldownTicks, int severeReplanBackoffTicks) {
        this.pathRequestCooldownTicks = Math.max(1, pathRequestCooldownTicks);
        this.severeReplanBackoffTicks = Math.max(1, severeReplanBackoffTicks);
    }

    boolean inRequestCooldown(long gameTime) {
        return gameTime - this.lastPathRequestTick < this.pathRequestCooldownTicks;
    }

    boolean canReuseRecentPath(boolean sameTargetRegion, boolean hasPath, boolean severelyStuck, long gameTime) {
        return sameTargetRegion && hasPath && !severelyStuck && inRequestCooldown(gameTime);
    }

    boolean canAttemptSevereReplan(long gameTime) {
        return gameTime - this.lastSevereReplanTick >= this.severeReplanBackoffTicks;
    }

    void markPathRequest(long gameTime) {
        this.lastPathRequestTick = gameTime;
    }

    void markSevereReplan(long gameTime) {
        this.lastSevereReplanTick = gameTime;
        this.lastPathRequestTick = gameTime;
    }

    void clear() {
        this.lastPathRequestTick = 0L;
        this.lastSevereReplanTick = 0L;
    }
}

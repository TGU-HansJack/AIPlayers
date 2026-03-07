package com.mcmod.aiplayers.entity;

public final class AITickScheduler {
    private AITickScheduler() {
    }

    public static boolean shouldRunCombat(int tickCount) {
        return tickCount % AgentConfigManager.getConfig().combatIntervalTicks() == 0;
    }

    public static boolean shouldRunExecutor(int tickCount) {
        return tickCount % AgentConfigManager.getConfig().executorIntervalTicks() == 0;
    }

    public static boolean shouldRunPlanner(int tickCount) {
        return tickCount % AgentConfigManager.getConfig().plannerIntervalTicks() == 0;
    }

    public static boolean shouldRunWorldScan(int tickCount) {
        return tickCount % AgentConfigManager.getConfig().worldScanIntervalTicks() == 0;
    }

    public static boolean shouldSyncTelemetry(int tickCount) {
        return tickCount % AgentConfigManager.getConfig().telemetryIntervalTicks() == 0;
    }

    public static boolean shouldFlushPersistentState(int tickCount) {
        return tickCount % AgentConfigManager.getConfig().worldScanIntervalTicks() == 0;
    }
}

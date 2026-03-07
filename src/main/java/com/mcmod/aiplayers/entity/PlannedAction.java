package com.mcmod.aiplayers.entity;

import net.minecraft.core.BlockPos;

public record PlannedAction(GoapActionType type, String label, String targetKey, BlockPos fallbackTarget, double speed, int requiredCount) {
    public PlannedAction {
        label = label == null || label.isBlank() ? type.displayName() : label;
        targetKey = targetKey == null ? "" : targetKey;
        speed = speed <= 0.0D ? 1.0D : speed;
        requiredCount = Math.max(0, requiredCount);
    }

    public static PlannedAction simple(GoapActionType type, String label) {
        return new PlannedAction(type, label, "", null, 1.0D, 0);
    }

    public static PlannedAction move(String label, String targetKey, BlockPos fallbackTarget, double speed) {
        return new PlannedAction(GoapActionType.MOVE_TO_TARGET, label, targetKey, fallbackTarget, speed, 0);
    }
}

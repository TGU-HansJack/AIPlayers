package com.mcmod.aiplayers.entity;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;

public record PlannedAction(
        GoapActionType type,
        String label,
        String targetKey,
        BlockPos fallbackTarget,
        double speed,
        int requiredCount,
        int cost,
        List<GoapCondition> preconditions,
        List<GoapEffect> effects) {
    public PlannedAction {
        type = type == null ? GoapActionType.OBSERVE_AND_REPORT : type;
        label = label == null || label.isBlank() ? type.displayName() : label;
        targetKey = targetKey == null ? "" : targetKey;
        speed = speed <= 0.0D ? 1.0D : speed;
        requiredCount = Math.max(0, requiredCount);
        cost = Math.max(1, cost);
        preconditions = preconditions == null ? List.of() : List.copyOf(preconditions);
        effects = effects == null ? List.of() : List.copyOf(effects);
    }

    public PlannedAction(GoapActionType type, String label, String targetKey, BlockPos fallbackTarget, double speed, int requiredCount) {
        this(type, label, targetKey, fallbackTarget, speed, requiredCount, 1, List.of(), List.of());
    }

    public static PlannedAction simple(GoapActionType type, String label) {
        return new PlannedAction(type, label, "", null, 1.0D, 0, 1, List.of(), List.of());
    }

    public static PlannedAction move(String label, String targetKey, BlockPos fallbackTarget, double speed) {
        return new PlannedAction(GoapActionType.MOVE_TO_TARGET, label, targetKey, fallbackTarget, speed, 0, 1, List.of(), List.of());
    }

    public String preconditionSummary() {
        if (this.preconditions.isEmpty()) {
            return "\u65e0";
        }
        return this.preconditions.stream().map(GoapCondition::summary).collect(Collectors.joining("\u3001"));
    }

    public String effectSummary() {
        if (this.effects.isEmpty()) {
            return "\u65e0";
        }
        return this.effects.stream().map(GoapEffect::summary).collect(Collectors.joining("\u3001"));
    }

    public String detailSummary() {
        return "\u524d\u7f6e=" + this.preconditionSummary() + "\uff1b\u6548\u679c=" + this.effectSummary() + "\uff1b\u4ee3\u4ef7=" + this.cost;
    }
}

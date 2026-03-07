package com.mcmod.aiplayers.entity;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

public record GoapActionDefinition(
        GoapActionType type,
        String label,
        int cost,
        String targetKey,
        double speed,
        int requiredCount,
        List<GoapCondition> preconditions,
        List<GoapEffect> effects,
        Predicate<WorldStateSnapshot> availability) {
    public GoapActionDefinition {
        type = type == null ? GoapActionType.OBSERVE_AND_REPORT : type;
        label = label == null || label.isBlank() ? type.displayName() : label;
        cost = Math.max(1, cost);
        targetKey = targetKey == null ? "" : targetKey;
        speed = speed <= 0.0D ? 1.0D : speed;
        requiredCount = Math.max(0, requiredCount);
        preconditions = preconditions == null ? List.of() : List.copyOf(preconditions);
        effects = effects == null ? List.of() : List.copyOf(effects);
    }

    public boolean isAvailable(WorldStateSnapshot snapshot) {
        return this.availability == null || this.availability.test(snapshot);
    }

    public boolean isApplicable(GoapPlanningState state, WorldStateSnapshot snapshot) {
        return this.isAvailable(snapshot) && state != null && state.satisfies(this.preconditions);
    }

    public GoapPlanningState apply(GoapPlanningState input) {
        GoapPlanningState next = input.copy();
        for (GoapEffect effect : this.effects) {
            effect.applyTo(next);
        }
        return next;
    }

    public PlannedAction instantiate(WorldStateSnapshot snapshot) {
        return new PlannedAction(
                this.type,
                this.label,
                this.targetKey,
                resolveFallbackTarget(snapshot, this.targetKey),
                this.speed,
                this.requiredCount,
                this.cost,
                this.preconditions,
                this.effects);
    }

    private static BlockPos resolveFallbackTarget(WorldStateSnapshot snapshot, String targetKey) {
        if (snapshot == null || targetKey == null || targetKey.isBlank()) {
            return null;
        }
        return switch (targetKey) {
            case "owner" -> snapshot.ownerPos();
            case "wood" -> snapshot.woodPos();
            case "ore" -> snapshot.orePos();
            case "crop" -> snapshot.cropPos();
            case "bed" -> snapshot.bedPos();
            default -> null;
        };
    }
}

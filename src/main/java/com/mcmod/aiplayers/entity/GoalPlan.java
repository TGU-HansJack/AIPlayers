package com.mcmod.aiplayers.entity;

import java.util.List;
import java.util.stream.Collectors;

public record GoalPlan(AgentGoal goal, List<PlannedAction> actions, String reasoning, String source) {
    public GoalPlan {
        goal = goal == null ? AgentGoal.of(GoalType.IDLE, "local", "待命") : goal;
        actions = actions == null ? List.of() : List.copyOf(actions);
        reasoning = reasoning == null || reasoning.isBlank() ? goal.reasoning() : reasoning;
        source = source == null || source.isBlank() ? goal.source() : source;
    }

    public String summary() {
        if (this.actions.isEmpty()) {
            return this.goal.label();
        }
        return this.goal.label() + " -> " + this.actions.stream().map(PlannedAction::label).collect(Collectors.joining(" -> "));
    }

    public PlannedAction firstAction() {
        return this.actions.isEmpty() ? null : this.actions.getFirst();
    }
}

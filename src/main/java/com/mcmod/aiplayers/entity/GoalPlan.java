package com.mcmod.aiplayers.entity;

import java.util.List;
import java.util.stream.Collectors;

public record GoalPlan(AgentGoal goal, List<PlannedAction> actions, String reasoning, String source, List<GoapCondition> desiredConditions) {
    public GoalPlan {
        goal = goal == null ? AgentGoal.of(GoalType.IDLE, "local", "\u5f85\u547d") : goal;
        actions = actions == null ? List.of() : List.copyOf(actions);
        reasoning = reasoning == null || reasoning.isBlank() ? goal.reasoning() : reasoning;
        source = source == null || source.isBlank() ? goal.source() : source;
        desiredConditions = desiredConditions == null ? List.of() : List.copyOf(desiredConditions);
    }

    public GoalPlan(AgentGoal goal, List<PlannedAction> actions, String reasoning, String source) {
        this(goal, actions, reasoning, source, List.of());
    }

    public String summary() {
        if (this.actions.isEmpty()) {
            return this.goal.label();
        }
        return this.goal.label() + " -> " + this.actions.stream().map(PlannedAction::label).collect(Collectors.joining(" -> "));
    }

    public String goalConditionSummary() {
        if (this.desiredConditions.isEmpty()) {
            return "\u65e0\u663e\u5f0f\u76ee\u6807\u6761\u4ef6";
        }
        return this.desiredConditions.stream().map(GoapCondition::summary).collect(Collectors.joining("\u3001"));
    }

    public PlannedAction firstAction() {
        return this.actions.isEmpty() ? null : this.actions.getFirst();
    }

    public PlannedAction findAction(String label) {
        if (label == null || label.isBlank()) {
            return this.firstAction();
        }
        return this.actions.stream()
                .filter(action -> label.equals(action.label()))
                .findFirst()
                .orElse(this.firstAction());
    }
}

package com.mcmod.aiplayers.entity;

import java.util.List;

public record InteractionPlan(String label, List<InteractionAction> actions, ActionExecutionResult emptyResult, String emptyReason) {
    public InteractionPlan {
        label = label == null || label.isBlank() ? "交互计划" : label;
        actions = actions == null ? List.of() : List.copyOf(actions);
        emptyResult = emptyResult == null ? ActionExecutionResult.FAILED : emptyResult;
        emptyReason = emptyReason == null ? "" : emptyReason;
    }

    static InteractionPlan success(String label, String reason) {
        return new InteractionPlan(label, List.of(), ActionExecutionResult.SUCCESS, reason);
    }

    static InteractionPlan failed(String label, String reason) {
        return new InteractionPlan(label, List.of(), ActionExecutionResult.FAILED, reason);
    }

    static InteractionPlan of(String label, List<InteractionAction> actions) {
        return new InteractionPlan(label, actions, ActionExecutionResult.RUNNING, "");
    }
}

package com.mcmod.aiplayers.entity;

public record AgentBrainResponse(
        TaskRequest task,
        int priority,
        ConstraintSet constraints,
        BehaviorNodeSpec subtree,
        TaskRequest fallbackTask,
        String reasoning,
        String source) {
    public AgentBrainResponse {
        task = task == null ? TaskRequest.idle("brain") : task;
        priority = Math.max(0, priority);
        constraints = constraints == null ? ConstraintSet.empty() : constraints;
        fallbackTask = fallbackTask == null ? TaskRequest.idle("brain-fallback") : fallbackTask;
        reasoning = reasoning == null || reasoning.isBlank() ? task.label() : reasoning;
        source = source == null || source.isBlank() ? "brain-local" : source;
    }

    public boolean hasUsablePlan() {
        return this.subtree != null || this.task != null;
    }
}

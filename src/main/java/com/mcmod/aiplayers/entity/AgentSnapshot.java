package com.mcmod.aiplayers.entity;

public record AgentSnapshot(
        WorldStateSnapshot worldState,
        AgentGoal currentGoal,
        GoalPlan currentPlan,
        String currentAction,
        String pathStatus,
        String plannerStatus,
        String llmStatus,
        AgentMemory memory) {
}

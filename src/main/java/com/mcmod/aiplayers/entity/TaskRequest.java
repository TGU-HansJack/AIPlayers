package com.mcmod.aiplayers.entity;

import java.util.LinkedHashMap;
import java.util.Map;

public record TaskRequest(
        String taskType,
        String label,
        Map<String, String> args,
        int priority,
        boolean pinned,
        String source,
        String fallbackTask) {
    public TaskRequest {
        taskType = taskType == null || taskType.isBlank() ? GoalType.IDLE.commandName() : taskType.trim();
        label = label == null || label.isBlank() ? taskType : label;
        args = args == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(args));
        priority = Math.max(0, priority);
        source = source == null || source.isBlank() ? "local" : source;
        fallbackTask = fallbackTask == null || fallbackTask.isBlank() ? GoalType.SURVIVE.commandName() : fallbackTask.trim();
    }

    public static TaskRequest idle(String source) {
        return new TaskRequest(GoalType.IDLE.commandName(), GoalType.IDLE.displayName(), Map.of(), 0, false, source, GoalType.SURVIVE.commandName());
    }

    public static TaskRequest fromGoal(AgentGoal goal, boolean pinned) {
        if (goal == null) {
            return idle("compat");
        }
        return new TaskRequest(
                goal.type().commandName(),
                goal.reasoning(),
                goal.args(),
                goal.priority(),
                pinned,
                goal.source(),
                goal.fallbackType().commandName());
    }

    public GoalType goalType() {
        GoalType resolved = GoalType.fromLegacyText(this.taskType);
        return resolved == null ? GoalType.IDLE : resolved;
    }

    public AgentGoal toAgentGoal() {
        return AgentGoal.of(this.goalType(), this.args, this.priority, this.source, this.label, GoalType.fromLegacyText(this.fallbackTask));
    }
}

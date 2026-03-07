package com.mcmod.aiplayers.ai;

import com.mcmod.aiplayers.entity.GoalType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AIGoalPlanResponse(String goalType, Map<String, String> goalArgs, int priority, List<String> constraints, String fallbackGoal, String speechReply, String source) {
    public AIGoalPlanResponse {
        goalType = goalType == null ? "" : goalType;
        goalArgs = goalArgs == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(goalArgs));
        priority = Math.max(0, priority);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        fallbackGoal = fallbackGoal == null ? "" : fallbackGoal;
        speechReply = speechReply == null ? "" : speechReply;
        source = source == null || source.isBlank() ? "goal-api" : source;
    }

    public boolean hasPlan() {
        return !this.goalType.isBlank() || !this.goalArgs.isEmpty() || !this.fallbackGoal.isBlank();
    }

    public GoalType resolveGoalType(GoalType fallback) {
        GoalType parsed = GoalType.fromLegacyText(this.goalType);
        return parsed == null ? fallback : parsed;
    }

    public GoalType resolveFallbackGoal(GoalType fallback) {
        GoalType parsed = GoalType.fromLegacyText(this.fallbackGoal);
        return parsed == null ? fallback : parsed;
    }
}

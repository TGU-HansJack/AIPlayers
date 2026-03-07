package com.mcmod.aiplayers.entity;

import java.util.LinkedHashMap;
import java.util.Map;

public record AgentGoal(GoalType type, Map<String, String> args, int priority, String source, String reasoning, GoalType fallbackType) {
    public AgentGoal {
        type = type == null ? GoalType.IDLE : type;
        args = args == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(args));
        priority = Math.max(0, priority);
        source = source == null || source.isBlank() ? "local" : source;
        reasoning = reasoning == null || reasoning.isBlank() ? type.displayName() : reasoning;
        fallbackType = fallbackType == null ? GoalType.SURVIVE : fallbackType;
    }

    public static AgentGoal of(GoalType type, String source, String reasoning) {
        return new AgentGoal(type, Map.of(), 50, source, reasoning, GoalType.SURVIVE);
    }

    public static AgentGoal of(GoalType type, Map<String, String> args, int priority, String source, String reasoning, GoalType fallbackType) {
        return new AgentGoal(type, args, priority, source, reasoning, fallbackType);
    }

    public String label() { return this.type.displayName(); }
    public String arg(String key) { return this.args.getOrDefault(key, ""); }
}

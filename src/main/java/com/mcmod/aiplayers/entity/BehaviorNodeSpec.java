package com.mcmod.aiplayers.entity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BehaviorNodeSpec(
        String id,
        String type,
        String label,
        String condition,
        ActionTask action,
        List<BehaviorNodeSpec> children,
        Map<String, String> args,
        int timeoutTicks) {
    public BehaviorNodeSpec {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        type = type == null || type.isBlank() ? "action" : type.trim().toLowerCase();
        label = label == null || label.isBlank() ? type : label;
        condition = condition == null ? "" : condition.trim().toLowerCase();
        children = children == null ? List.of() : List.copyOf(children);
        args = args == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(args));
        timeoutTicks = Math.max(0, timeoutTicks);
    }

    public static BehaviorNodeSpec action(String label, String actionType, Map<String, String> args) {
        return new BehaviorNodeSpec(null, "action", label, "", new ActionTask(actionType, label, args), List.of(), Map.of(), 0);
    }

    public static BehaviorNodeSpec sequence(String label, List<BehaviorNodeSpec> children) {
        return new BehaviorNodeSpec(null, "sequence", label, "", null, children, Map.of(), 0);
    }

    public static BehaviorNodeSpec selector(String label, List<BehaviorNodeSpec> children) {
        return new BehaviorNodeSpec(null, "selector", label, "", null, children, Map.of(), 0);
    }

    public static BehaviorNodeSpec condition(String label, String condition) {
        return new BehaviorNodeSpec(null, "condition", label, condition, null, List.of(), Map.of(), 0);
    }
}

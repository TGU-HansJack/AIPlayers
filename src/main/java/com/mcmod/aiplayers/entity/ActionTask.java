package com.mcmod.aiplayers.entity;

import java.util.LinkedHashMap;
import java.util.Map;

public record ActionTask(String actionType, String label, Map<String, String> args) {
    public ActionTask {
        actionType = actionType == null || actionType.isBlank() ? "observe" : actionType.trim().toLowerCase();
        label = label == null || label.isBlank() ? actionType : label;
        args = args == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(args));
    }

    public String key() {
        return this.actionType + "|" + this.label + "|" + this.args.toString();
    }
}

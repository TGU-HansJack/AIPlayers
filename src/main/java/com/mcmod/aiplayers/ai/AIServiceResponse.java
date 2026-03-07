package com.mcmod.aiplayers.ai;

import java.util.LinkedHashMap;
import java.util.Map;

public record AIServiceResponse(String reply, String mode, String goalType, Map<String, String> goalArgs, String action, String source) {
    public AIServiceResponse {
        reply = reply == null ? "" : reply;
        mode = mode == null ? "unchanged" : mode;
        goalType = goalType == null ? "" : goalType;
        goalArgs = goalArgs == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(goalArgs));
        action = action == null ? "none" : action;
        source = source == null || source.isBlank() ? "api" : source;
    }

    public boolean hasDirective() {
        return (!this.mode.isBlank() && !"unchanged".equalsIgnoreCase(this.mode))
                || !this.goalType.isBlank()
                || (!this.action.isBlank() && !"none".equalsIgnoreCase(this.action));
    }
}

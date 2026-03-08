package com.mcmod.aiplayers.ai;

import java.util.LinkedHashMap;
import java.util.Map;

public record AIServiceResponse(
        String reply,
        String mode,
        String goalType,
        Map<String, String> goalArgs,
        String action,
        String source,
        String controller,
        String controllerAction,
        Map<String, String> controllerArgs) {
    public AIServiceResponse(String reply, String mode, String goalType, Map<String, String> goalArgs, String action, String source) {
        this(reply, mode, goalType, goalArgs, action, source, "", "", Map.of());
    }

    public AIServiceResponse {
        reply = reply == null ? "" : reply;
        mode = mode == null ? "unchanged" : mode;
        goalType = goalType == null ? "" : goalType;
        goalArgs = goalArgs == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(goalArgs));
        action = action == null ? "none" : action;
        source = source == null || source.isBlank() ? "api" : source;
        controller = controller == null ? "" : controller;
        controllerAction = controllerAction == null ? "" : controllerAction;
        controllerArgs = controllerArgs == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(controllerArgs));
    }

    public boolean hasDirective() {
        return (!this.mode.isBlank() && !"unchanged".equalsIgnoreCase(this.mode))
                || !this.goalType.isBlank()
                || (!this.action.isBlank() && !"none".equalsIgnoreCase(this.action))
                || this.hasControllerDirective();
    }

    public boolean hasControllerDirective() {
        return !this.controller.isBlank() || !this.controllerAction.isBlank();
    }
}

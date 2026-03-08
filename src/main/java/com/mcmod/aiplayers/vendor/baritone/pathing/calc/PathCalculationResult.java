package com.mcmod.aiplayers.vendor.baritone.pathing.calc;

import com.mcmod.aiplayers.vendor.baritone.pathing.path.Path;

public record PathCalculationResult(Type type, Path path) {
    public enum Type {
        SUCCESS_TO_GOAL,
        SUCCESS_SEGMENT,
        FAILURE,
        CANCELLED
    }
}

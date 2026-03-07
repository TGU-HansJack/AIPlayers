package com.mcmod.aiplayers.ai;

import com.mcmod.aiplayers.entity.AIPlayerMode;
import java.util.List;

public record AITaskPlanResponse(String goal, String mode, List<String> subtasks, String fallback, String source) {
    public AIPlayerMode resolveMode(AIPlayerMode fallbackMode) {
        if (this.mode == null || this.mode.isBlank() || "unchanged".equalsIgnoreCase(this.mode)) {
            return fallbackMode;
        }
        AIPlayerMode parsed = AIPlayerMode.fromCommand(this.mode.trim());
        return parsed == null ? fallbackMode : parsed;
    }

    public boolean hasPlan() {
        return (this.goal != null && !this.goal.isBlank())
                || (this.subtasks != null && !this.subtasks.isEmpty())
                || (this.mode != null && !this.mode.isBlank());
    }
}

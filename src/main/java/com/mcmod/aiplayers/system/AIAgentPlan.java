package com.mcmod.aiplayers.system;

import com.mcmod.aiplayers.entity.AIPlayerMode;
import java.util.List;

public record AIAgentPlan(
        String goal,
        AIPlayerMode recommendedMode,
        List<String> steps,
        String reasoning,
        String source) {
    public String summary() {
        String stepText = this.steps == null || this.steps.isEmpty() ? "保持当前动作" : String.join(" -> ", this.steps);
        String modeText = this.recommendedMode == null ? "保持当前模式" : this.recommendedMode.displayName();
        return "目标=" + this.goal + "；模式=" + modeText + "；步骤=" + stepText + "；推理=" + this.reasoning + "；来源=" + this.source;
    }
}

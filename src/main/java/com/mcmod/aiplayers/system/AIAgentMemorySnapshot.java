package com.mcmod.aiplayers.system;

import java.util.List;

public record AIAgentMemorySnapshot(
        String shortTermSummary,
        String longTermSummary,
        List<String> recentEvents) {
}

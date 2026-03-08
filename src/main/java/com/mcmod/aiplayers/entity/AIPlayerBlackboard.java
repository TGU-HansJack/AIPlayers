package com.mcmod.aiplayers.entity;

import java.util.LinkedHashMap;
import java.util.Map;

final class AIPlayerBlackboard {
    private TaskRequest pinnedTask;
    private final Map<String, Integer> nodeIndices = new LinkedHashMap<>();
    private final Map<String, Integer> nodeStartTicks = new LinkedHashMap<>();
    private final Map<String, String> values = new LinkedHashMap<>();

    TaskRequest pinnedTask() {
        return this.pinnedTask;
    }

    void pinTask(TaskRequest task) {
        this.pinnedTask = task;
        this.resetRuntimeState();
    }

    void clearPinnedTask() {
        this.pinnedTask = null;
        this.resetRuntimeState();
    }

    int indexOf(String nodeId) {
        return this.nodeIndices.getOrDefault(nodeId, 0);
    }

    void setIndex(String nodeId, int value) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        this.nodeIndices.put(nodeId, Math.max(0, value));
    }

    int startTickOf(String nodeId) {
        return this.nodeStartTicks.getOrDefault(nodeId, -1);
    }

    void setStartTick(String nodeId, int tick) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        this.nodeStartTicks.put(nodeId, tick);
    }

    void clearNodeState(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        this.nodeIndices.remove(nodeId);
        this.nodeStartTicks.remove(nodeId);
    }

    void putValue(String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (value == null) {
            this.values.remove(key);
            return;
        }
        this.values.put(key, value);
    }

    String value(String key) {
        return this.values.getOrDefault(key, "");
    }

    void resetRuntimeState() {
        this.nodeIndices.clear();
        this.nodeStartTicks.clear();
        this.values.clear();
    }
}

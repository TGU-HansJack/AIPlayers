package com.mcmod.aiplayers.entity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public final class AgentMemory {
    private final Deque<String> recentEvents = new ArrayDeque<>();
    private String lastFailure = "暂无失败";
    private String lastLearning = "暂无学习";
    private String lastAction = "待命";

    public void noteEvent(String event) {
        if (event == null || event.isBlank()) {
            return;
        }
        if (!this.recentEvents.isEmpty() && event.equals(this.recentEvents.peekLast())) {
            return;
        }
        if (this.recentEvents.size() >= 8) {
            this.recentEvents.removeFirst();
        }
        this.recentEvents.addLast(event);
    }

    public void noteFailure(String detail) {
        this.lastFailure = detail == null || detail.isBlank() ? this.lastFailure : detail;
        this.noteEvent("失败：" + this.lastFailure);
    }

    public void noteLearning(String detail) {
        this.lastLearning = detail == null || detail.isBlank() ? this.lastLearning : detail;
        this.noteEvent("学习：" + this.lastLearning);
    }

    public void noteAction(String action) {
        this.lastAction = action == null || action.isBlank() ? this.lastAction : action;
    }

    public String lastFailure() { return this.lastFailure; }
    public String lastLearning() { return this.lastLearning; }
    public String lastAction() { return this.lastAction; }

    public List<String> recentEvents() {
        return List.copyOf(this.recentEvents);
    }

    public String recentSummary() {
        if (this.recentEvents.isEmpty()) {
            return "暂无";
        }
        return this.recentEvents.stream().collect(Collectors.joining(" | "));
    }
}

package com.mcmod.aiplayers.entity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public final class AgentMemory {
    private final Deque<String> recentEvents = new ArrayDeque<>();
    private String lastFailure = "\u6682\u65e0\u5931\u8d25";
    private String lastLearning = "\u6682\u65e0\u5b66\u4e60";
    private String lastAction = "\u5f85\u547d";

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
        this.noteEvent("\u5931\u8d25\uff1a" + this.lastFailure);
    }

    public void noteLearning(String detail) {
        this.lastLearning = detail == null || detail.isBlank() ? this.lastLearning : detail;
        this.noteEvent("\u5b66\u4e60\uff1a" + this.lastLearning);
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
            return "\u6682\u65e0";
        }
        return this.recentEvents.stream().collect(Collectors.joining(" | "));
    }
}

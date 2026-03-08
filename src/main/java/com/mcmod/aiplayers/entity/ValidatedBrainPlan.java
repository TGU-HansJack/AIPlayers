package com.mcmod.aiplayers.entity;

public record ValidatedBrainPlan(
        boolean accepted,
        AgentBrainResponse plan,
        String note) {
    public static ValidatedBrainPlan accepted(AgentBrainResponse plan, String note) {
        return new ValidatedBrainPlan(true, plan, note == null ? "accepted" : note);
    }

    public static ValidatedBrainPlan rejected(String note) {
        return new ValidatedBrainPlan(false, null, note == null ? "rejected" : note);
    }
}

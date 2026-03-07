package com.mcmod.aiplayers.entity;

public record GoapCondition(GoapFact fact, boolean expected, String description) {
    public GoapCondition {
        fact = fact == null ? GoapFact.OBSERVATION_REFRESHED : fact;
        description = description == null || description.isBlank()
                ? fact.displayName() + (expected ? "=\u9700\u8981" : "=\u4e0d\u9700\u8981")
                : description;
    }

    public boolean matches(GoapPlanningState state) {
        return state != null && state.get(this.fact) == this.expected;
    }

    public String summary() {
        return this.description;
    }

    public static GoapCondition require(GoapFact fact, boolean expected, String description) {
        return new GoapCondition(fact, expected, description);
    }
}

package com.mcmod.aiplayers.entity;

public record GoapEffect(GoapFact fact, boolean value, String description) {
    public GoapEffect {
        fact = fact == null ? GoapFact.OBSERVATION_REFRESHED : fact;
        description = description == null || description.isBlank()
                ? fact.displayName() + "->" + (value ? "已达成" : "已取消")
                : description;
    }

    public void applyTo(GoapPlanningState state) {
        if (state != null) {
            state.set(this.fact, this.value);
        }
    }

    public String summary() {
        return this.description;
    }

    public static GoapEffect set(GoapFact fact, boolean value, String description) {
        return new GoapEffect(fact, value, description);
    }
}

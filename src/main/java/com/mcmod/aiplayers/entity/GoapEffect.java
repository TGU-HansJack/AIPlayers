package com.mcmod.aiplayers.entity;

public record GoapEffect(GoapFact fact, boolean value, String description) {
    public GoapEffect {
        fact = fact == null ? GoapFact.OBSERVATION_REFRESHED : fact;
        description = description == null || description.isBlank()
                ? fact.displayName() + "->" + (value ? "\u5df2\u8fbe\u6210" : "\u5df2\u53d6\u6d88")
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

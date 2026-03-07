package com.mcmod.aiplayers.entity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class GoapPlanningState {
    private final EnumMap<GoapFact, Boolean> facts;

    public GoapPlanningState() {
        this.facts = new EnumMap<>(GoapFact.class);
        for (GoapFact fact : GoapFact.values()) {
            this.facts.put(fact, Boolean.FALSE);
        }
    }

    private GoapPlanningState(EnumMap<GoapFact, Boolean> facts) {
        this.facts = new EnumMap<>(facts);
    }

    public static GoapPlanningState fromWorldState(WorldStateSnapshot state) {
        GoapPlanningState planningState = new GoapPlanningState();
        if (state == null) {
            return planningState;
        }
        planningState.set(GoapFact.OWNER_AVAILABLE, state.ownerAvailable());
        planningState.set(GoapFact.OWNER_NEAR, state.ownerAvailable() && state.ownerDistance() <= 5.0D);
        planningState.set(GoapFact.OWNER_SAFE, !state.ownerUnderThreat());
        planningState.set(GoapFact.SAFE, !state.inHazard() && !state.onFire() && !state.navigationStuck());
        planningState.set(GoapFact.HEALTHY, !state.lowHealth());
        planningState.set(GoapFact.HAS_TOOLS, !state.lowTools());
        planningState.set(GoapFact.HAS_WOOD_TARGET, state.woodKnown());
        planningState.set(GoapFact.HAS_ORE_TARGET, state.oreKnown());
        planningState.set(GoapFact.HAS_CROP_TARGET, state.cropKnown());
        planningState.set(GoapFact.HAS_BED_TARGET, state.bedKnown());
        planningState.set(GoapFact.HAS_BUILDING_MATERIALS, state.buildingUnits() >= 12);
        planningState.set(GoapFact.HAS_LOGS, state.logCount() > 0);
        planningState.set(GoapFact.HAS_PLANKS, state.plankCount() >= 4);
        planningState.set(GoapFact.HAS_FOOD_SUPPLY, !state.lowFood() || state.breadCount() >= 2);
        planningState.set(GoapFact.HAS_BACKPACK_SPACE, state.freeBackpackSlots() > 0);
        planningState.set(GoapFact.HAS_ORE_SUPPLY, state.oreCount() > 0);
        planningState.set(GoapFact.DELIVERY_PENDING, state.pendingDelivery());
        planningState.set(GoapFact.DELIVERY_COMPLETE, !state.pendingDelivery());
        planningState.set(GoapFact.SHELTER_READY, state.shelterReady());
        planningState.set(GoapFact.OBSERVATION_REFRESHED, false);
        planningState.set(GoapFact.HAS_TORCH_SUPPLY, state.torchCount() >= 4);
        return planningState;
    }

    public boolean get(GoapFact fact) {
        return this.facts.getOrDefault(fact, Boolean.FALSE);
    }

    public void set(GoapFact fact, boolean value) {
        this.facts.put(fact, value);
    }

    public boolean satisfies(List<GoapCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        return conditions.stream().allMatch(condition -> condition != null && condition.matches(this));
    }

    public GoapPlanningState copy() {
        return new GoapPlanningState(this.facts);
    }

    public String describeUnsatisfied(List<GoapCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return "无";
        }
        return conditions.stream()
                .filter(condition -> condition != null && !condition.matches(this))
                .map(GoapCondition::summary)
                .collect(Collectors.joining("、"));
    }

    public String key() {
        return this.facts.entrySet().stream()
                .map(entry -> entry.getKey().name() + '=' + (entry.getValue() ? '1' : '0'))
                .collect(Collectors.joining("|"));
    }

    public Map<GoapFact, Boolean> asMap() {
        return Map.copyOf(this.facts);
    }
}

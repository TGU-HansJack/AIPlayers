package com.mcmod.aiplayers.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Predicate;

public final class GOAPPlanner {
    private static final int DEFAULT_WOOD_TARGET = 12;
    private static final int SEARCH_DEPTH_LIMIT = 8;

    private GOAPPlanner() {
    }

    public static GoalPlan plan(AgentGoal goal, WorldStateSnapshot state) {
        List<GoapCondition> desiredConditions = buildDesiredConditions(goal, state);
        if (desiredConditions.isEmpty()) {
            return new GoalPlan(goal, List.of(), goal.reasoning(), goal.source(), desiredConditions);
        }

        GoapPlanningState initial = GoapPlanningState.fromWorldState(state);
        SearchNode solved = solve(initial, desiredConditions, buildActionCatalog(state), state);
        if (solved != null) {
            String reasoning = goal.reasoning() + "\uff1b\u76ee\u6807\u6761\u4ef6=" + new GoalPlan(goal, List.of(), goal.reasoning(), goal.source(), desiredConditions).goalConditionSummary();
            return new GoalPlan(goal, solved.actions, reasoning, goal.source(), desiredConditions);
        }

        return buildFallbackPlan(goal, state, desiredConditions);
    }

    private static SearchNode solve(GoapPlanningState initial, List<GoapCondition> desiredConditions, List<GoapActionDefinition> catalog, WorldStateSnapshot snapshot) {
        PriorityQueue<SearchNode> frontier = new PriorityQueue<>(Comparator.comparingInt(node -> node.score(desiredConditions)));
        Map<String, Integer> bestCosts = new HashMap<>();
        frontier.add(new SearchNode(initial, List.of(), 0));
        bestCosts.put(initial.key(), 0);

        while (!frontier.isEmpty()) {
            SearchNode node = frontier.poll();
            if (node.state.satisfies(desiredConditions)) {
                return node;
            }
            if (node.actions.size() >= SEARCH_DEPTH_LIMIT) {
                continue;
            }
            for (GoapActionDefinition action : catalog) {
                if (!action.isApplicable(node.state, snapshot)) {
                    continue;
                }
                GoapPlanningState nextState = action.apply(node.state);
                int nextCost = node.cost + action.cost();
                String key = nextState.key();
                if (bestCosts.containsKey(key) && bestCosts.get(key) <= nextCost) {
                    continue;
                }
                bestCosts.put(key, nextCost);
                List<PlannedAction> nextActions = new ArrayList<>(node.actions);
                nextActions.add(action.instantiate(snapshot));
                frontier.add(new SearchNode(nextState, List.copyOf(nextActions), nextCost));
            }
        }
        return null;
    }

    private static List<GoapCondition> buildDesiredConditions(AgentGoal goal, WorldStateSnapshot state) {
        List<GoapCondition> desired = new ArrayList<>();
        switch (goal.type()) {
            case FOLLOW_OWNER -> {
                desired.add(require(GoapFact.OWNER_AVAILABLE, true));
                desired.add(require(GoapFact.OWNER_NEAR, true));
            }
            case GUARD_OWNER -> {
                desired.add(require(GoapFact.OWNER_AVAILABLE, true));
                desired.add(require(GoapFact.OWNER_NEAR, true));
                desired.add(require(GoapFact.OBSERVATION_REFRESHED, true));
            }
            case SURVIVE -> {
                desired.add(require(GoapFact.SAFE, true));
                if (state.lowHealth()) {
                    desired.add(require(GoapFact.HEALTHY, true));
                }
                if (state.lowFood()) {
                    desired.add(require(GoapFact.HAS_FOOD_SUPPLY, true));
                }
                if (state.lowTools()) {
                    desired.add(require(GoapFact.HAS_TOOLS, true));
                }
                if (state.night()) {
                    desired.add(require(GoapFact.SHELTER_READY, true));
                }
            }
            case COLLECT_WOOD -> desired.add(require(GoapFact.HAS_BUILDING_MATERIALS, true));
            case COLLECT_ORE -> desired.add(require(GoapFact.HAS_ORE_SUPPLY, true));
            case COLLECT_FOOD -> desired.add(require(GoapFact.HAS_FOOD_SUPPLY, true));
            case BUILD_SHELTER -> desired.add(require(GoapFact.SHELTER_READY, true));
            case DELIVER_ITEM -> desired.add(require(GoapFact.DELIVERY_COMPLETE, true));
            case EXPLORE_AREA, TALK_ONLY -> desired.add(require(GoapFact.OBSERVATION_REFRESHED, true));
            case RECOVER_SELF -> {
                desired.add(require(GoapFact.SAFE, true));
                if (state.lowHealth()) {
                    desired.add(require(GoapFact.HEALTHY, true));
                }
            }
            case IDLE -> desired.add(require(GoapFact.OBSERVATION_REFRESHED, true));
        }
        return desired;
    }

    private static List<GoapActionDefinition> buildActionCatalog(WorldStateSnapshot state) {
        List<GoapActionDefinition> actions = new ArrayList<>();
        actions.add(action(GoapActionType.MOVE_TO_TARGET, "\u9760\u8fd1\u4e3b\u4eba", 1, "owner", 1.18D, 0,
                List.of(require(GoapFact.OWNER_AVAILABLE, true)),
                List.of(effect(GoapFact.OWNER_NEAR, true), effect(GoapFact.OWNER_SAFE, true)),
                snapshot -> snapshot != null && snapshot.ownerAvailable()));
        actions.add(action(GoapActionType.OBSERVE_AND_REPORT, "\u89c2\u5bdf\u5e76\u6c47\u62a5", 1, "", 1.0D, 0,
                List.of(),
                List.of(effect(GoapFact.OBSERVATION_REFRESHED, true)),
                snapshot -> true));
        actions.add(action(GoapActionType.RETREAT_TO_SAFE_GROUND, "\u64a4\u79bb\u5230\u5b89\u5168\u5730\u5e26", 1, "", 1.08D, 0,
                List.of(),
                List.of(effect(GoapFact.SAFE, true), effect(GoapFact.OWNER_SAFE, true)),
                snapshot -> snapshot != null && (snapshot.inHazard() || snapshot.onFire() || snapshot.navigationStuck())));
        actions.add(action(GoapActionType.REST_AT_BED, "\u524d\u5f80\u5e8a\u4f4d\u4f11\u6574", 2, "bed", 1.0D, 0,
                List.of(require(GoapFact.HAS_BED_TARGET, true)),
                List.of(effect(GoapFact.HEALTHY, true), effect(GoapFact.SAFE, true)),
                snapshot -> snapshot != null && snapshot.bedKnown()));
        actions.add(action(GoapActionType.ACQUIRE_TOOL, "\u8865\u9f50\u57fa\u7840\u5de5\u5177", 2, "", 1.0D, 0,
                List.of(),
                List.of(effect(GoapFact.HAS_TOOLS, true)),
                snapshot -> snapshot != null && snapshot.lowTools()));
        actions.add(action(GoapActionType.CHOP_TREE, "\u780d\u6811\u5e76\u56de\u6536\u539f\u6728", 2, "wood", 1.08D, DEFAULT_WOOD_TARGET,
                List.of(require(GoapFact.HAS_WOOD_TARGET, true)),
                List.of(effect(GoapFact.HAS_LOGS, true), effect(GoapFact.HAS_PLANKS, true), effect(GoapFact.HAS_BUILDING_MATERIALS, true)),
                snapshot -> snapshot != null && snapshot.woodKnown()));
        actions.add(action(GoapActionType.CRAFT_PLANKS, "\u628a\u539f\u6728\u8f6c\u6210\u6728\u677f", 1, "", 1.0D, 4,
                List.of(require(GoapFact.HAS_LOGS, true)),
                List.of(effect(GoapFact.HAS_PLANKS, true), effect(GoapFact.HAS_BUILDING_MATERIALS, true)),
                snapshot -> snapshot != null && snapshot.logCount() > 0));
        actions.add(action(GoapActionType.CRAFT_TORCH, "\u5236\u4f5c\u706b\u628a", 1, "", 1.0D, 4,
                List.of(require(GoapFact.HAS_PLANKS, true)),
                List.of(effect(GoapFact.HAS_TORCH_SUPPLY, true)),
                snapshot -> snapshot != null && snapshot.torchCount() < 4));
        actions.add(action(GoapActionType.MINE_ORE, "\u524d\u5f80\u77ff\u70b9\u5e76\u91c7\u6398", 3, "ore", 1.05D, 1,
                List.of(require(GoapFact.HAS_ORE_TARGET, true), require(GoapFact.HAS_TOOLS, true), require(GoapFact.HAS_BACKPACK_SPACE, true)),
                List.of(effect(GoapFact.HAS_ORE_SUPPLY, true)),
                snapshot -> snapshot != null && snapshot.oreKnown()));
        actions.add(action(GoapActionType.HARVEST_CROP, "\u6536\u5272\u6210\u719f\u4f5c\u7269", 2, "crop", 1.0D, 1,
                List.of(require(GoapFact.HAS_CROP_TARGET, true)),
                List.of(effect(GoapFact.HAS_FOOD_SUPPLY, true)),
                snapshot -> snapshot != null && snapshot.cropKnown()));
        actions.add(action(GoapActionType.CRAFT_BREAD, "\u5408\u6210\u53ef\u6062\u590d\u98df\u7269", 3, "", 1.0D, 2,
                List.of(require(GoapFact.HAS_CROP_TARGET, true)),
                List.of(effect(GoapFact.HAS_FOOD_SUPPLY, true)),
                snapshot -> snapshot != null && (snapshot.cropKnown() || snapshot.breadCount() > 0)));
        actions.add(action(GoapActionType.BUILD_SHELTER, "\u6309\u84dd\u56fe\u642d\u5efa\u907f\u96be\u6240", 3, "", 1.0D, 0,
                List.of(require(GoapFact.HAS_BUILDING_MATERIALS, true)),
                List.of(effect(GoapFact.SHELTER_READY, true), effect(GoapFact.SAFE, true)),
                snapshot -> true));
        actions.add(action(GoapActionType.DELIVER_ITEM, "\u9760\u8fd1\u4e3b\u4eba\u5e76\u4ea4\u4ed8\u7269\u8d44", 2, "delivery_receiver", 1.2D, 0,
                List.of(require(GoapFact.DELIVERY_PENDING, true), require(GoapFact.OWNER_AVAILABLE, true)),
                List.of(effect(GoapFact.DELIVERY_COMPLETE, true), effect(GoapFact.OWNER_NEAR, true)),
                snapshot -> snapshot != null && snapshot.pendingDelivery()));
        actions.add(action(GoapActionType.COLLECT_DROP, "\u56de\u6536\u9644\u8fd1\u6389\u843d\u7269", 1, "", 1.0D, 0,
                List.of(require(GoapFact.HAS_BACKPACK_SPACE, true)),
                List.of(effect(GoapFact.OBSERVATION_REFRESHED, true)),
                snapshot -> snapshot != null && snapshot.dropNearby()));
        actions.add(action(GoapActionType.MOVE_TO_TARGET, "\u524d\u5f80\u63a2\u7d22\u533a\u57df", 1, "explore", 1.0D, 0,
                List.of(),
                List.of(effect(GoapFact.OBSERVATION_REFRESHED, true)),
                snapshot -> true));
        return actions;
    }

    private static GoalPlan buildFallbackPlan(AgentGoal goal, WorldStateSnapshot state, List<GoapCondition> desiredConditions) {
        List<PlannedAction> actions = new ArrayList<>();
        switch (goal.type()) {
            case FOLLOW_OWNER -> actions.add(PlannedAction.move("\u9760\u8fd1\u4e3b\u4eba", "owner", state.ownerPos(), 1.18D));
            case GUARD_OWNER -> {
                actions.add(PlannedAction.move("\u9760\u8fd1\u4e3b\u4eba", "owner", state.ownerPos(), 1.18D));
                actions.add(PlannedAction.simple(GoapActionType.OBSERVE_AND_REPORT, "\u89c2\u5bdf\u4e3b\u4eba\u9644\u8fd1\u5a01\u80c1"));
            }
            case RECOVER_SELF -> {
                actions.add(PlannedAction.simple(GoapActionType.RETREAT_TO_SAFE_GROUND, "\u5c1d\u8bd5\u8131\u79bb\u5371\u9669\u5730\u5f62"));
                if (state.bedKnown()) {
                    actions.add(new PlannedAction(GoapActionType.REST_AT_BED, "\u524d\u5f80\u5e8a\u4f4d\u4f11\u6574", "bed", state.bedPos(), 1.0D, 0));
                }
            }
            case DELIVER_ITEM -> {
                actions.add(PlannedAction.move("\u9760\u8fd1\u6536\u8d27\u73a9\u5bb6", "delivery_receiver", state.ownerPos(), 1.25D));
                actions.add(PlannedAction.simple(GoapActionType.DELIVER_ITEM, "\u4ea4\u4ed8\u7269\u8d44"));
            }
            case COLLECT_WOOD -> actions.add(new PlannedAction(GoapActionType.CHOP_TREE, "\u780d\u6811\u5e76\u56de\u6536\u539f\u6728", "wood", state.woodPos(), 1.08D, DEFAULT_WOOD_TARGET));
            case COLLECT_ORE -> actions.add(new PlannedAction(GoapActionType.MINE_ORE, "\u524d\u5f80\u77ff\u70b9\u5e76\u91c7\u6398", "ore", state.orePos(), 1.05D, 1));
            case COLLECT_FOOD -> actions.add(new PlannedAction(GoapActionType.HARVEST_CROP, "\u6536\u5272\u6210\u719f\u4f5c\u7269", "crop", state.cropPos(), 1.0D, 1));
            case BUILD_SHELTER -> actions.add(PlannedAction.simple(GoapActionType.BUILD_SHELTER, "\u6309\u84dd\u56fe\u642d\u5efa\u907f\u96be\u6240"));
            case EXPLORE_AREA, TALK_ONLY, IDLE -> actions.add(PlannedAction.simple(GoapActionType.OBSERVE_AND_REPORT, "\u89c2\u5bdf\u5e76\u6c47\u62a5"));
            case SURVIVE -> {
                if (state.inHazard() || state.onFire()) {
                    actions.add(PlannedAction.simple(GoapActionType.RETREAT_TO_SAFE_GROUND, "\u5148\u8131\u79bb\u5371\u9669\u73af\u5883"));
                } else if (state.lowFood() && state.cropKnown()) {
                    actions.add(new PlannedAction(GoapActionType.HARVEST_CROP, "\u5148\u8865\u5145\u98df\u7269", "crop", state.cropPos(), 1.0D, 1));
                } else if (state.night()) {
                    actions.add(PlannedAction.simple(GoapActionType.BUILD_SHELTER, "\u591c\u665a\u642d\u5efa\u907f\u96be\u6240"));
                } else if ((state.lowTools() || state.buildingUnits() < DEFAULT_WOOD_TARGET) && state.woodKnown()) {
                    actions.add(new PlannedAction(GoapActionType.CHOP_TREE, "\u8865\u8db3\u6728\u6750\u50a8\u5907", "wood", state.woodPos(), 1.08D, DEFAULT_WOOD_TARGET));
                } else {
                    actions.add(PlannedAction.simple(GoapActionType.OBSERVE_AND_REPORT, "\u6301\u7eed\u66f4\u65b0\u89c2\u5bdf\u4e0e\u8bb0\u5fc6"));
                }
            }
        }
        return new GoalPlan(goal, actions, goal.reasoning() + "\uff1bGOAP \u672a\u627e\u5230\u5b8c\u6574\u94fe\u8def\uff0c\u4f7f\u7528\u515c\u5e95\u52a8\u4f5c\u5e8f\u5217", goal.source(), desiredConditions);
    }

    private static GoapCondition require(GoapFact fact, boolean expected) {
        return GoapCondition.require(fact, expected, fact.displayName() + (expected ? "=\u662f" : "=\u5426"));
    }

    private static GoapEffect effect(GoapFact fact, boolean value) {
        return GoapEffect.set(fact, value, fact.displayName() + "->" + (value ? "\u662f" : "\u5426"));
    }

    private static GoapActionDefinition action(GoapActionType type, String label, int cost, String targetKey, double speed, int requiredCount, List<GoapCondition> preconditions, List<GoapEffect> effects, Predicate<WorldStateSnapshot> availability) {
        return new GoapActionDefinition(type, label, cost, targetKey, speed, requiredCount, preconditions, effects, availability);
    }

    private record SearchNode(GoapPlanningState state, List<PlannedAction> actions, int cost) {
        private int score(List<GoapCondition> desiredConditions) {
            return this.cost + (int)desiredConditions.stream().filter(condition -> !condition.matches(this.state)).count() * 2;
        }
    }
}

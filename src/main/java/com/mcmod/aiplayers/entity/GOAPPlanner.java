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
            String reasoning = goal.reasoning() + "；目标条件=" + new GoalPlan(goal, List.of(), goal.reasoning(), goal.source(), desiredConditions).goalConditionSummary();
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
        actions.add(action(GoapActionType.MOVE_TO_TARGET, "靠近主人", 1, "owner", 1.18D, 0,
                List.of(require(GoapFact.OWNER_AVAILABLE, true)),
                List.of(effect(GoapFact.OWNER_NEAR, true), effect(GoapFact.OWNER_SAFE, true)),
                snapshot -> snapshot != null && snapshot.ownerAvailable()));
        actions.add(action(GoapActionType.OBSERVE_AND_REPORT, "观察并汇报", 1, "", 1.0D, 0,
                List.of(),
                List.of(effect(GoapFact.OBSERVATION_REFRESHED, true)),
                snapshot -> true));
        actions.add(action(GoapActionType.RETREAT_TO_SAFE_GROUND, "撤离到安全地带", 1, "", 1.08D, 0,
                List.of(),
                List.of(effect(GoapFact.SAFE, true), effect(GoapFact.OWNER_SAFE, true)),
                snapshot -> snapshot != null && (snapshot.inHazard() || snapshot.onFire() || snapshot.navigationStuck())));
        actions.add(action(GoapActionType.REST_AT_BED, "前往床位休整", 2, "bed", 1.0D, 0,
                List.of(require(GoapFact.HAS_BED_TARGET, true)),
                List.of(effect(GoapFact.HEALTHY, true), effect(GoapFact.SAFE, true)),
                snapshot -> snapshot != null && snapshot.bedKnown()));
        actions.add(action(GoapActionType.ACQUIRE_TOOL, "补齐基础工具", 2, "", 1.0D, 0,
                List.of(),
                List.of(effect(GoapFact.HAS_TOOLS, true)),
                snapshot -> snapshot != null && snapshot.lowTools()));
        actions.add(action(GoapActionType.CHOP_TREE, "砍树并回收原木", 2, "wood", 1.08D, DEFAULT_WOOD_TARGET,
                List.of(require(GoapFact.HAS_WOOD_TARGET, true)),
                List.of(effect(GoapFact.HAS_LOGS, true), effect(GoapFact.HAS_PLANKS, true), effect(GoapFact.HAS_BUILDING_MATERIALS, true)),
                snapshot -> snapshot != null && snapshot.woodKnown()));
        actions.add(action(GoapActionType.CRAFT_PLANKS, "把原木转成木板", 1, "", 1.0D, 4,
                List.of(require(GoapFact.HAS_LOGS, true)),
                List.of(effect(GoapFact.HAS_PLANKS, true), effect(GoapFact.HAS_BUILDING_MATERIALS, true)),
                snapshot -> snapshot != null && snapshot.logCount() > 0));
        actions.add(action(GoapActionType.CRAFT_TORCH, "制作火把", 1, "", 1.0D, 4,
                List.of(require(GoapFact.HAS_PLANKS, true)),
                List.of(effect(GoapFact.HAS_TORCH_SUPPLY, true)),
                snapshot -> snapshot != null && snapshot.torchCount() < 4));
        actions.add(action(GoapActionType.MINE_ORE, "前往矿点并采掘", 3, "ore", 1.05D, 1,
                List.of(require(GoapFact.HAS_ORE_TARGET, true), require(GoapFact.HAS_TOOLS, true), require(GoapFact.HAS_BACKPACK_SPACE, true)),
                List.of(effect(GoapFact.HAS_ORE_SUPPLY, true)),
                snapshot -> snapshot != null && snapshot.oreKnown()));
        actions.add(action(GoapActionType.HARVEST_CROP, "收割成熟作物", 2, "crop", 1.0D, 1,
                List.of(require(GoapFact.HAS_CROP_TARGET, true)),
                List.of(effect(GoapFact.HAS_FOOD_SUPPLY, true)),
                snapshot -> snapshot != null && snapshot.cropKnown()));
        actions.add(action(GoapActionType.CRAFT_BREAD, "合成可恢复食物", 3, "", 1.0D, 2,
                List.of(require(GoapFact.HAS_CROP_TARGET, true)),
                List.of(effect(GoapFact.HAS_FOOD_SUPPLY, true)),
                snapshot -> snapshot != null && (snapshot.cropKnown() || snapshot.breadCount() > 0)));
        actions.add(action(GoapActionType.BUILD_SHELTER, "按蓝图搭建避难所", 3, "", 1.0D, 0,
                List.of(require(GoapFact.HAS_BUILDING_MATERIALS, true)),
                List.of(effect(GoapFact.SHELTER_READY, true), effect(GoapFact.SAFE, true)),
                snapshot -> true));
        actions.add(action(GoapActionType.DELIVER_ITEM, "靠近主人并交付物资", 2, "delivery_receiver", 1.2D, 0,
                List.of(require(GoapFact.DELIVERY_PENDING, true), require(GoapFact.OWNER_AVAILABLE, true)),
                List.of(effect(GoapFact.DELIVERY_COMPLETE, true), effect(GoapFact.OWNER_NEAR, true)),
                snapshot -> snapshot != null && snapshot.pendingDelivery()));
        actions.add(action(GoapActionType.COLLECT_DROP, "回收附近掉落物", 1, "", 1.0D, 0,
                List.of(require(GoapFact.HAS_BACKPACK_SPACE, true)),
                List.of(effect(GoapFact.OBSERVATION_REFRESHED, true)),
                snapshot -> snapshot != null && snapshot.dropNearby()));
        actions.add(action(GoapActionType.MOVE_TO_TARGET, "前往探索区域", 1, "explore", 1.0D, 0,
                List.of(),
                List.of(effect(GoapFact.OBSERVATION_REFRESHED, true)),
                snapshot -> true));
        return actions;
    }

    private static GoalPlan buildFallbackPlan(AgentGoal goal, WorldStateSnapshot state, List<GoapCondition> desiredConditions) {
        List<PlannedAction> actions = new ArrayList<>();
        switch (goal.type()) {
            case FOLLOW_OWNER -> actions.add(PlannedAction.move("靠近主人", "owner", state.ownerPos(), 1.18D));
            case GUARD_OWNER -> {
                actions.add(PlannedAction.move("靠近主人", "owner", state.ownerPos(), 1.18D));
                actions.add(PlannedAction.simple(GoapActionType.OBSERVE_AND_REPORT, "观察主人附近威胁"));
            }
            case RECOVER_SELF -> {
                actions.add(PlannedAction.simple(GoapActionType.RETREAT_TO_SAFE_GROUND, "尝试脱离危险地形"));
                if (state.bedKnown()) {
                    actions.add(new PlannedAction(GoapActionType.REST_AT_BED, "前往床位休整", "bed", state.bedPos(), 1.0D, 0));
                }
            }
            case DELIVER_ITEM -> {
                actions.add(PlannedAction.move("靠近收货玩家", "delivery_receiver", state.ownerPos(), 1.25D));
                actions.add(PlannedAction.simple(GoapActionType.DELIVER_ITEM, "交付物资"));
            }
            case COLLECT_WOOD -> actions.add(new PlannedAction(GoapActionType.CHOP_TREE, "砍树并回收原木", "wood", state.woodPos(), 1.08D, DEFAULT_WOOD_TARGET));
            case COLLECT_ORE -> actions.add(new PlannedAction(GoapActionType.MINE_ORE, "前往矿点并采掘", "ore", state.orePos(), 1.05D, 1));
            case COLLECT_FOOD -> actions.add(new PlannedAction(GoapActionType.HARVEST_CROP, "收割成熟作物", "crop", state.cropPos(), 1.0D, 1));
            case BUILD_SHELTER -> actions.add(PlannedAction.simple(GoapActionType.BUILD_SHELTER, "按蓝图搭建避难所"));
            case EXPLORE_AREA, TALK_ONLY, IDLE -> actions.add(PlannedAction.simple(GoapActionType.OBSERVE_AND_REPORT, "观察并汇报"));
            case SURVIVE -> {
                if (state.inHazard() || state.onFire()) {
                    actions.add(PlannedAction.simple(GoapActionType.RETREAT_TO_SAFE_GROUND, "先脱离危险环境"));
                } else if (state.lowFood() && state.cropKnown()) {
                    actions.add(new PlannedAction(GoapActionType.HARVEST_CROP, "先补充食物", "crop", state.cropPos(), 1.0D, 1));
                } else if (state.night()) {
                    actions.add(PlannedAction.simple(GoapActionType.BUILD_SHELTER, "夜晚搭建避难所"));
                } else if ((state.lowTools() || state.buildingUnits() < DEFAULT_WOOD_TARGET) && state.woodKnown()) {
                    actions.add(new PlannedAction(GoapActionType.CHOP_TREE, "补足木材储备", "wood", state.woodPos(), 1.08D, DEFAULT_WOOD_TARGET));
                } else {
                    actions.add(PlannedAction.simple(GoapActionType.OBSERVE_AND_REPORT, "持续更新观察与记忆"));
                }
            }
        }
        return new GoalPlan(goal, actions, goal.reasoning() + "；GOAP 未找到完整链路，使用兜底动作序列", goal.source(), desiredConditions);
    }

    private static GoapCondition require(GoapFact fact, boolean expected) {
        return GoapCondition.require(fact, expected, fact.displayName() + (expected ? "=是" : "=否"));
    }

    private static GoapEffect effect(GoapFact fact, boolean value) {
        return GoapEffect.set(fact, value, fact.displayName() + "->" + (value ? "是" : "否"));
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

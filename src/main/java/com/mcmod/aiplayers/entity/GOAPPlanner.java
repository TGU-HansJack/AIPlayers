package com.mcmod.aiplayers.entity;

import java.util.ArrayList;
import java.util.List;

public final class GOAPPlanner {
    private static final int DEFAULT_WOOD_TARGET = 12;

    private GOAPPlanner() {
    }

    public static GoalPlan plan(AgentGoal goal, WorldStateSnapshot state) {
        List<PlannedAction> actions = new ArrayList<>();
        String reasoning = goal.reasoning();
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
            case COLLECT_WOOD -> {
                if (state.lowTools()) {
                    actions.add(PlannedAction.simple(GoapActionType.ACQUIRE_TOOL, "补齐基础工具"));
                }
                actions.add(new PlannedAction(GoapActionType.CHOP_TREE, "砍树并回收原木", "wood", state.woodPos(), 1.08D, DEFAULT_WOOD_TARGET));
                actions.add(new PlannedAction(GoapActionType.CRAFT_PLANKS, "把原木转成木板", "", null, 1.0D, 4));
            }
            case COLLECT_ORE -> {
                if (state.lowTools()) {
                    actions.add(PlannedAction.simple(GoapActionType.ACQUIRE_TOOL, "先补齐采矿工具"));
                }
                actions.add(new PlannedAction(GoapActionType.MINE_ORE, "前往矿点并采掘", "ore", state.orePos(), 1.05D, 1));
            }
            case COLLECT_FOOD -> {
                actions.add(new PlannedAction(GoapActionType.HARVEST_CROP, "收割成熟作物", "crop", state.cropPos(), 1.0D, 1));
                actions.add(new PlannedAction(GoapActionType.CRAFT_BREAD, "合成可恢复食物", "", null, 1.0D, 2));
            }
            case BUILD_SHELTER -> {
                if (state.lowTools()) {
                    actions.add(PlannedAction.simple(GoapActionType.ACQUIRE_TOOL, "补齐基础工具"));
                }
                if (state.buildingUnits() < DEFAULT_WOOD_TARGET) {
                    actions.add(new PlannedAction(GoapActionType.CHOP_TREE, "补足建材", "wood", state.woodPos(), 1.08D, DEFAULT_WOOD_TARGET));
                    actions.add(new PlannedAction(GoapActionType.CRAFT_PLANKS, "整理建造用木板", "", null, 1.0D, 4));
                }
                actions.add(PlannedAction.simple(GoapActionType.BUILD_SHELTER, "按蓝图搭建避难所"));
            }
            case EXPLORE_AREA -> {
                actions.add(PlannedAction.move("前往新的可达区域", "explore", null, 1.0D));
                actions.add(PlannedAction.simple(GoapActionType.OBSERVE_AND_REPORT, "更新环境观察"));
            }
            case SURVIVE -> {
                if (state.inHazard() || state.onFire()) {
                    actions.add(PlannedAction.simple(GoapActionType.RETREAT_TO_SAFE_GROUND, "先脱离危险环境"));
                } else if (state.lowFood() && state.cropKnown()) {
                    actions.add(new PlannedAction(GoapActionType.HARVEST_CROP, "先补充食物", "crop", state.cropPos(), 1.0D, 1));
                    actions.add(new PlannedAction(GoapActionType.CRAFT_BREAD, "合成面包", "", null, 1.0D, 2));
                } else if (state.night() && state.buildingUnits() >= DEFAULT_WOOD_TARGET) {
                    actions.add(PlannedAction.simple(GoapActionType.BUILD_SHELTER, "夜晚搭建避难所"));
                } else if ((state.lowTools() || state.buildingUnits() < DEFAULT_WOOD_TARGET) && state.woodKnown()) {
                    actions.add(new PlannedAction(GoapActionType.CHOP_TREE, "补足木材储备", "wood", state.woodPos(), 1.08D, DEFAULT_WOOD_TARGET));
                } else if (state.oreKnown() && state.freeBackpackSlots() > 2) {
                    actions.add(new PlannedAction(GoapActionType.MINE_ORE, "顺路采矿补给", "ore", state.orePos(), 1.05D, 1));
                } else {
                    actions.add(PlannedAction.move("保持附近巡游", state.ownerAvailable() ? "owner" : "explore", state.ownerPos(), 1.0D));
                    actions.add(PlannedAction.simple(GoapActionType.OBSERVE_AND_REPORT, "持续更新观察与记忆"));
                }
            }
            case TALK_ONLY -> actions.add(PlannedAction.simple(GoapActionType.OBSERVE_AND_REPORT, "整理当前观察并回复"));
            case IDLE -> actions.add(PlannedAction.simple(GoapActionType.OBSERVE_AND_REPORT, "保持待命观察"));
        }
        return new GoalPlan(goal, actions, reasoning, goal.source());
    }
}

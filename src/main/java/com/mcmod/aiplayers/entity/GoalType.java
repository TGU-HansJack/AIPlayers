package com.mcmod.aiplayers.entity;

import java.util.Locale;

public enum GoalType {
    IDLE("idle", "待命", AIPlayerMode.IDLE),
    FOLLOW_OWNER("follow_owner", "跟随主人", AIPlayerMode.FOLLOW),
    GUARD_OWNER("guard_owner", "护卫主人", AIPlayerMode.GUARD),
    SURVIVE("survive", "自主生存", AIPlayerMode.SURVIVE),
    COLLECT_WOOD("collect_wood", "收集木头", AIPlayerMode.GATHER_WOOD),
    COLLECT_ORE("collect_ore", "采集矿石", AIPlayerMode.MINE),
    COLLECT_FOOD("collect_food", "收集食物", AIPlayerMode.SURVIVE),
    BUILD_SHELTER("build_shelter", "建造避难所", AIPlayerMode.BUILD_SHELTER),
    DELIVER_ITEM("deliver_item", "交付物品", AIPlayerMode.FOLLOW),
    EXPLORE_AREA("explore_area", "探索区域", AIPlayerMode.EXPLORE),
    RECOVER_SELF("recover_self", "脱困自救", AIPlayerMode.SURVIVE),
    TALK_ONLY("talk_only", "自由对话", AIPlayerMode.IDLE);

    private final String commandName;
    private final String displayName;
    private final AIPlayerMode coarseMode;

    GoalType(String commandName, String displayName, AIPlayerMode coarseMode) {
        this.commandName = commandName;
        this.displayName = displayName;
        this.coarseMode = coarseMode;
    }

    public String commandName() { return this.commandName; }
    public String displayName() { return this.displayName; }
    public AIPlayerMode coarseMode() { return this.coarseMode; }

    public static GoalType fromCommand(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String normalized = input.toLowerCase(Locale.ROOT).trim();
        for (GoalType value : values()) {
            if (value.commandName.equals(normalized)) {
                return value;
            }
        }
        return null;
    }

    public static GoalType fromMode(AIPlayerMode mode) {
        if (mode == null) {
            return IDLE;
        }
        return switch (mode) {
            case IDLE -> IDLE;
            case FOLLOW -> FOLLOW_OWNER;
            case GUARD -> GUARD_OWNER;
            case GATHER_WOOD -> COLLECT_WOOD;
            case MINE -> COLLECT_ORE;
            case EXPLORE -> EXPLORE_AREA;
            case BUILD_SHELTER -> BUILD_SHELTER;
            case SURVIVE -> SURVIVE;
        };
    }

    public static GoalType fromLegacyText(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        GoalType direct = fromCommand(input);
        if (direct != null) {
            return direct;
        }
        AIPlayerMode mode = AIPlayerMode.fromCommand(input);
        if (mode != null) {
            return fromMode(mode);
        }
        String normalized = input.toLowerCase(Locale.ROOT);
        if (normalized.contains("follow")) return FOLLOW_OWNER;
        if (normalized.contains("guard")) return GUARD_OWNER;
        if (normalized.contains("wood") || normalized.contains("tree")) return COLLECT_WOOD;
        if (normalized.contains("ore") || normalized.contains("mine")) return COLLECT_ORE;
        if (normalized.contains("food") || normalized.contains("crop")) return COLLECT_FOOD;
        if (normalized.contains("build") || normalized.contains("shelter")) return BUILD_SHELTER;
        if (normalized.contains("deliver") || normalized.contains("give")) return DELIVER_ITEM;
        if (normalized.contains("recover") || normalized.contains("unstuck")) return RECOVER_SELF;
        if (normalized.contains("explore")) return EXPLORE_AREA;
        if (normalized.contains("survive")) return SURVIVE;
        return null;
    }
}

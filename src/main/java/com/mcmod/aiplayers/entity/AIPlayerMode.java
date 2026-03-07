package com.mcmod.aiplayers.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum AIPlayerMode {
    IDLE("idle", "待命"),
    FOLLOW("follow", "跟随"),
    GUARD("guard", "护卫"),
    GATHER_WOOD("gather_wood", "砍树"),
    MINE("mine", "挖矿"),
    EXPLORE("explore", "探索"),
    BUILD_SHELTER("build_shelter", "建造"),
    SURVIVE("survive", "生存");

    private final String commandName;
    private final String displayName;

    AIPlayerMode(String commandName, String displayName) {
        this.commandName = commandName;
        this.displayName = displayName;
    }

    public String commandName() {
        return this.commandName;
    }

    public String displayName() {
        return this.displayName;
    }

    public static AIPlayerMode fromCommand(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).trim();
        return Arrays.stream(values())
                .filter(mode -> mode.commandName.equals(normalized))
                .findFirst()
                .orElse(null);
    }

    public static List<String> commandNames() {
        return Arrays.stream(values()).map(AIPlayerMode::commandName).toList();
    }
}
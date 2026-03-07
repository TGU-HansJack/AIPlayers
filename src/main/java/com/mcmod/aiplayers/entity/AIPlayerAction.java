package com.mcmod.aiplayers.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum AIPlayerAction {
    JUMP("jump", "跳跃"),
    CROUCH("crouch", "下蹲"),
    STAND("stand", "站起"),
    LOOK_UP("look_up", "抬头"),
    LOOK_DOWN("look_down", "低头"),
    LOOK_OWNER("look_owner", "看向主人");

    private final String commandName;
    private final String displayName;

    AIPlayerAction(String commandName, String displayName) {
        this.commandName = commandName;
        this.displayName = displayName;
    }

    public String commandName() {
        return this.commandName;
    }

    public String displayName() {
        return this.displayName;
    }

    public static AIPlayerAction fromCommand(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).trim();
        return Arrays.stream(values())
                .filter(action -> action.commandName.equals(normalized))
                .findFirst()
                .orElse(null);
    }

    public static List<String> commandNames() {
        return Arrays.stream(values()).map(AIPlayerAction::commandName).toList();
    }
}
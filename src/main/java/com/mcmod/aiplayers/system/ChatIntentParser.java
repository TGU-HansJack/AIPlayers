package com.mcmod.aiplayers.system;

import java.util.Locale;

public final class ChatIntentParser {
    private ChatIntentParser() {
    }

    public static ChatIntent parse(String message) {
        if (message == null || message.isBlank()) {
            return ChatIntent.HELP;
        }

        String normalized = message.toLowerCase(Locale.ROOT).trim();

        if (containsAny(normalized, "你好", "嗨", "hello", "hi", "hey")) {
            return ChatIntent.GREET;
        }
        if (containsAny(normalized, "帮助", "help", "你会", "能做什么")) {
            return ChatIntent.HELP;
        }
        if (containsAny(normalized, "背包", "包里", "物品", "库存", "inventory", "bag", "what do you have")) {
            return ChatIntent.INVENTORY;
        }
        if (containsAny(normalized, "记忆", "回忆", "memory", "recent memory")) {
            return ChatIntent.MEMORY;
        }
        if (containsAny(normalized, "计划", "规划", "接下来", "下一步", "plan", "what next")) {
            return ChatIntent.PLAN;
        }
        if (containsAny(normalized, "状态", "情况", "看到了什么", "看见什么", "status", "report", "what do you see", "what can you see")) {
            return ChatIntent.STATUS;
        }
        if (containsAny(normalized, "跟随", "跟着", "follow", "come with me")) {
            return ChatIntent.FOLLOW;
        }
        if (containsAny(normalized, "快跟上", "跟紧", "过来", "回来", "come here")) {
            return ChatIntent.FOLLOW;
        }
        if (containsAny(normalized, "护卫", "保护", "guard", "protect")) {
            return ChatIntent.GUARD;
        }
        if (containsAny(normalized, "砍树", "木头", "原木", "chop", "wood", "tree", "log")) {
            return ChatIntent.GATHER_WOOD;
        }
        if (containsAny(normalized, "挖矿", "矿", "mine", "ore", "mining")) {
            return ChatIntent.MINE;
        }
        if (containsAny(normalized, "探索", "逛逛", "explore", "scout")) {
            return ChatIntent.EXPLORE;
        }
        if (containsAny(normalized, "建造", "搭房子", "盖房子", "庇护所", "build", "shelter")) {
            return ChatIntent.BUILD;
        }
        if (containsAny(normalized, "生存", "活下去", "survive")) {
            return ChatIntent.SURVIVE;
        }
        if (containsAny(normalized, "跳", "跳一下", "jump")) {
            return ChatIntent.JUMP;
        }
        if (containsAny(normalized, "蹲", "潜行", "下蹲", "crouch", "sneak")) {
            return ChatIntent.CROUCH;
        }
        if (containsAny(normalized, "站起来", "站立", "取消潜行", "stand", "unsneak")) {
            return ChatIntent.STAND;
        }
        if (containsAny(normalized, "抬头", "往上看", "look up")) {
            return ChatIntent.LOOK_UP;
        }
        if (containsAny(normalized, "低头", "往下看", "look down")) {
            return ChatIntent.LOOK_DOWN;
        }
        if (containsAny(normalized, "看我", "看向我", "look at me", "look owner")) {
            return ChatIntent.LOOK_OWNER;
        }
        if (containsAny(normalized, "脱困", "出来", "卡住了", "卡住", "unstuck", "recover")) {
            return ChatIntent.RECOVER;
        }
        if (containsAny(normalized, "停止", "待命", "停下", "stop", "wait", "idle")) {
            return ChatIntent.STOP;
        }

        return ChatIntent.UNKNOWN;
    }

    private static boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }
}

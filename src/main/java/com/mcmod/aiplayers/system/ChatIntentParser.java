package com.mcmod.aiplayers.system;

import java.util.Locale;

public final class ChatIntentParser {
    private ChatIntentParser() {
    }

    public static ChatIntent parse(String message) {
        if (message == null || message.isBlank()) {
            return ChatIntent.HELP;
        }

        String normalized = normalize(message);
        String compact = normalized.replace(" ", "");

        if (containsAny(normalized, compact, "你好", "嗨", "hello", "hi", "hey")) {
            return ChatIntent.GREET;
        }
        if (containsAny(normalized, compact, "帮助", "help", "你会", "能做什么", "你能干嘛", "你可以做什么")) {
            return ChatIntent.HELP;
        }
        if (containsAny(normalized, compact, "背包", "包里", "物品", "库存", "查看背包", "看看背包", "inventory", "bag", "what do you have")) {
            return ChatIntent.INVENTORY;
        }
        if (containsAny(normalized, compact, "记忆", "回忆", "memory", "recent memory")) {
            return ChatIntent.MEMORY;
        }
        if (containsAny(normalized, compact, "计划", "规划", "接下来", "下一步", "计划一下", "说说计划", "plan", "what next")) {
            return ChatIntent.PLAN;
        }
        if (containsAny(normalized, compact, "状态", "情况", "汇报", "现在怎么样", "看到了什么", "看见什么", "status", "report", "what do you see", "what can you see")) {
            return ChatIntent.STATUS;
        }
        if ((containsAny(normalized, compact, "给我", "交给我", "给一下", "拿给我", "递给我", "丢给我", "give me", "bring me")
                && containsAny(normalized, compact, "木头", "原木", "木板", "圆石", "面包", "煤", "铁", "石镐", "斧头", "物品", "库存", "log", "wood", "plank", "cobble", "bread", "coal", "iron", "pickaxe", "axe"))
                || containsAny(normalized, compact, "把木头给我", "把原木给我", "把木板给我", "把圆石给我", "把面包给我", "把石头给我")) {
            return ChatIntent.GIVE_ITEM;
        }
        if (containsAny(normalized, compact, "跟随", "跟着", "跟我走", "跟我来", "跟上我", "follow", "come with me")) {
            return ChatIntent.FOLLOW;
        }
        if (containsAny(normalized, compact, "快跟上", "跟紧", "过来", "回来", "过来一下", "快过来", "come here")) {
            return ChatIntent.FOLLOW;
        }
        if (containsAny(normalized, compact, "护卫", "保护", "guard", "protect")) {
            return ChatIntent.GUARD;
        }
        if (containsAny(normalized, compact, "砍树", "砍点树", "砍木头", "木头", "原木", "chop", "wood", "tree", "log")) {
            return ChatIntent.GATHER_WOOD;
        }
        if (containsAny(normalized, compact, "挖矿", "挖点矿", "采矿", "矿", "矿石", "mine", "ore", "mining")) {
            return ChatIntent.MINE;
        }
        if (containsAny(normalized, compact, "探索", "逛逛", "侦查", "explore", "scout")) {
            return ChatIntent.EXPLORE;
        }
        if (containsAny(normalized, compact, "建造", "搭房子", "盖房子", "造房子", "造个家", "庇护所", "避难所", "build", "shelter")) {
            return ChatIntent.BUILD;
        }
        if (containsAny(normalized, compact, "生存", "活下去", "自己生存", "survive")) {
            return ChatIntent.SURVIVE;
        }
        if (containsAny(normalized, compact, "跳", "跳一下", "跳起来", "jump")) {
            return ChatIntent.JUMP;
        }
        if (containsAny(normalized, compact, "蹲", "潜行", "下蹲", "蹲下", "crouch", "sneak")) {
            return ChatIntent.CROUCH;
        }
        if (containsAny(normalized, compact, "站起来", "站立", "取消潜行", "起身", "stand", "unsneak")) {
            return ChatIntent.STAND;
        }
        if (containsAny(normalized, compact, "抬头", "台头", "拾头", "往上看", "look up")) {
            return ChatIntent.LOOK_UP;
        }
        if (containsAny(normalized, compact, "低头", "往下看", "look down")) {
            return ChatIntent.LOOK_DOWN;
        }
        if (containsAny(normalized, compact, "看我", "看向我", "看着我", "look at me", "look owner")) {
            return ChatIntent.LOOK_OWNER;
        }
        if (containsAny(normalized, compact, "脱困", "出来", "救一下自己", "卡住了", "卡住", "unstuck", "recover")) {
            return ChatIntent.RECOVER;
        }
        if (containsAny(normalized, compact, "停止", "待命", "停下", "停一下", "别做了", "先别忙了", "stop", "wait", "idle")) {
            return ChatIntent.STOP;
        }

        return ChatIntent.UNKNOWN;
    }

    private static String normalize(String message) {
        return message.toLowerCase(Locale.ROOT)
                .replace('@', ' ')
                .replace('，', ' ')
                .replace('。', ' ')
                .replace('！', ' ')
                .replace('？', ' ')
                .replace('、', ' ')
                .replace('：', ' ')
                .replace(':', ' ')
                .replace('；', ' ')
                .replace(';', ' ')
                .replace('（', ' ')
                .replace('）', ' ')
                .replace('(', ' ')
                .replace(')', ' ')
                .replace('【', ' ')
                .replace('】', ' ')
                .replace('[', ' ')
                .replace(']', ' ')
                .replace('“', ' ')
                .replace('”', ' ')
                .replace('"', ' ')
                .replace('·', ' ')
                .replace(',', ' ')
                .replace('.', ' ')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean containsAny(String text, String compact, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token) || compact.contains(token.replace(" ", ""))) {
                return true;
            }
        }
        return false;
    }
}

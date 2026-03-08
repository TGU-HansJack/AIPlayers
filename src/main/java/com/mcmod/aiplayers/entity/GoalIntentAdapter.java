package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.system.ChatIntent;
import com.mcmod.aiplayers.system.ChatIntentParser;
import net.minecraft.server.level.ServerPlayer;

public final class GoalIntentAdapter {
    private GoalIntentAdapter() {
    }

    public static GoalDirective adapt(AIPlayerEntity entity, ServerPlayer speaker, String content) {
        ChatIntent intent = ChatIntentParser.parse(content);
        if (intent == ChatIntent.UNKNOWN) {
            return null;
        }
        if (intent == ChatIntent.GIVE_ITEM) {
            return GoalDirective.delivery();
        }
        return switch (intent) {
            case GREET -> GoalDirective.info("你好，我已就绪。" + entity.getObservationSummary());
            case HELP -> GoalDirective.info("你可以让我跟随、护卫、砍树、挖矿、探索、建造、生存，也可以让我跳跃、下蹲、抬头、查看背包、把木头给你、脱困、查看记忆或询问当前计划。");
            case STATUS -> GoalDirective.info(entity.getStatusSummary());
            case INVENTORY -> GoalDirective.info("当前背包：" + entity.getDetailedInventorySummary());
            case MEMORY -> GoalDirective.info("最近记忆：" + entity.getMemorySummary());
            case PLAN -> GoalDirective.info("当前规划：" + entity.getPlanSummary());
            case FOLLOW -> GoalDirective.goal("收到，我开始跟随你。", AgentGoal.of(GoalType.FOLLOW_OWNER, "player", "玩家要求跟随"));
            case GUARD -> GoalDirective.goal("收到，我会优先保护你。", AgentGoal.of(GoalType.GUARD_OWNER, "player", "玩家要求护卫"));
            case GATHER_WOOD -> GoalDirective.goal("开始收集木头，我会优先处理可达树干。", AgentGoal.of(GoalType.COLLECT_WOOD, "player", "玩家要求采木"));
            case MINE -> GoalDirective.goal("开始挖矿，我会先寻找可达矿点。", AgentGoal.of(GoalType.COLLECT_ORE, "player", "玩家要求采矿"));
            case EXPLORE -> GoalDirective.goal("开始探索，我会记录资源和危险位置。", AgentGoal.of(GoalType.EXPLORE_AREA, "player", "玩家要求探索"));
            case BUILD -> GoalDirective.goal("开始建造避难所，我会先补足建材。", AgentGoal.of(GoalType.BUILD_SHELTER, "player", "玩家要求建造"));
            case SURVIVE -> GoalDirective.goal("进入自主生存模式，我会持续观察、采集、恢复和建造。", AgentGoal.of(GoalType.SURVIVE, "player", "玩家要求自主生存"));
            case JUMP -> GoalDirective.action("", AIPlayerAction.JUMP);
            case CROUCH -> GoalDirective.action("", AIPlayerAction.CROUCH);
            case STAND -> GoalDirective.action("", AIPlayerAction.STAND);
            case LOOK_UP -> GoalDirective.action("", AIPlayerAction.LOOK_UP);
            case LOOK_DOWN -> GoalDirective.action("", AIPlayerAction.LOOK_DOWN);
            case LOOK_OWNER -> GoalDirective.action("", AIPlayerAction.LOOK_OWNER);
            case RECOVER -> GoalDirective.goal("收到，我先优先脱困。", AgentGoal.of(GoalType.RECOVER_SELF, "player", "玩家要求脱困"));
            case STOP -> GoalDirective.stop("已停止当前任务，进入待命。");
            case GIVE_ITEM, UNKNOWN -> null;
        };
    }
}

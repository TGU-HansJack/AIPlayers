package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.ai.AIServiceManager;
import com.mcmod.aiplayers.ai.AIServiceResponse;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ConversationRouter {
    private ConversationRouter() {
    }

    public static String handle(AIPlayerEntity entity, ServerPlayer speaker, String content) {
        if (!entity.canReceiveOrdersFrom(speaker)) {
            return "我现在只接受绑定玩家的命令。";
        }
        entity.runtimeRemember("对话", speaker.getName().getString() + "：" + content);
        GoalDirective directive = GoalIntentAdapter.adapt(entity, speaker, content);
        if (directive != null) {
            if (directive.deliveryRequest()) {
                return entity.runtimeHandleDeliveryRequest(speaker, content);
            }
            if (directive.stopRequested()) {
                entity.runtimeStopGoal();
                return directive.reply();
            }
            if (directive.action() != null) {
                return entity.performAction(directive.action());
            }
            if (directive.goal() != null) {
                entity.runtimeApplyGoalDirective(speaker, directive.goal(), true);
                return directive.reply();
            }
            if (directive.reply() != null && !directive.reply().isBlank()) {
                return directive.reply();
            }
        }
        AnimalTargetHelper.HuntTarget huntTarget = AnimalTargetHelper.parseHuntDirective(content);
        if (huntTarget != null) {
            return entity.startAnimalHunt(speaker, huntTarget.targetId(), huntTarget.label());
        }
        if (AnimalTargetHelper.mentionsAttackIntent(content)) {
            return "请告诉我要攻击的原版动物，例如：" + AnimalTargetHelper.supportedAnimalsSummary() + "。";
        }
        if (!AIServiceManager.canUseConversationService()) {
            return entity.runtimeBuildLocalConversationReply(speaker, content, false);
        }
        if (entity.runtimeIsPendingConversation()) {
            return "我还在思考上一条消息，请稍等。";
        }
        entity.runtimeSetPendingConversation(true);
        entity.runtimeMarkDirty();
        AIServiceManager.tryRespondAsync(entity, speaker, content).whenComplete((apiResponse, throwable) -> {
            var server = speaker.level().getServer();
            if (server == null) {
                entity.runtimeSetPendingConversation(false);
                return;
            }
            server.execute(() -> {
                entity.runtimeSetPendingConversation(false);
                entity.runtimeMarkDirty();
                String reply;
                if (throwable != null || apiResponse == null) {
                    reply = entity.runtimeBuildLocalConversationReply(speaker, content, true);
                } else {
                    String directiveResult = applyApiDirective(entity, speaker, apiResponse);
                    reply = apiResponse.reply() == null || apiResponse.reply().isBlank() ? directiveResult : apiResponse.reply();
                    entity.runtimeRemember("AI", reply + "（" + apiResponse.source() + "）");
                }
                if (!speaker.isRemoved()) {
                    speaker.sendSystemMessage(Component.literal("[" + entity.getAIName() + "] " + reply));
                }
            });
        });
        return "收到，我先思考一下。";
    }

    private static String applyApiDirective(AIPlayerEntity entity, ServerPlayer speaker, AIServiceResponse response) {
        if (response.goalType() != null && !response.goalType().isBlank()) {
            GoalType goalType = GoalType.fromLegacyText(response.goalType());
            if (goalType != null) {
                entity.runtimeApplyGoalDirective(speaker, AgentGoal.of(goalType, response.goalArgs(), 60, response.source(), "外部对话 AI 指令", GoalType.SURVIVE), true);
                return "我已调整为“" + goalType.displayName() + "”目标。";
            }
        }
        if (response.mode() != null && !response.mode().isBlank() && !"unchanged".equalsIgnoreCase(response.mode())) {
            GoalType goalType = GoalType.fromLegacyText(response.mode());
            if (goalType != null) {
                entity.runtimeApplyGoalDirective(speaker, AgentGoal.of(goalType, response.goalArgs(), 60, response.source(), "外部对话 AI 指令", GoalType.SURVIVE), true);
                return "我已调整任务目标。";
            }
        }
        if (response.action() != null && !response.action().isBlank() && !"none".equalsIgnoreCase(response.action())) {
            AIPlayerAction action = AIPlayerAction.fromCommand(response.action());
            if (action != null) {
                return entity.performAction(action);
            }
        }
        return "我已经处理你的请求。";
    }
}

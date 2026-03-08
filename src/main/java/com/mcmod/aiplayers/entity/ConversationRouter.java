package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.ai.AIServiceManager;
import com.mcmod.aiplayers.ai.AIServiceResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
                    DirectiveApplyResult directiveResult = applyApiDirective(entity, speaker, apiResponse);
                    if (apiResponse.reply() == null || apiResponse.reply().isBlank()) {
                        reply = directiveResult.message();
                    } else if (directiveResult.executed() && !directiveResult.success()) {
                        reply = apiResponse.reply() + "（控制指令失败：" + directiveResult.message() + "）";
                    } else {
                        reply = apiResponse.reply();
                    }
                    entity.runtimeRemember("AI", reply + "（" + apiResponse.source() + "）");
                }
                if (!speaker.isRemoved()) {
                    speaker.sendSystemMessage(Component.literal("[" + entity.getAIName() + "] " + reply));
                }
            });
        });
        return "收到，我先思考一下。";
    }

    private static DirectiveApplyResult applyApiDirective(AIPlayerEntity entity, ServerPlayer speaker, AIServiceResponse response) {
        String controllerFallback = "";
        Map<String, String> mergedActionArgs = mergeDirectiveArgs(response.goalArgs(), response.controllerArgs());
        if (response.hasControllerDirective()) {
            AIControllerHub.ControllerExecutionResult result = entity.executeControllerDirective(
                    speaker,
                    response.controller(),
                    response.controllerAction(),
                    response.controllerArgs());
            if (result.success()) {
                return DirectiveApplyResult.ok(result.message(), true);
            }
            controllerFallback = result.message();
        }
        if (response.goalType() != null && !response.goalType().isBlank()) {
            GoalType goalType = GoalType.fromLegacyText(response.goalType());
            if (goalType != null) {
                entity.runtimeApplyGoalDirective(speaker, AgentGoal.of(goalType, response.goalArgs(), 60, response.source(), "外部对话 AI 指令", GoalType.SURVIVE), true);
                return DirectiveApplyResult.ok("我已调整为“" + goalType.displayName() + "”目标。", true);
            }
        }
        if (response.mode() != null && !response.mode().isBlank() && !"unchanged".equalsIgnoreCase(response.mode())) {
            GoalType goalType = GoalType.fromLegacyText(response.mode());
            if (goalType != null) {
                entity.runtimeApplyGoalDirective(speaker, AgentGoal.of(goalType, response.goalArgs(), 60, response.source(), "外部对话 AI 指令", GoalType.SURVIVE), true);
                return DirectiveApplyResult.ok("我已调整任务目标。", true);
            }
        }
        if (response.action() != null && !response.action().isBlank() && !"none".equalsIgnoreCase(response.action())) {
            AIControllerHub.ControllerExecutionResult controllerActionResult = entity.executeControllerDirective(
                    speaker,
                    "",
                    response.action(),
                    mergedActionArgs);
            if (controllerActionResult.success()) {
                return DirectiveApplyResult.ok(controllerActionResult.message(), true);
            }
            AIPlayerAction action = AIPlayerAction.fromCommand(response.action());
            if (action != null) {
                return DirectiveApplyResult.ok(entity.performAction(action), true);
            }
            if (controllerFallback.isBlank()) {
                controllerFallback = controllerActionResult.message();
            }
        }
        if (!controllerFallback.isBlank()) {
            return DirectiveApplyResult.fail(controllerFallback, true);
        }
        return DirectiveApplyResult.ok("我已经处理你的请求。", false);
    }

    private static Map<String, String> mergeDirectiveArgs(Map<String, String> goalArgs, Map<String, String> controllerArgs) {
        if ((goalArgs == null || goalArgs.isEmpty()) && (controllerArgs == null || controllerArgs.isEmpty())) {
            return Map.of();
        }
        Map<String, String> merged = new LinkedHashMap<>();
        if (goalArgs != null && !goalArgs.isEmpty()) {
            merged.putAll(goalArgs);
        }
        if (controllerArgs != null && !controllerArgs.isEmpty()) {
            merged.putAll(controllerArgs);
        }
        return Map.copyOf(merged);
    }

    private static void activateBridgeIfNeeded(AIPlayerEntity entity, String controller, String action, Map<String, String> args) {
        String normalizedController = normalize(controller);
        String normalizedAction = normalize(action);
        if (normalizedController.isBlank()) {
            normalizedController = inferControllerFromAction(normalizedAction);
        }
        if (normalizedController.isBlank() || "vision".equals(normalizedController) || "task".equals(normalizedController)) {
            return;
        }
        if ("stop".equals(normalizedAction) || "halt".equals(normalizedAction) || "canceltask".equals(normalizedAction) || "cancel_task".equals(normalizedAction)) {
            return;
        }
        int holdTicks = parsePositiveInt(args, "holdTicks", parsePositiveInt(args, "durationTicks", -1));
        if (holdTicks <= 0) {
            int seconds = parsePositiveInt(args, "holdSeconds", parsePositiveInt(args, "durationSeconds", -1));
            if (seconds > 0) {
                holdTicks = Math.min(20 * 60, seconds * 20);
            }
        }
        entity.runtimeActivateControllerBridge(normalizedController + "." + normalizedAction, holdTicks);
    }

    private static int parsePositiveInt(Map<String, String> args, String key, int fallback) {
        if (args == null || args.isEmpty() || key == null || key.isBlank()) {
            return fallback;
        }
        String raw = args.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private static String inferControllerFromAction(String action) {
        if (action == null || action.isBlank()) {
            return "";
        }
        return switch (action) {
            case "scan", "all", "scanblocks", "blocks", "scanentities", "entities", "scanhostiles", "hostiles", "scanresources", "resources", "scanstructures", "structures" -> "vision";
            case "goto", "go_to", "move_to", "follow", "follow_owner", "wander", "explore", "jump", "look", "lookat", "look_at", "input", "set_input", "wasd", "avoidobstacle", "avoid_obstacle", "recover", "stop", "halt" -> "movement";
            case "opendoor", "open_door", "open", "openchest", "open_chest", "sleepinbed", "sleep_in_bed", "sleep", "pressbutton", "press_button", "useitem", "use_item", "use" -> "interaction";
            case "mineore", "mine_ore", "choptree", "chop_tree", "mineblock", "mine_block", "cleararea", "clear_area" -> "mining";
            case "attack", "raiseshield", "raise_shield", "lowershield", "lower_shield", "retreat", "kiteenemy", "kite_enemy" -> "combat";
            case "equipbesttool", "equip_best_tool", "selectslot", "select_slot", "pickupitem", "pickup_item", "dropitem", "drop_item", "storeinchest", "store_in_chest" -> "inventory";
            case "placeblock", "place_block", "buildstructure", "build_structure", "buildbridge", "build_bridge", "buildstairs", "build_stairs" -> "building";
            case "setgoal", "set_goal", "choosetask", "choose_task", "updatetask", "update_task", "canceltask", "cancel_task" -> "task";
            default -> "";
        };
    }

    private record DirectiveApplyResult(boolean success, boolean executed, String message) {
        static DirectiveApplyResult ok(String message, boolean executed) {
            return new DirectiveApplyResult(true, executed, message == null || message.isBlank() ? "我已经处理你的请求。" : message);
        }

        static DirectiveApplyResult fail(String message, boolean executed) {
            return new DirectiveApplyResult(false, executed, message == null || message.isBlank() ? "控制指令执行失败。" : message);
        }
    }
}

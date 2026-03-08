package com.mcmod.aiplayers.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmod.aiplayers.entity.ActionTask;
import com.mcmod.aiplayers.entity.AgentBrainResponse;
import com.mcmod.aiplayers.entity.AgentGoal;
import com.mcmod.aiplayers.entity.AgentSnapshot;
import com.mcmod.aiplayers.entity.BehaviorNodeSpec;
import com.mcmod.aiplayers.entity.ConstraintSet;
import com.mcmod.aiplayers.entity.GoalType;
import com.mcmod.aiplayers.entity.AIPlayerEntity;
import com.mcmod.aiplayers.entity.SharedMemorySnapshot;
import com.mcmod.aiplayers.entity.TaskRequest;
import com.mcmod.aiplayers.entity.WorldModelSnapshot;
import com.mcmod.aiplayers.knowledge.KnowledgeManager;
import com.mcmod.aiplayers.system.AIAgentPlan;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.level.ServerPlayer;

public final class AIServiceManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "aiplayers-api.json");
    private static final String DEFAULT_PROVIDER = "qwen-compatible";
    private static final String DEFAULT_CHAT_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String DEFAULT_CHAT_MODEL = "qwen-plus";
    private static final String DEFAULT_PLANNER_MODE = "llm_primary";
    private static final String LEGACY_OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final int DEFAULT_TIMEOUT_MS = 45000;
    private static final int MIN_TIMEOUT_MS = 8000;
    private static final int MAX_TIMEOUT_MS = 180000;
    private static final int QWEN_COMPAT_MIN_TIMEOUT_MS = 18000;
    private static final int QWEN_COMPAT_MIN_PLAN_TIMEOUT_MS = 22000;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int MAX_RETRIES_LIMIT = 6;
    private static final int CONVERSATION_MAX_TOKENS = 280;
    private static final int GOAL_PLAN_MAX_TOKENS = 220;
    private static final int STATUS_BODY_SNIPPET_LIMIT = 220;
    private static volatile Config config = loadOrCreateConfig();
    private static volatile String lastStatus = "本地规则模式";

    private AIServiceManager() {
    }

    public static void initialize() {
        config = loadOrCreateConfig();
    }

    public static void reload() {
        config = loadOrCreateConfig();
    }

    public static boolean canUseConversationService() {
        return config.enabled
                && config.conversationEnabled
                && config.url != null
                && !config.url.isBlank()
                && config.conversationModel != null
                && !config.conversationModel.isBlank();
    }

    public static boolean canUseGoalPlanningService() {
        return config.enabled
                && config.taskAiEnabled
                && config.taskPlanningEnabled
                && config.url != null
                && !config.url.isBlank()
                && config.goalModel != null
                && !config.goalModel.isBlank();
    }

    public static boolean canUseBrainPlanningService() {
        return canUseGoalPlanningService();
    }

    public static boolean canUseExternalService() {
        return canUseConversationService();
    }

    public static boolean canUseTaskPlanningService() {
        return canUseGoalPlanningService();
    }

    public static int getReplanIntervalTicks() {
        return Math.max(60, config.replanIntervalSeconds * 20);
    }

    public static int getTaskAiIntervalTicks() {
        return Math.max(60, config.taskAiIntervalSeconds * 20);
    }

    public static String getPlannerMode() {
        return config.plannerMode;
    }

    public static void setEnabled(boolean enabled) {
        Config next = config.normalize();
        next.enabled = enabled;
        next.conversationEnabled = enabled;
        next.taskPlanningEnabled = enabled;
        next.taskAiEnabled = enabled;
        config = next;
        saveConfig(next);
        lastStatus = enabled ? "AI 接口已启用" : "AI 接口已关闭";
    }

    public static String getStatusSummary() {
        String endpoint = config.url == null || config.url.isBlank() ? "未配置" : config.url;
        return "AI接口：" + (config.enabled ? "已启用" : "已关闭")
                + "；提供方：" + config.provider
                + "；规划模式：" + config.plannerMode
                + "；对话：" + (config.conversationEnabled ? config.conversationModel : "关闭")
                + "；任务规划：" + (config.taskPlanningEnabled ? config.goalModel : "关闭")
                + "；超时=" + resolveRequestTimeoutMs()
                + "ms"
                + "；重试=" + config.maxRetries
                + "；地址：" + endpoint
                + "；状态：" + lastStatus;
    }

    public static String getLastStatusText() {
        return lastStatus;
    }

    public static CompletableFuture<AIGoalPlanResponse> tryPlanGoalAsync(AIPlayerEntity companion, AgentSnapshot snapshot, AgentGoal localGoal) {
        if (!canUseGoalPlanningService()) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            HttpClient client = createClient();
            HttpRequest request = buildGoalPlanningRequest(companion, snapshot, localGoal);
            lastStatus = "任务目标规划请求中";
            return sendAsyncWithRetries(client, request, "任务规划")
                    .handle((response, throwable) -> {
                        if (throwable != null) {
                            Throwable cause = throwable instanceof CompletionException completion && completion.getCause() != null
                                    ? completion.getCause()
                                    : throwable;
                            lastStatus = normalizeFailureStatus("任务规划", cause);
                            return null;
                        }
                        return parseGoalPlanHttpResponse(response);
                    });
        } catch (IllegalArgumentException ex) {
            lastStatus = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            return CompletableFuture.completedFuture(null);
        }
    }

    public static CompletableFuture<AgentBrainResponse> tryPlanBrainAsync(
            AIPlayerEntity companion,
            WorldModelSnapshot worldModel,
            SharedMemorySnapshot sharedMemory,
            TaskRequest taskRequest,
            AgentSnapshot compatibilitySnapshot) {
        if (!canUseBrainPlanningService()) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            HttpClient client = createClient();
            HttpRequest request = buildBrainPlanningRequest(companion, worldModel, sharedMemory, taskRequest, compatibilitySnapshot);
            lastStatus = "Brain 规划请求中";
            return sendAsyncWithRetries(client, request, "Brain规划")
                    .handle((response, throwable) -> {
                        if (throwable != null) {
                            Throwable cause = throwable instanceof CompletionException completion && completion.getCause() != null
                                    ? completion.getCause()
                                    : throwable;
                            lastStatus = normalizeFailureStatus("Brain规划", cause);
                            return null;
                        }
                        return parseBrainPlanHttpResponse(response);
                    });
        } catch (IllegalArgumentException ex) {
            lastStatus = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            return CompletableFuture.completedFuture(null);
        }
    }

    public static CompletableFuture<AITaskPlanResponse> tryPlanTaskAsync(AIPlayerEntity companion, AIAgentPlan localPlan) {
        AgentGoal goal = AgentGoal.of(GoalType.fromLegacyText(localPlan == null ? "survive" : localPlan.recommendedMode().commandName()), "legacy", localPlan == null ? "旧版兼容" : localPlan.goal());
        AgentSnapshot snapshot = companion.getAgentRuntimeSnapshot();
        return tryPlanGoalAsync(companion, snapshot, goal).thenApply(response -> {
            if (response == null || !response.hasPlan()) {
                return null;
            }
            GoalType resolved = response.resolveGoalType(goal.type());
            return new AITaskPlanResponse(
                    response.speechReply().isBlank() ? resolved.displayName() : response.speechReply(),
                    resolved.coarseMode().commandName(),
                    response.constraints(),
                    response.fallbackGoal().isBlank() ? "回退到本地规划" : response.fallbackGoal(),
                    response.source());
        });
    }

    public static AIServiceResponse tryRespond(AIPlayerEntity companion, ServerPlayer speaker, String message) {
        if (!canUseConversationService()) {
            lastStatus = config.enabled ? "AI 接口配置不完整" : "本地规则模式";
            return null;
        }
        int attempts = Math.max(1, config.maxRetries + 1);
        try {
            HttpClient client = createClient();
            HttpRequest request = buildConversationRequest(companion, speaker, message);
            lastStatus = "对话请求中";
            IOException lastIoEx = null;
            InterruptedException lastInterrupt = null;
            for (int attempt = 0; attempt < attempts; attempt++) {
                try {
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    return parseConversationHttpResponse(response);
                } catch (IOException ioEx) {
                    lastIoEx = ioEx;
                    if (attempt < attempts - 1 && shouldRetry(ioEx)) {
                        continue;
                    }
                    throw ioEx;
                } catch (InterruptedException interruptedEx) {
                    lastInterrupt = interruptedEx;
                    if (attempt < attempts - 1 && shouldRetry(interruptedEx)) {
                        continue;
                    }
                    throw interruptedEx;
                }
            }
            if (lastInterrupt != null) {
                throw lastInterrupt;
            }
            if (lastIoEx != null) {
                throw lastIoEx;
            }
            return null;
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            lastStatus = normalizeFailureStatus("对话", ex);
            return null;
        }
    }

    public static CompletableFuture<AIServiceResponse> tryRespondAsync(AIPlayerEntity companion, ServerPlayer speaker, String message) {
        if (!canUseConversationService()) {
            lastStatus = config.enabled ? "AI 接口配置不完整" : "本地规则模式";
            return CompletableFuture.completedFuture(null);
        }
        try {
            HttpClient client = createClient();
            HttpRequest request = buildConversationRequest(companion, speaker, message);
            lastStatus = "对话请求中";
            return sendAsyncWithRetries(client, request, "对话")
                    .handle((response, throwable) -> {
                        if (throwable != null) {
                            Throwable cause = throwable instanceof CompletionException completion && completion.getCause() != null
                                    ? completion.getCause()
                                    : throwable;
                            lastStatus = normalizeFailureStatus("对话", cause);
                            return null;
                        }
                        return parseConversationHttpResponse(response);
                    });
        } catch (IllegalArgumentException ex) {
            lastStatus = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            return CompletableFuture.completedFuture(null);
        }
    }

    private static HttpClient createClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(resolveConnectTimeoutMs()))
                .build();
    }

    private static HttpRequest buildConversationRequest(AIPlayerEntity companion, ServerPlayer speaker, String message) {
        JsonObject root = new JsonObject();
        root.addProperty("model", config.conversationModel);
        root.addProperty("temperature", 0.4D);
        root.addProperty("max_tokens", CONVERSATION_MAX_TOKENS);
        root.addProperty("stream", false);
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", buildConversationSystemPrompt());
        messages.add(system);
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", buildConversationUserPrompt(companion, speaker, message));
        messages.add(user);
        root.add("messages", messages);
        return createRequest(root, config.conversationModel, false);
    }

    private static HttpRequest buildGoalPlanningRequest(AIPlayerEntity companion, AgentSnapshot snapshot, AgentGoal localGoal) {
        JsonObject root = new JsonObject();
        root.addProperty("model", config.goalModel);
        root.addProperty("temperature", 0.2D);
        root.addProperty("max_tokens", GOAL_PLAN_MAX_TOKENS);
        root.addProperty("stream", false);
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", buildGoalPlanningSystemPrompt());
        messages.add(system);
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", buildGoalPlanningUserPrompt(companion, snapshot, localGoal));
        messages.add(user);
        root.add("messages", messages);
        return createRequest(root, config.goalModel, true);
    }

    private static HttpRequest buildBrainPlanningRequest(
            AIPlayerEntity companion,
            WorldModelSnapshot worldModel,
            SharedMemorySnapshot sharedMemory,
            TaskRequest taskRequest,
            AgentSnapshot compatibilitySnapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("model", config.goalModel);
        root.addProperty("temperature", 0.15D);
        root.addProperty("max_tokens", GOAL_PLAN_MAX_TOKENS + 260);
        root.addProperty("stream", false);
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", buildBrainPlanningSystemPrompt());
        messages.add(system);
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", buildBrainPlanningUserPrompt(companion, worldModel, sharedMemory, taskRequest, compatibilitySnapshot));
        messages.add(user);
        root.add("messages", messages);
        return createRequest(root, config.goalModel, true);
    }

    private static HttpRequest createRequest(JsonObject root, String model, boolean planningRequest) {
        String endpoint = resolveApiEndpoint(config.url);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMillis(resolveRequestTimeoutMs(model, planningRequest)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(root), StandardCharsets.UTF_8));
        if (config.apiKey != null && !config.apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + config.apiKey);
        }
        return builder.build();
    }

    private static String resolveApiEndpoint(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return DEFAULT_CHAT_URL;
        }
        String trimmed = rawUrl.trim();
        String normalized = trimmed.toLowerCase();
        if (normalized.endsWith("/chat/completions") || normalized.endsWith("/completions")) {
            return trimmed;
        }
        if (normalized.endsWith("/v1")) {
            return trimmed + "/chat/completions";
        }
        if (normalized.endsWith("/v1/")) {
            return trimmed + "chat/completions";
        }
        return trimmed;
    }

    private static CompletableFuture<HttpResponse<String>> sendAsyncWithRetries(HttpClient client, HttpRequest request, String scope) {
        int retries = Math.max(0, config.maxRetries);
        CompletableFuture<HttpResponse<String>> result = new CompletableFuture<>();
        sendAsyncAttempt(client, request, scope, 0, retries, result);
        return result;
    }

    private static void sendAsyncAttempt(HttpClient client, HttpRequest request, String scope, int attempt, int maxRetries, CompletableFuture<HttpResponse<String>> result) {
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, throwable) -> {
                    if (throwable == null) {
                        if (response != null && isRetryableStatus(response.statusCode()) && attempt < maxRetries) {
                            int nextAttempt = attempt + 1;
                            lastStatus = scope + "返回可重试状态 HTTP " + response.statusCode() + "，重试 " + nextAttempt + "/" + maxRetries;
                            long backoffMs = retryDelayMs(nextAttempt);
                            CompletableFuture.delayedExecutor(backoffMs, TimeUnit.MILLISECONDS)
                                    .execute(() -> sendAsyncAttempt(client, request, scope, nextAttempt, maxRetries, result));
                            return;
                        }
                        result.complete(response);
                        return;
                    }
                    Throwable cause = throwable instanceof CompletionException completion && completion.getCause() != null
                            ? completion.getCause()
                            : throwable;
                    if (attempt < maxRetries && shouldRetry(cause)) {
                        int nextAttempt = attempt + 1;
                        lastStatus = scope + "超时/网络抖动，重试 " + nextAttempt + "/" + maxRetries;
                        long backoffMs = retryDelayMs(nextAttempt);
                        CompletableFuture.delayedExecutor(backoffMs, TimeUnit.MILLISECONDS)
                                .execute(() -> sendAsyncAttempt(client, request, scope, nextAttempt, maxRetries, result));
                        return;
                    }
                    result.completeExceptionally(cause);
                });
    }

    private static boolean shouldRetry(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        if (throwable instanceof HttpTimeoutException
                || throwable instanceof SocketTimeoutException
                || throwable instanceof ConnectException) {
            return true;
        }
        if (throwable.getCause() != null && throwable.getCause() != throwable && shouldRetry(throwable.getCause())) {
            return true;
        }
        String message = throwable.getMessage() == null ? "" : throwable.getMessage().toLowerCase();
        return message.contains("timeout")
                || message.contains("timed out")
                || message.contains("connection reset")
                || message.contains("connectexception")
                || message.contains("closedchannel");
    }

    private static String normalizeFailureStatus(String scope, Throwable throwable) {
        if (throwable == null) {
            return scope + "失败";
        }
        if (throwable instanceof HttpTimeoutException || throwable instanceof SocketTimeoutException) {
            return scope + "超时（请求超时 " + resolveRequestTimeoutMs() + "ms），已回退本地";
        }
        String message = throwable.getMessage();
        String normalized = message == null ? "" : message.toLowerCase();
        if (normalized.contains("timeout") || normalized.contains("timed out")) {
            return scope + "超时（请求超时 " + resolveRequestTimeoutMs() + "ms），已回退本地";
        }
        return throwable.getClass().getSimpleName() + ": " + (message == null ? "未知错误" : message);
    }

    private static String buildConversationSystemPrompt() {
        return "你是 Minecraft 模组中的 AI 玩家同伴。"
                + "请只返回 JSON，对象字段为 reply、goalType、goalArgs、action，且可选 controller、controllerAction、controllerArgs。"
                + "reply 是对玩家的简短中文回复。"
                + "goalType 可选：idle,follow_owner,guard_owner,survive,collect_wood,collect_ore,collect_food,build_shelter,deliver_item,explore_area,recover_self,talk_only。"
                + "goalArgs 是字符串键值对象；action 可选：none,jump,crouch,stand,look_up,look_down,look_owner。"
                + "controller 可选：vision,movement,interaction,mining,combat,inventory,building,task。"
                + "controllerAction 对应控制器动作；controllerArgs 是字符串键值对象。"
                + "如果只是聊天，请把 goalType 设为空字符串，action 设为 none。"
                + "不要虚构不存在的物品、地点和能力。";
    }

    private static String buildGoalPlanningSystemPrompt() {
        return "你是 Minecraft AI 同伴的高层目标规划器。"
                + "请只返回 JSON，对象字段必须为 goalType、goalArgs、priority、constraints、fallbackGoal、speechReply。"
                + "goalType 只能从 idle,follow_owner,guard_owner,survive,collect_wood,collect_ore,collect_food,build_shelter,deliver_item,explore_area,recover_self,talk_only 中选择。"
                + "goalArgs 是字符串对象；priority 是 0 到 100 的整数；constraints 是字符串数组。"
                + "fallbackGoal 是回退目标；speechReply 是给玩家的简短中文解释。"
                + "你只能做高层目标选择，不能假设拥有未实现的能力。";
    }

    private static String buildBrainPlanningSystemPrompt() {
        return "你是 Minecraft AI 同伴的 Agent Brain。"
                + "请只返回 JSON。"
                + "顶层字段必须包含 task、priority、constraints、subtree、fallbackTask、reasoning。"
                + "task 和 fallbackTask 是对象，字段为 taskType、label、args、priority。"
                + "taskType 只能从 idle,follow_owner,guard_owner,survive,collect_wood,collect_ore,collect_food,build_shelter,deliver_item,explore_area,recover_self,talk_only 中选择。"
                + "subtree 是行为树节点，对象字段为 type、label、condition、action、children、args、timeoutTicks。"
                + "允许的 type 只有 selector、sequence、condition、action、repeat_until、timeout。"
                + "action 节点的 action 对象字段为 actionType、label、args。"
                + "允许的 actionType 只有 move_to、look_at、equip_tool、mine_block、chop_tree、collect_drops、attack_target、place_block、bridge、tunnel、open_container、consume_food、recover_self、observe、harvest_crop、deliver_item。"
                + "不要输出未定义字段，不要假设模组不存在的能力，不要直接下发任意 controller 指令。";
    }

    private static String buildConversationUserPrompt(AIPlayerEntity companion, ServerPlayer speaker, String message) {
        return "玩家消息：" + message + "\n"
                + "玩家名：" + speaker.getName().getString() + "\n"
                + "AI 名称：" + companion.getAIName() + "\n"
                + "AI当前动作：" + companion.getCurrentActionSummary() + "\n"
                + "状态摘要：" + companion.getStatusSummary() + "\n"
                + "观察摘要：" + companion.getObservationSummary() + "\n"
                + "主人完整信息：" + companion.getOwnerDetailedContextForAi() + "\n"
                + "AI自身完整信息：" + companion.getSelfDetailedContextForAi() + "\n"
                + "局部20x20x20对象（名称+世界坐标）：" + companion.getLocalWorldObjectsContextForAi() + "\n"
                + "知识库摘要：" + KnowledgeManager.getStatusSummary() + "\n"
                + "合成链提示：" + KnowledgeManager.getCraftingHintSummary() + "\n"
                + "生物弱点提示：" + KnowledgeManager.getMobKnowledgeSummary() + "\n"
                + "控制器能力：" + companion.getControllerCapabilitySummary() + "\n"
                + "计划摘要：" + companion.getPlanSummary() + "\n"
                + "长期记忆：" + companion.getLongTermMemorySummary();
    }

    private static String buildGoalPlanningUserPrompt(AIPlayerEntity companion, AgentSnapshot snapshot, AgentGoal localGoal) {
        WorldSummary summary = WorldSummary.from(snapshot);
        return "AI 名称：" + companion.getAIName() + "\n"
                + "本地目标：" + localGoal.type().commandName() + "\n"
                + "当前目标：" + snapshot.currentGoal().type().commandName() + "\n"
                + "当前动作：" + snapshot.currentAction() + "\n"
                + "路径状态：" + snapshot.pathStatus() + "\n"
                + "主人可用：" + summary.ownerAvailable + "\n"
                + "主人距离：" + summary.ownerDistance + "\n"
                + "危险：" + summary.inHazard + "\n"
                + "食物偏低：" + summary.lowFood + "\n"
                + "工具偏低：" + summary.lowTools + "\n"
                + "夜晚：" + summary.night + "\n"
                + "建材单位：" + summary.buildingUnits + "\n"
                + "观察：" + summary.observation + "\n"
                + "局部认知：" + summary.cognition + "\n"
                + "背包：" + summary.inventory + "\n"
                + "合成链提示：" + KnowledgeManager.getCraftingHintSummary() + "\n"
                + "生物弱点提示：" + KnowledgeManager.getMobKnowledgeSummary() + "\n"
                + "最近失败：" + snapshot.memory().lastFailure() + "\n"
                + "最近学习：" + snapshot.memory().lastLearning() + "\n"
                + "团队知识：" + TeamSummary.safe(companion);
    }

    private static String buildBrainPlanningUserPrompt(
            AIPlayerEntity companion,
            WorldModelSnapshot worldModel,
            SharedMemorySnapshot sharedMemory,
            TaskRequest taskRequest,
            AgentSnapshot compatibilitySnapshot) {
        return "AI 名称：" + companion.getAIName() + "\n"
                + "请求任务：" + (taskRequest == null ? "idle" : taskRequest.taskType()) + "\n"
                + "请求标签：" + (taskRequest == null ? "待命" : taskRequest.label()) + "\n"
                + "WorldModel 摘要：" + (worldModel == null ? "无" : worldModel.summary()) + "\n"
                + "观察：" + (worldModel == null ? "无" : worldModel.observation()) + "\n"
                + "认知：" + (worldModel == null ? "无" : worldModel.cognition()) + "\n"
                + "资源：" + formatSpatialFacts(worldModel == null ? List.of() : worldModel.resources()) + "\n"
                + "结构：" + formatSpatialFacts(worldModel == null ? List.of() : worldModel.structures()) + "\n"
                + "危险：" + formatSpatialFacts(worldModel == null ? List.of() : worldModel.dangers()) + "\n"
                + "附近实体：" + formatEntityFacts(worldModel == null ? List.of() : worldModel.nearbyEntities()) + "\n"
                + "库存：" + formatInventoryFacts(worldModel == null ? List.of() : worldModel.inventory()) + "\n"
                + "装备：" + formatEquipmentFacts(worldModel == null ? List.of() : worldModel.equipment()) + "\n"
                + "导航：" + (worldModel == null ? "无" : worldModel.navigation().status()) + "\n"
                + "共享记忆：" + (sharedMemory == null ? "无" : sharedMemory.summary()) + "\n"
                + "共享失败：" + (sharedMemory == null ? List.of() : sharedMemory.recentFailures()) + "\n"
                + "共享任务结果：" + (sharedMemory == null ? List.of() : sharedMemory.taskOutcomes()) + "\n"
                + "兼容快照目标：" + (compatibilitySnapshot == null ? "无" : compatibilitySnapshot.currentGoal().type().commandName()) + "\n"
                + "兼容快照动作：" + (compatibilitySnapshot == null ? "无" : compatibilitySnapshot.currentAction()) + "\n"
                + "路径状态：" + (compatibilitySnapshot == null ? "无" : compatibilitySnapshot.pathStatus()) + "\n"
                + "知识库摘要：" + KnowledgeManager.getStatusSummary() + "\n"
                + "合成链提示：" + KnowledgeManager.getCraftingHintSummary() + "\n"
                + "生物弱点提示：" + KnowledgeManager.getMobKnowledgeSummary();
    }

    private static String formatSpatialFacts(List<WorldModelSnapshot.SpatialFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return "无";
        }
        return facts.stream()
                .limit(8)
                .map(fact -> fact.kind() + "@" + formatPos(fact.pos()))
                .toList()
                .toString();
    }

    private static String formatEntityFacts(List<WorldModelSnapshot.EntityFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return "无";
        }
        return facts.stream()
                .limit(8)
                .map(fact -> fact.kind() + ":" + fact.label() + "@" + formatPos(fact.pos()))
                .toList()
                .toString();
    }

    private static String formatInventoryFacts(List<WorldModelSnapshot.InventoryFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return "空";
        }
        return facts.stream().limit(12).map(fact -> fact.label() + "x" + fact.count()).toList().toString();
    }

    private static String formatEquipmentFacts(List<WorldModelSnapshot.EquipmentFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return "空";
        }
        return facts.stream().limit(8).map(fact -> fact.slot() + "=" + fact.label()).toList().toString();
    }

    private static String formatPos(net.minecraft.core.BlockPos pos) {
        return pos == null ? "?" : (pos.getX() + "," + pos.getY() + "," + pos.getZ());
    }

    private static AIServiceResponse parseConversationHttpResponse(HttpResponse<String> response) {
        if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
            if (response == null) {
                lastStatus = "对话 HTTP null";
            } else {
                lastStatus = "对话 HTTP " + response.statusCode() + "：" + summarizeBody(response.body());
            }
            return null;
        }
        AIServiceResponse parsed = parseConversationResponse(response.body());
        lastStatus = parsed != null ? "最近一次对话调用成功" : "对话响应解析失败";
        return parsed;
    }

    private static AIGoalPlanResponse parseGoalPlanHttpResponse(HttpResponse<String> response) {
        if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
            if (response == null) {
                lastStatus = "任务规划 HTTP null";
            } else {
                lastStatus = "任务规划 HTTP " + response.statusCode() + "：" + summarizeBody(response.body());
            }
            return null;
        }
        AIGoalPlanResponse parsed = parseGoalPlanResponse(response.body());
        lastStatus = parsed != null ? "最近一次目标规划成功" : "目标规划解析失败";
        return parsed;
    }

    private static AIServiceResponse parseConversationResponse(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            String content = extractAssistantContent(root);
            if (content != null && !content.isBlank()) {
                return parseAssistantContent(extractJsonCandidate(content));
            }
            if (looksLikeAssistantDirectiveRoot(root)) {
                return parseAssistantContent(root.toString());
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static AIGoalPlanResponse parseGoalPlanResponse(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            String content = extractAssistantContent(root);
            if (content != null && !content.isBlank()) {
                JsonObject object = JsonParser.parseString(extractJsonCandidate(content)).getAsJsonObject();
                return parseGoalPlanObject(object);
            }
            if (looksLikeGoalPlanRoot(root)) {
                return parseGoalPlanObject(root);
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static AgentBrainResponse parseBrainPlanHttpResponse(HttpResponse<String> response) {
        if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
            if (response == null) {
                lastStatus = "Brain规划无响应";
            } else {
                lastStatus = "Brain规划 HTTP " + response.statusCode();
            }
            return null;
        }
        AgentBrainResponse parsed = parseBrainPlanResponse(response.body());
        if (parsed == null) {
            lastStatus = "Brain规划返回体无法解析";
        } else {
            lastStatus = "Brain规划成功";
        }
        return parsed;
    }

    private static AgentBrainResponse parseBrainPlanResponse(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            String content = extractAssistantContent(root);
            if (content != null && !content.isBlank()) {
                JsonObject object = JsonParser.parseString(extractJsonCandidate(content)).getAsJsonObject();
                return parseBrainPlanObject(object);
            }
            if (looksLikeBrainPlanRoot(root)) {
                return parseBrainPlanObject(root);
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static AgentBrainResponse parseBrainPlanObject(JsonObject object) {
        if (object == null) {
            return null;
        }
        TaskRequest task = parseTaskRequest(firstJsonObject(object, "task"));
        if (task == null) {
            task = new TaskRequest(
                    firstNonBlank(getString(object, "taskType"), getString(object, "goalType"), getString(object, "goal")),
                    firstNonBlank(getString(object, "label"), getString(object, "reasoning"), getString(object, "goal")),
                    parseStringMap(firstJsonObject(object, "taskArgs", "goalArgs", "args")),
                    parseInt(object.get("priority"), 50),
                    false,
                    "brain-api",
                    firstNonBlank(getString(object, "fallbackTask"), getString(object, "fallbackGoal"), GoalType.SURVIVE.commandName()));
        }
        TaskRequest fallbackTask = parseTaskRequest(firstJsonObject(object, "fallbackTask"));
        if (fallbackTask == null) {
            fallbackTask = new TaskRequest(
                    firstNonBlank(getString(object, "fallbackGoal"), GoalType.SURVIVE.commandName()),
                    firstNonBlank(getString(object, "fallbackGoal"), "fallback"),
                    Map.of(),
                    20,
                    false,
                    "brain-api",
                    GoalType.SURVIVE.commandName());
        }
        ConstraintSet constraints = new ConstraintSet(parseStringList(object.getAsJsonArray("constraints")), parseStringMap(firstJsonObject(object, "constraintMeta", "constraintArgs", "constraintMetadata")));
        BehaviorNodeSpec subtree = parseBehaviorNodeSpec(firstJsonObject(object, "subtree", "tree", "behaviorTree"));
        if (subtree == null) {
            subtree = BehaviorNodeSpec.action(task.label(), mapGoalToDefaultAction(task.goalType()), task.args());
        }
        return new AgentBrainResponse(
                task,
                parseInt(object.get("priority"), task.priority()),
                constraints,
                subtree,
                fallbackTask,
                firstNonBlank(getString(object, "reasoning"), getString(object, "speechReply"), task.label()),
                "brain-api");
    }

    private static TaskRequest parseTaskRequest(JsonObject object) {
        if (object == null) {
            return null;
        }
        String taskType = firstNonBlank(getString(object, "taskType"), getString(object, "goalType"), getString(object, "goal"));
        if (taskType == null || taskType.isBlank()) {
            return null;
        }
        return new TaskRequest(
                taskType,
                firstNonBlank(getString(object, "label"), getString(object, "reasoning"), taskType),
                parseStringMap(firstJsonObject(object, "args", "taskArgs", "goalArgs")),
                parseInt(object.get("priority"), 50),
                false,
                firstNonBlank(getString(object, "source"), "brain-api"),
                firstNonBlank(getString(object, "fallbackTask"), getString(object, "fallbackGoal"), GoalType.SURVIVE.commandName()));
    }

    private static BehaviorNodeSpec parseBehaviorNodeSpec(JsonObject object) {
        if (object == null) {
            return null;
        }
        JsonArray childrenArray = object.getAsJsonArray("children");
        List<BehaviorNodeSpec> children = new ArrayList<>();
        if (childrenArray != null) {
            for (JsonElement element : childrenArray) {
                if (element != null && element.isJsonObject()) {
                    BehaviorNodeSpec child = parseBehaviorNodeSpec(element.getAsJsonObject());
                    if (child != null) {
                        children.add(child);
                    }
                }
            }
        }
        ActionTask action = parseActionTask(firstJsonObject(object, "action"));
        String type = defaultString(getString(object, "type"), action == null ? "sequence" : "action");
        return new BehaviorNodeSpec(
                getString(object, "id"),
                type,
                firstNonBlank(getString(object, "label"), getString(object, "name"), type),
                defaultString(getString(object, "condition"), ""),
                action,
                children,
                parseStringMap(firstJsonObject(object, "args")),
                parseInt(object.get("timeoutTicks"), 0));
    }

    private static ActionTask parseActionTask(JsonObject object) {
        if (object == null) {
            return null;
        }
        String actionType = firstNonBlank(getString(object, "actionType"), getString(object, "type"), getString(object, "name"));
        if (actionType == null || actionType.isBlank()) {
            return null;
        }
        return new ActionTask(actionType, firstNonBlank(getString(object, "label"), actionType), parseStringMap(firstJsonObject(object, "args")));
    }

    private static List<String> parseStringList(JsonArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (element == null || element.isJsonNull()) {
                continue;
            }
            values.add(element.getAsString());
        }
        return values;
    }

    private static String mapGoalToDefaultAction(GoalType goalType) {
        if (goalType == null) {
            return "observe";
        }
        return switch (goalType) {
            case FOLLOW_OWNER -> "move_to";
            case GUARD_OWNER -> "attack_target";
            case COLLECT_WOOD -> "chop_tree";
            case COLLECT_ORE -> "mine_block";
            case COLLECT_FOOD -> "harvest_crop";
            case BUILD_SHELTER -> "place_block";
            case DELIVER_ITEM -> "deliver_item";
            case RECOVER_SELF -> "recover_self";
            case EXPLORE_AREA, SURVIVE, TALK_ONLY, IDLE -> "observe";
        };
    }

    private static String extractAssistantContent(JsonObject root) {
        if (root == null) {
            return null;
        }
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices != null && !choices.isEmpty()) {
            JsonObject choice = choices.get(0).getAsJsonObject();
            String messageContent = extractMessageContent(choice.get("message"));
            if (messageContent != null && !messageContent.isBlank()) {
                return messageContent;
            }
            String text = getString(choice, "text");
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        String outputText = getString(root, "output_text");
        if (outputText != null && !outputText.isBlank()) {
            return outputText;
        }
        return extractMessageContent(root.get("message"));
    }

    private static String extractMessageContent(JsonElement messageElement) {
        if (messageElement == null || messageElement.isJsonNull()) {
            return null;
        }
        if (messageElement.isJsonPrimitive()) {
            try {
                return messageElement.getAsString();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        if (!messageElement.isJsonObject()) {
            return null;
        }
        JsonObject message = messageElement.getAsJsonObject();
        JsonElement contentElement = message.get("content");
        if (contentElement == null || contentElement.isJsonNull()) {
            return getString(message, "text");
        }
        if (contentElement.isJsonPrimitive()) {
            return contentElement.getAsString();
        }
        if (contentElement.isJsonObject()) {
            JsonObject contentObject = contentElement.getAsJsonObject();
            String text = getString(contentObject, "text");
            if (text != null && !text.isBlank()) {
                return text;
            }
            JsonElement inner = contentObject.get("content");
            if (inner != null && inner.isJsonPrimitive()) {
                return inner.getAsString();
            }
            return null;
        }
        if (!contentElement.isJsonArray()) {
            return null;
        }
        StringBuilder merged = new StringBuilder();
        for (JsonElement entry : contentElement.getAsJsonArray()) {
            if (entry == null || entry.isJsonNull()) {
                continue;
            }
            String piece = null;
            if (entry.isJsonPrimitive()) {
                piece = entry.getAsString();
            } else if (entry.isJsonObject()) {
                JsonObject object = entry.getAsJsonObject();
                piece = firstNonBlank(getString(object, "text"), getString(object, "content"), getString(object, "value"));
            }
            if (piece != null && !piece.isBlank()) {
                if (merged.length() > 0) {
                    merged.append('\n');
                }
                merged.append(piece.trim());
            }
        }
        return merged.length() == 0 ? null : merged.toString();
    }

    private static boolean looksLikeAssistantDirectiveRoot(JsonObject root) {
        if (root == null) {
            return false;
        }
        return root.has("reply")
                || root.has("goalType")
                || root.has("mode")
                || root.has("action")
                || root.has("controller")
                || root.has("controllerAction")
                || root.has("controllerArgs")
                || root.has("controller_args");
    }

    private static boolean looksLikeBrainPlanRoot(JsonObject root) {
        if (root == null) {
            return false;
        }
        return root.has("task")
                || root.has("taskType")
                || root.has("subtree")
                || root.has("behaviorTree")
                || root.has("fallbackTask")
                || root.has("reasoning");
    }

    private static boolean looksLikeGoalPlanRoot(JsonObject root) {
        if (root == null) {
            return false;
        }
        return root.has("goalType")
                || root.has("goalArgs")
                || root.has("priority")
                || root.has("constraints")
                || root.has("fallbackGoal")
                || root.has("speechReply");
    }

    private static AIServiceResponse parseAssistantContent(String content) {
        try {
            JsonObject object = JsonParser.parseString(content).getAsJsonObject();
            JsonObject controllerArgsObject = firstJsonObject(
                    object,
                    "controllerArgs",
                    "controller_args",
                    "controllerParams",
                    "params");
            String controller = firstNonBlank(
                    getString(object, "controller"),
                    getString(object, "controllerType"),
                    getString(object, "capability"));
            String controllerAction = firstNonBlank(
                    getString(object, "controllerAction"),
                    getString(object, "operation"),
                    getString(object, "controllerOp"));
            return new AIServiceResponse(
                    defaultString(getString(object, "reply"), ""),
                    defaultString(getString(object, "mode"), "unchanged"),
                    defaultString(getString(object, "goalType"), ""),
                    parseStringMap(object.getAsJsonObject("goalArgs")),
                    defaultString(getString(object, "action"), "none"),
                    "api",
                    defaultString(controller, ""),
                    defaultString(controllerAction, ""),
                    parseStringMap(controllerArgsObject));
        } catch (RuntimeException ex) {
            return new AIServiceResponse(content, "unchanged", "", Map.of(), "none", "api", "", "", Map.of());
        }
    }

    private static String extractJsonCandidate(String content) {
        if (content == null || content.isBlank()) {
            return "{}";
        }
        String trimmed = content.trim();
        int fencedStart = trimmed.indexOf("```");
        if (fencedStart >= 0) {
            int firstLineEnd = trimmed.indexOf('\n', fencedStart + 3);
            int fencedEnd = trimmed.lastIndexOf("```");
            if (firstLineEnd > fencedStart && fencedEnd > firstLineEnd) {
                String fenced = trimmed.substring(firstLineEnd + 1, fencedEnd).trim();
                if (fenced.startsWith("{") && fenced.endsWith("}")) {
                    return fenced;
                }
            }
        }
        int jsonStart = trimmed.indexOf('{');
        int jsonEnd = trimmed.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return trimmed.substring(jsonStart, jsonEnd + 1);
        }
        return trimmed;
    }

    private static AIGoalPlanResponse parseGoalPlanObject(JsonObject object) {
        if (object == null) {
            return null;
        }
        List<String> constraints = new ArrayList<>();
        JsonArray array = object.getAsJsonArray("constraints");
        if (array != null) {
            for (JsonElement element : array) {
                if (element != null && !element.isJsonNull()) {
                    String value = element.getAsString();
                    if (value != null && !value.isBlank()) {
                        constraints.add(value.trim());
                    }
                }
            }
        }
        String goalType = defaultString(getString(object, "goalType"), "");
        if (goalType.isBlank()) {
            goalType = defaultString(getString(object, "mode"), defaultString(getString(object, "goal"), ""));
        }
        return new AIGoalPlanResponse(
                goalType,
                parseStringMap(object.getAsJsonObject("goalArgs")),
                parseInt(object.get("priority"), 60),
                constraints,
                defaultString(getString(object, "fallbackGoal"), defaultString(getString(object, "fallback"), "survive")),
                defaultString(getString(object, "speechReply"), defaultString(getString(object, "goal"), "")),
                "goal-api");
    }

    private static Map<String, String> parseStringMap(JsonObject object) {
        if (object == null) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isJsonNull()) {
                values.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return values;
    }

    private static JsonObject firstJsonObject(JsonObject root, String... keys) {
        if (root == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            JsonElement element = root.get(key);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        }
        return null;
    }

    private static int parseInt(JsonElement element, int fallback) {
        try {
            return element == null || element.isJsonNull() ? fallback : element.getAsInt();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static String getString(JsonObject object, String key) {
        if (object == null) {
            return null;
        }
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static int resolveRequestTimeoutMs() {
        int conversationTimeout = resolveRequestTimeoutMs(config.conversationModel, false);
        int planningTimeout = resolveRequestTimeoutMs(config.goalModel, true);
        return Math.max(conversationTimeout, planningTimeout);
    }

    private static int resolveRequestTimeoutMs(String model, boolean planningRequest) {
        int timeout = Math.max(MIN_TIMEOUT_MS, Math.min(config.timeoutMs, MAX_TIMEOUT_MS));
        if (isQwenCompatibleProvider(config.provider, config.url)) {
            int timeoutFloor = planningRequest ? QWEN_COMPAT_MIN_PLAN_TIMEOUT_MS : QWEN_COMPAT_MIN_TIMEOUT_MS;
            if (isLikelySlowModel(model)) {
                timeoutFloor += 4000;
            }
            timeout = Math.max(timeout, timeoutFloor);
        }
        return timeout;
    }

    private static int resolveConnectTimeoutMs() {
        int requestTimeout = Math.max(
                resolveRequestTimeoutMs(config.conversationModel, false),
                resolveRequestTimeoutMs(config.goalModel, true));
        int connect = Math.max(3000, requestTimeout / 2);
        return Math.min(connect, 30000);
    }

    private static boolean isQwenCompatibleProvider(String provider, String url) {
        String normalizedProvider = provider == null ? "" : provider.toLowerCase();
        if (normalizedProvider.contains("qwen")) {
            return true;
        }
        return isDashScopeEndpoint(url);
    }

    private static boolean isDashScopeEndpoint(String url) {
        String normalizedUrl = url == null ? "" : url.toLowerCase();
        return normalizedUrl.contains("dashscope.aliyuncs.com")
                && normalizedUrl.contains("compatible-mode");
    }

    private static boolean isLikelySlowModel(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        String normalizedModel = model.toLowerCase();
        return normalizedModel.contains("qwen3.5-plus")
                || normalizedModel.contains("qwen-plus")
                || normalizedModel.contains("qwen-max");
    }

    private static long retryDelayMs(int attempt) {
        return Math.min(5000L, 450L * attempt + 300L);
    }

    private static boolean isRetryableStatus(int statusCode) {
        return statusCode == 408
                || statusCode == 409
                || statusCode == 425
                || statusCode == 429
                || statusCode == 500
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504;
    }

    private static String summarizeBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String compact = body.replace('\n', ' ').replace('\r', ' ').trim();
        if (compact.length() <= STATUS_BODY_SNIPPET_LIMIT) {
            return compact;
        }
        return compact.substring(0, STATUS_BODY_SNIPPET_LIMIT) + "...";
    }

    private static Config loadOrCreateConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (!Files.exists(CONFIG_PATH)) {
                Config template = Config.createDefault();
                saveConfig(template);
                lastStatus = "已生成默认配置文件：" + CONFIG_PATH;
                return template;
            }
            String content = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            Config loaded = GSON.fromJson(content, Config.class);
            if (loaded == null) {
                return Config.createDefault();
            }
            Config normalized = loaded.normalize();
            if (!GSON.toJson(normalized).equals(content)) {
                saveConfig(normalized);
            }
            return normalized;
        } catch (IOException ex) {
            lastStatus = "读取配置失败：" + ex.getMessage();
            return Config.createDefault();
        }
    }

    private static void saveConfig(Config value) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(value.normalize()), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            lastStatus = "保存配置失败：" + ex.getMessage();
        }
    }

    private static final class Config {
        private boolean enabled;
        private boolean taskAiEnabled;
        private int taskAiIntervalSeconds;
        private String provider;
        private String url;
        private String apiKey;
        private String model;
        private int timeoutMs;
        private String plannerMode;
        private boolean conversationEnabled;
        private boolean taskPlanningEnabled;
        private String conversationModel;
        private String goalModel;
        private int replanIntervalSeconds;
        private int maxRetries;

        private static Config createDefault() {
            Config config = new Config();
            config.enabled = false;
            config.taskAiEnabled = false;
            config.taskAiIntervalSeconds = 5;
            config.provider = DEFAULT_PROVIDER;
            config.url = DEFAULT_CHAT_URL;
            config.apiKey = "";
            config.model = DEFAULT_CHAT_MODEL;
            config.timeoutMs = DEFAULT_TIMEOUT_MS;
            config.plannerMode = DEFAULT_PLANNER_MODE;
            config.conversationEnabled = false;
            config.taskPlanningEnabled = false;
            config.conversationModel = DEFAULT_CHAT_MODEL;
            config.goalModel = DEFAULT_CHAT_MODEL;
            config.replanIntervalSeconds = 5;
            config.maxRetries = DEFAULT_MAX_RETRIES;
            return config;
        }

        private Config normalize() {
            if (this.provider == null || this.provider.isBlank() || "openai-compatible".equalsIgnoreCase(this.provider)) {
                this.provider = DEFAULT_PROVIDER;
            }
            if (this.url == null || this.url.isBlank() || LEGACY_OPENAI_CHAT_URL.equalsIgnoreCase(this.url)) {
                this.url = DEFAULT_CHAT_URL;
            }
            if (this.apiKey == null) {
                this.apiKey = "";
            }
            if (this.model == null || this.model.isBlank()) {
                this.model = DEFAULT_CHAT_MODEL;
            }
            if (this.timeoutMs <= 0) {
                this.timeoutMs = DEFAULT_TIMEOUT_MS;
            } else {
                this.timeoutMs = Math.max(MIN_TIMEOUT_MS, Math.min(this.timeoutMs, MAX_TIMEOUT_MS));
            }
            if (this.taskAiIntervalSeconds <= 0) {
                this.taskAiIntervalSeconds = 5;
            }
            if (this.plannerMode == null || this.plannerMode.isBlank()) {
                this.plannerMode = DEFAULT_PLANNER_MODE;
            }
            if (this.conversationModel == null || this.conversationModel.isBlank()) {
                this.conversationModel = this.model;
            }
            if (this.goalModel == null || this.goalModel.isBlank()) {
                this.goalModel = this.model;
            }
            if (isQwenCompatibleProvider(this.provider, this.url)) {
                int providerFloor = isLikelySlowModel(this.goalModel) || isLikelySlowModel(this.conversationModel)
                        ? QWEN_COMPAT_MIN_PLAN_TIMEOUT_MS
                        : QWEN_COMPAT_MIN_TIMEOUT_MS;
                this.timeoutMs = Math.max(this.timeoutMs, providerFloor);
            }
            if (this.replanIntervalSeconds <= 0) {
                this.replanIntervalSeconds = this.taskAiIntervalSeconds > 0 ? this.taskAiIntervalSeconds : 5;
            }
            if (this.maxRetries < 0) {
                this.maxRetries = DEFAULT_MAX_RETRIES;
            } else if (this.maxRetries > MAX_RETRIES_LIMIT) {
                this.maxRetries = MAX_RETRIES_LIMIT;
            }
            if (!this.conversationEnabled && this.enabled) {
                this.conversationEnabled = true;
            }
            if (!this.taskPlanningEnabled && this.enabled) {
                this.taskPlanningEnabled = true;
            }
            if (!this.taskPlanningEnabled && this.taskAiEnabled) {
                this.taskPlanningEnabled = true;
            }
            return this;
        }
    }

    private record WorldSummary(boolean ownerAvailable, double ownerDistance, boolean inHazard, boolean lowFood, boolean lowTools, boolean night, int buildingUnits, String observation, String inventory, String cognition) {
        private static WorldSummary from(AgentSnapshot snapshot) {
            return new WorldSummary(
                    snapshot.worldState().ownerAvailable(),
                    snapshot.worldState().ownerDistance(),
                    snapshot.worldState().inHazard(),
                    snapshot.worldState().lowFood(),
                    snapshot.worldState().lowTools(),
                    snapshot.worldState().night(),
                    snapshot.worldState().buildingUnits(),
                    snapshot.worldState().observation(),
                    snapshot.worldState().inventory(),
                    snapshot.worldState().cognition());
        }
    }

    private static final class TeamSummary {
        private static String safe(AIPlayerEntity entity) {
            return entity.getLongTermMemorySummary();
        }
    }
}

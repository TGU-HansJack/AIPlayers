package com.mcmod.aiplayers.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmod.aiplayers.entity.AgentGoal;
import com.mcmod.aiplayers.entity.AgentSnapshot;
import com.mcmod.aiplayers.entity.GoalType;
import com.mcmod.aiplayers.entity.AIPlayerEntity;
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
        return config.conversationEnabled
                && config.url != null
                && !config.url.isBlank()
                && config.conversationModel != null
                && !config.conversationModel.isBlank();
    }

    public static boolean canUseGoalPlanningService() {
        return config.taskPlanningEnabled
                && config.url != null
                && !config.url.isBlank()
                && config.goalModel != null
                && !config.goalModel.isBlank();
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
        return getReplanIntervalTicks();
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
        return createRequest(root);
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
        return createRequest(root);
    }

    private static HttpRequest createRequest(JsonObject root) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.url))
                .timeout(Duration.ofMillis(resolveRequestTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(root), StandardCharsets.UTF_8));
        if (config.apiKey != null && !config.apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + config.apiKey);
        }
        return builder.build();
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
                        long backoffMs = Math.min(2000L, 400L * nextAttempt);
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
                + "请只返回 JSON，对象字段为 reply、goalType、goalArgs、action。"
                + "reply 是对玩家的简短中文回复。"
                + "goalType 可选：idle,follow_owner,guard_owner,survive,collect_wood,collect_ore,collect_food,build_shelter,deliver_item,explore_area,recover_self,talk_only。"
                + "goalArgs 是字符串键值对象；action 可选：none,jump,crouch,stand,look_up,look_down,look_owner。"
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

    private static String buildConversationUserPrompt(AIPlayerEntity companion, ServerPlayer speaker, String message) {
        return "玩家消息：" + message + "\n"
                + "玩家名：" + speaker.getName().getString() + "\n"
                + "AI 名称：" + companion.getAIName() + "\n"
                + "状态摘要：" + companion.getStatusSummary() + "\n"
                + "观察摘要：" + companion.getObservationSummary() + "\n"
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
                + "背包：" + summary.inventory + "\n"
                + "最近失败：" + snapshot.memory().lastFailure() + "\n"
                + "最近学习：" + snapshot.memory().lastLearning() + "\n"
                + "团队知识：" + TeamSummary.safe(companion);
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
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JsonObject choice = choices.get(0).getAsJsonObject();
                JsonObject message = choice.getAsJsonObject("message");
                if (message != null) {
                    String content = getString(message, "content");
                    if (content != null && !content.isBlank()) {
                        return parseAssistantContent(extractJsonCandidate(content));
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static AIGoalPlanResponse parseGoalPlanResponse(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JsonObject choice = choices.get(0).getAsJsonObject();
                JsonObject message = choice.getAsJsonObject("message");
                if (message != null) {
                    String content = getString(message, "content");
                    if (content != null && !content.isBlank()) {
                        JsonObject object = JsonParser.parseString(extractJsonCandidate(content)).getAsJsonObject();
                        return parseGoalPlanObject(object);
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static AIServiceResponse parseAssistantContent(String content) {
        try {
            JsonObject object = JsonParser.parseString(content).getAsJsonObject();
            return new AIServiceResponse(
                    defaultString(getString(object, "reply"), ""),
                    defaultString(getString(object, "mode"), "unchanged"),
                    defaultString(getString(object, "goalType"), ""),
                    parseStringMap(object.getAsJsonObject("goalArgs")),
                    defaultString(getString(object, "action"), "none"),
                    "api");
        } catch (RuntimeException ex) {
            return new AIServiceResponse(content, "unchanged", "", Map.of(), "none", "api");
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

    private static int resolveRequestTimeoutMs() {
        return Math.max(MIN_TIMEOUT_MS, Math.min(config.timeoutMs, MAX_TIMEOUT_MS));
    }

    private static int resolveConnectTimeoutMs() {
        int requestTimeout = resolveRequestTimeoutMs();
        int connect = Math.max(3000, requestTimeout / 2);
        return Math.min(connect, 30000);
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

    private record WorldSummary(boolean ownerAvailable, double ownerDistance, boolean inHazard, boolean lowFood, boolean lowTools, boolean night, int buildingUnits, String observation, String inventory) {
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
                    snapshot.worldState().inventory());
        }
    }

    private static final class TeamSummary {
        private static String safe(AIPlayerEntity entity) {
            return entity.getLongTermMemorySummary();
        }
    }
}

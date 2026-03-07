package com.mcmod.aiplayers.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class TeamKnowledge {
    private static final int PLANNER_WINDOW_TICKS = 20;
    private static final int MAX_PLANNER_REQUESTS = 2;
    private static final int PATH_WINDOW_TICKS = 10;
    private static final int MAX_PATH_REQUESTS = 4;

    private static final Map<UUID, EnumMap<ResourceType, List<BlockPos>>> RESOURCE_MAP = new ConcurrentHashMap<>();
    private static final Map<UUID, List<String>> EVENT_MAP = new ConcurrentHashMap<>();
    private static final Map<UUID, BudgetWindow> PLANNER_BUDGET = new ConcurrentHashMap<>();
    private static final Map<UUID, BudgetWindow> PATH_BUDGET = new ConcurrentHashMap<>();

    private TeamKnowledge() {
    }

    public static void reportResource(AIPlayerEntity entity, ResourceType type, BlockPos pos) {
        UUID teamId = resolveTeamId(entity);
        if (teamId == null || type == null || pos == null) {
            return;
        }
        EnumMap<ResourceType, List<BlockPos>> resources = RESOURCE_MAP.computeIfAbsent(teamId, ignored -> new EnumMap<>(ResourceType.class));
        List<BlockPos> nodes = resources.computeIfAbsent(type, ignored -> new ArrayList<>());
        if (nodes.stream().anyMatch(existing -> existing.distSqr(pos) <= 4.0D)) {
            return;
        }
        nodes.add(pos.immutable());
        if (nodes.size() > 24) {
            nodes.removeFirst();
        }
    }

    public static void forgetResourceNear(AIPlayerEntity entity, ResourceType type, BlockPos pos, double maxDistanceSqr) {
        UUID teamId = resolveTeamId(entity);
        if (teamId == null || type == null || pos == null) {
            return;
        }
        EnumMap<ResourceType, List<BlockPos>> resources = RESOURCE_MAP.get(teamId);
        if (resources == null) {
            return;
        }
        List<BlockPos> nodes = resources.get(type);
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        nodes.removeIf(existing -> existing.distSqr(pos) <= maxDistanceSqr);
    }

    public static BlockPos findNearestResource(AIPlayerEntity entity, ResourceType type, BlockPos origin) {
        UUID teamId = resolveTeamId(entity);
        if (teamId == null || origin == null) {
            return null;
        }
        EnumMap<ResourceType, List<BlockPos>> resources = RESOURCE_MAP.get(teamId);
        if (resources == null) {
            return null;
        }
        List<BlockPos> nodes = resources.get(type);
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        return nodes.stream()
                .min((left, right) -> Double.compare(Vec3.atCenterOf(left).distanceToSqr(Vec3.atCenterOf(origin)), Vec3.atCenterOf(right).distanceToSqr(Vec3.atCenterOf(origin))))
                .orElse(null);
    }

    public static boolean tryAcquirePlannerBudget(AIPlayerEntity entity, long gameTime) {
        return tryAcquireBudget(PLANNER_BUDGET, resolveTeamId(entity), gameTime, PLANNER_WINDOW_TICKS, MAX_PLANNER_REQUESTS);
    }

    public static boolean tryAcquirePathBudget(AIPlayerEntity entity, long gameTime) {
        return tryAcquireBudget(PATH_BUDGET, resolveTeamId(entity), gameTime, PATH_WINDOW_TICKS, MAX_PATH_REQUESTS);
    }

    public static void reportFailure(AIPlayerEntity entity, String detail, BlockPos pos) {
        UUID teamId = resolveTeamId(entity);
        if (teamId == null || detail == null || detail.isBlank()) {
            return;
        }
        List<String> events = EVENT_MAP.computeIfAbsent(teamId, ignored -> new ArrayList<>());
        String note = pos == null ? detail : detail + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        if (!events.isEmpty() && events.getLast().equals(note)) {
            return;
        }
        events.add(note);
        if (events.size() > 12) {
            events.removeFirst();
        }
    }

    public static void reportStructure(AIPlayerEntity entity, String label, BlockPos pos) {
        reportFailure(entity, "结构：" + label, pos);
    }

    public static String getSummary(AIPlayerEntity entity) {
        UUID teamId = resolveTeamId(entity);
        if (teamId == null) {
            return "暂无团队知识";
        }
        List<String> events = EVENT_MAP.get(teamId);
        return events == null || events.isEmpty() ? "暂无团队知识" : String.join(" | ", events);
    }

    private static UUID resolveTeamId(AIPlayerEntity entity) {
        if (entity == null) {
            return null;
        }
        UUID ownerId = entity.getRuntimeOwnerId();
        return ownerId != null ? ownerId : entity.getUUID();
    }

    private static boolean tryAcquireBudget(Map<UUID, BudgetWindow> budgets, UUID teamId, long gameTime, int windowTicks, int maxRequests) {
        if (teamId == null) {
            return true;
        }
        BudgetWindow window = budgets.computeIfAbsent(teamId, ignored -> new BudgetWindow());
        return window.tryAcquire(gameTime, windowTicks, maxRequests);
    }

    private static final class BudgetWindow {
        private long windowStartTick = Long.MIN_VALUE;
        private int used;

        private synchronized boolean tryAcquire(long gameTime, int windowTicks, int maxRequests) {
            if (this.windowStartTick == Long.MIN_VALUE || gameTime - this.windowStartTick >= windowTicks) {
                this.windowStartTick = gameTime;
                this.used = 0;
            }
            if (this.used >= maxRequests) {
                return false;
            }
            this.used++;
            return true;
        }
    }
}

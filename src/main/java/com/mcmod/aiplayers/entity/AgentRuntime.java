package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.ai.AIGoalPlanResponse;
import com.mcmod.aiplayers.ai.AIServiceManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

final class AgentRuntime {
    private final AIPlayerEntity entity;
    private final AgentMemory memory = new AgentMemory();
    private final PathManager movementController;
    private AgentGoal currentGoal = AgentGoal.of(GoalType.IDLE, "boot", "初始化待命");
    private GoalPlan currentPlan = new GoalPlan(this.currentGoal, java.util.List.of(), "初始化", "boot");
    private AgentGoal directedGoal;
    private AgentGoal llmGoal;
    private boolean pendingGoalResponse;
    private int nextGoalRequestTick;
    private int currentActionIndex;
    private boolean forceReplan = true;
    private String currentActionLabel = "待命";
    private String plannerStatus = "本地规划待机";

    AgentRuntime(AIPlayerEntity entity) {
        this.entity = entity;
        this.movementController = new PathManager(entity);
    }

    PathManager movementController() {
        return this.movementController;
    }

    void tickWorldScan() {
        this.entity.runtimeRefreshPerception();
        this.memory.noteEvent(this.entity.getObservationSummary());
    }

    void tickCombat() {
        this.entity.runtimeTickCombatSense();
    }

    void tickMovement() {
        this.movementController.tick();
    }

    void tickPlanner() {
        if (this.entity.runtimeHasActiveHuntDirective()) {
            this.applyHuntDirectiveState();
            this.entity.runtimeApplyGoalSummary(this.currentGoal, this.currentPlan, this.currentActionLabel, this.plannerStatus, this.movementController.getPathStatus(), this.memory);
            return;
        }
        WorldStateSnapshot state = this.entity.captureWorldStateSnapshot();
        AgentGoal lockedHarvestGoal = this.entity.runtimeHarvestTaskLockGoal();
        AgentGoal localGoal = lockedHarvestGoal != null ? lockedHarvestGoal : GoalSelector.select(this.entity, state, this.memory);
        maybeRequestLlmGoal(localGoal);
        AgentGoal llmCandidate = this.llmGoal;
        if (lockedHarvestGoal != null
                && this.directedGoal == null
                && llmCandidate != null
                && llmCandidate.type() != lockedHarvestGoal.type()
                && llmCandidate.type() != GoalType.RECOVER_SELF) {
            llmCandidate = null;
        }
        AgentGoal chosenGoal = this.directedGoal != null ? this.directedGoal : (llmCandidate != null ? llmCandidate : localGoal);
        if (lockedHarvestGoal != null && this.directedGoal == null && chosenGoal.type() == lockedHarvestGoal.type()) {
            this.plannerStatus = "采集任务状态机锁定中";
        }
        GoalPlan nextPlan = GOAPPlanner.plan(chosenGoal, state);
        if (this.forceReplan || !nextPlan.summary().equals(this.currentPlan.summary())) {
            this.currentGoal = chosenGoal;
            this.currentPlan = nextPlan;
            this.currentActionIndex = 0;
            this.forceReplan = false;
            this.memory.noteLearning("规划更新：" + nextPlan.summary());
        }
        this.currentActionLabel = this.currentPlan.actions().isEmpty()
                ? "待命"
                : this.currentPlan.actions().get(Math.min(this.currentActionIndex, this.currentPlan.actions().size() - 1)).label();
        this.entity.runtimeApplyGoalSummary(this.currentGoal, this.currentPlan, this.currentActionLabel, this.plannerStatus, this.movementController.getPathStatus(), this.memory);
    }

    void tickExecutor() {
        if (this.entity.runtimeHasActiveHuntDirective()) {
            this.applyHuntDirectiveState();
            this.entity.runtimePerformHuntDirective();
            return;
        }
        if (this.currentPlan.actions().isEmpty()) {
            return;
        }
        int guard = 0;
        while (this.currentActionIndex < this.currentPlan.actions().size() && guard < 3) {
            WorldStateSnapshot state = this.entity.captureWorldStateSnapshot();
            PlannedAction action = this.currentPlan.actions().get(this.currentActionIndex);
            this.currentActionLabel = action.label();
            this.memory.noteAction(this.currentActionLabel);
            if (isSatisfied(action, state)) {
                this.currentActionIndex++;
                guard++;
                continue;
            }
            ActionExecutionResult result = this.entity.executePlannedAction(action, this.currentGoal);
            if (result == ActionExecutionResult.SUCCESS) {
                this.memory.noteLearning("完成动作：" + action.label());
                this.currentActionIndex++;
                this.forceReplan = true;
                guard++;
                continue;
            }
            if (result == ActionExecutionResult.FAILED) {
                this.memory.noteFailure("动作失败：" + action.label());
                this.plannerStatus = "动作失败，等待重规划";
                TeamKnowledge.reportFailure(this.entity, action.label(), this.entity.blockPosition());
                this.forceReplan = true;
            }
            break;
        }
        if (this.currentActionIndex >= this.currentPlan.actions().size()) {
            this.forceReplan = true;
        }
        this.entity.runtimeApplyGoalSummary(this.currentGoal, this.currentPlan, this.currentActionLabel, this.plannerStatus, this.movementController.getPathStatus(), this.memory);
    }

    private void applyHuntDirectiveState() {
        String huntLabel = this.entity.runtimeHuntTargetLabel();
        String reason = (huntLabel == null || huntLabel.isBlank()) ? "玩家狩猎指令" : "玩家狩猎指令：" + huntLabel;
        this.currentGoal = AgentGoal.of(GoalType.SURVIVE, "player_hunt", reason);
        this.currentPlan = new GoalPlan(this.currentGoal, List.of(), reason, "hunt-directive");
        this.currentActionIndex = 0;
        this.currentActionLabel = huntLabel == null || huntLabel.isBlank() ? "狩猎中" : ("狩猎 " + huntLabel);
        this.forceReplan = false;
        this.llmGoal = null;
        this.plannerStatus = "狩猎指令接管中";
    }

    void applyDirectedGoal(ServerPlayer speaker, AgentGoal goal, boolean pin) {
        if (speaker != null) {
            this.entity.assignOwner(speaker);
        }
        this.directedGoal = pin ? goal : null;
        this.llmGoal = null;
        this.currentGoal = goal;
        this.currentPlan = GOAPPlanner.plan(goal, this.entity.captureWorldStateSnapshot());
        this.currentActionIndex = 0;
        this.forceReplan = false;
        this.plannerStatus = pin ? "玩家指令接管" : "临时目标";
        this.entity.runtimeApplyCoarseMode(goal.type().coarseMode(), true);
        this.entity.runtimeApplyGoalSummary(this.currentGoal, this.currentPlan, this.currentPlan.firstAction() == null ? "待命" : this.currentPlan.firstAction().label(), this.plannerStatus, this.movementController.getPathStatus(), this.memory);
    }

    void clearDirectedGoal() {
        this.directedGoal = null;
        this.llmGoal = null;
        this.forceReplan = true;
        this.currentGoal = AgentGoal.of(GoalType.IDLE, "player", "玩家停止当前任务");
        this.currentPlan = GOAPPlanner.plan(this.currentGoal, this.entity.captureWorldStateSnapshot());
        this.currentActionIndex = 0;
        this.movementController.clear();
        this.entity.runtimeApplyCoarseMode(AIPlayerMode.IDLE, true);
    }

    String currentActionLabel() {
        return this.currentActionLabel;
    }

    String plannerStatus() {
        return this.plannerStatus;
    }

    String pathStatus() {
        return this.movementController.getPathStatus();
    }

    String lastLearningSummary() {
        return this.memory.lastLearning();
    }

    String lastFailureSummary() {
        return this.memory.lastFailure();
    }

    AgentSnapshot snapshot() {
        return new AgentSnapshot(this.entity.captureWorldStateSnapshot(), this.currentGoal, this.currentPlan, this.currentActionLabel, this.movementController.getPathStatus(), this.plannerStatus, AIServiceManager.getLastStatusText(), this.memory);
    }

    private boolean isSatisfied(PlannedAction action, WorldStateSnapshot state) {
        return switch (action.type()) {
            case MOVE_TO_TARGET -> {
                BlockPos resolved = this.entity.resolveRuntimeTarget(action.targetKey(), action.fallbackTarget());
                yield resolved != null && this.movementController.hasReachedTarget(resolved);
            }
            case ACQUIRE_TOOL -> !state.lowTools();
            case CHOP_TREE -> {
                if (this.currentGoal.type() == GoalType.COLLECT_WOOD) {
                    yield false;
                }
                boolean enoughBuildingUnits = action.requiredCount() > 0 && state.buildingUnits() >= action.requiredCount();
                if (!enoughBuildingUnits) {
                    yield false;
                }
                BlockPos stillReachableWood = this.entity.runtimeResolveHarvestTarget(true);
                yield stillReachableWood == null;
            }
            case MINE_ORE -> false;
            case COLLECT_DROP -> !state.dropNearby();
            case HARVEST_CROP -> !state.lowFood() || !state.cropKnown();
            case CRAFT_PLANKS -> state.plankCount() >= Math.max(1, action.requiredCount());
            case CRAFT_BREAD -> state.breadCount() >= Math.max(1, action.requiredCount()) || !state.lowFood();
            case CRAFT_TORCH -> state.torchCount() >= Math.max(1, action.requiredCount());
            case BUILD_SHELTER -> this.entity.getObservationSummary().contains("已完成");
            case DELIVER_ITEM -> !state.pendingDelivery();
            case RETREAT_TO_SAFE_GROUND -> !state.inHazard() && !state.onFire();
            case REST_AT_BED -> !state.lowHealth() || !state.bedKnown();
            case OBSERVE_AND_REPORT -> false;
        };
    }

    private void maybeRequestLlmGoal(AgentGoal localGoal) {
        if (this.directedGoal != null) {
            return;
        }
        if (!AIServiceManager.canUseGoalPlanningService()) {
            this.plannerStatus = "本地规划运行中";
            return;
        }
        if (this.pendingGoalResponse || this.entity.tickCount < this.nextGoalRequestTick) {
            return;
        }
        if (!TeamKnowledge.tryAcquirePlannerBudget(this.entity, this.entity.level().getGameTime())) {
            this.plannerStatus = "团队规划预算忙，暂用本地目标";
            return;
        }
        this.pendingGoalResponse = true;
        this.nextGoalRequestTick = this.entity.tickCount + AIServiceManager.getReplanIntervalTicks();
        this.plannerStatus = "等待 LLM 目标重规划";
        AIServiceManager.tryPlanGoalAsync(this.entity, snapshot(), localGoal).whenComplete((response, throwable) -> {
            var server = this.entity.level().getServer();
            if (server == null) {
                this.pendingGoalResponse = false;
                return;
            }
            server.execute(() -> {
                this.pendingGoalResponse = false;
                if (throwable != null || response == null || !response.hasPlan()) {
                    this.llmGoal = null;
                    this.plannerStatus = "LLM 不可用，继续本地规划：" + AIServiceManager.getLastStatusText();
                    this.entity.runtimeRemember("任务AI", this.plannerStatus);
                    return;
                }
                this.llmGoal = toGoal(response, localGoal);
                this.plannerStatus = "LLM 已返回高层目标：" + this.llmGoal.label();
                if (response.speechReply() != null && !response.speechReply().isBlank()) {
                    this.memory.noteLearning(response.speechReply());
                }
                this.forceReplan = true;
            });
        });
    }

    private AgentGoal toGoal(AIGoalPlanResponse response, AgentGoal localGoal) {
        GoalType type = response.resolveGoalType(localGoal.type());
        Map<String, String> args = new HashMap<>(response.goalArgs());
        GoalType fallback = response.resolveFallbackGoal(localGoal.fallbackType());
        return AgentGoal.of(type, args, response.priority(), response.source(), response.speechReply().isBlank() ? response.goalType() : response.speechReply(), fallback);
    }
}

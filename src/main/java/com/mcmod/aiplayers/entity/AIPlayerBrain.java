package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.ai.AIServiceManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

final class AIPlayerBrain {
    private final AIPlayerEntity entity;
    private final AgentMemory memory;
    private final PathManager movementController;
    private final AIPlayerBlackboard blackboard = new AIPlayerBlackboard();

    private WorldModelSnapshot worldModel = WorldModelSnapshot.empty();
    private SharedMemorySnapshot sharedMemory = SharedMemorySnapshot.empty();
    private AgentGoal currentGoal = AgentGoal.of(GoalType.IDLE, "brain", "初始化待命");
    private GoalPlan currentPlan = new GoalPlan(this.currentGoal, List.of(), "初始化", "brain");
    private BehaviorNode rootNode = new ActionLeaf(BehaviorNodeSpec.action("待命观察", "observe", Map.of()));
    private AgentBrainResponse llmPlan;
    private boolean pendingBrainResponse;
    private int nextBrainPlanTick;
    private int brainPlanValidUntilTick;
    private int lastWorldSignature;
    private boolean forceReplan = true;
    private String currentActionLabel = "待命";
    private String activeNodeLabel = "Idle";
    private String plannerStatus = "Brain 初始化";
    private String currentPlanKey = "boot";
    private BrainAction activeAction;
    private String activeActionKey = "";

    AIPlayerBrain(AIPlayerEntity entity, AgentMemory memory, PathManager movementController) {
        this.entity = entity;
        this.memory = memory;
        this.movementController = movementController;
    }

    void tickWorldScan() {
        this.worldModel = this.entity.buildWorldModelSnapshot(this.movementController);
        TeamMemoryService.syncFromWorldModel(this.entity, this.worldModel);
        this.sharedMemory = TeamMemoryService.snapshot(this.entity);
        this.memory.noteEvent(this.worldModel.observation());
    }

    void tickCombat() {
        if (this.worldModel.hostileNearby()) {
            this.memory.noteEvent("战斗感知：附近存在敌对目标");
        }
    }

    void tickPlanner() {
        if (this.worldModel.gameTime() == 0L) {
            this.tickWorldScan();
        }

        TaskRequest preferred = this.resolvePreferredTask();
        AgentBrainResponse localPlan = LocalSafetyPlanner.plan(this.entity, this.worldModel, this.sharedMemory, preferred);
        boolean contextChanged = this.hasPlanningContextChanged(preferred);
        this.maybeRequestLlmPlan(preferred, contextChanged);

        AgentBrainResponse chosen = this.choosePlan(localPlan);
        String nextPlanKey = this.planKey(chosen);
        if (this.forceReplan || contextChanged || !nextPlanKey.equals(this.currentPlanKey)) {
            this.currentGoal = chosen.task().toAgentGoal();
            this.rootNode = this.compile(this.buildRootSpec(chosen));
            this.currentPlan = this.toCompatibilityPlan(chosen);
            this.currentPlanKey = nextPlanKey;
            this.currentActionLabel = "待命";
            this.activeNodeLabel = chosen.task().label();
            this.forceReplan = false;
            this.entity.runtimeApplyCoarseMode(this.currentGoal.type().coarseMode(), chosen.task().pinned());
            this.memory.noteLearning("Brain 规划更新：" + chosen.reasoning());
        }
        this.plannerStatus = (chosen.source().contains("api") ? "LLM Brain" : "Local Brain")
                + " | 任务=" + chosen.task().goalType().displayName()
                + " | 节点=" + this.activeNodeLabel;
        this.entity.runtimeApplyGoalSummary(this.currentGoal, this.currentPlan, this.currentActionLabel, this.plannerStatus, this.movementController.getPathStatus(), this.memory);
    }

    void tickExecutor() {
        if (this.rootNode == null) {
            this.rootNode = this.compile(this.buildRootSpec(LocalSafetyPlanner.plan(this.entity, this.worldModel, this.sharedMemory, this.resolvePreferredTask())));
        }
        BehaviorDecision decision = this.rootNode.tick();
        this.activeNodeLabel = decision.nodeLabel();
        if (decision.status() == BehaviorStatus.FAILURE) {
            this.memory.noteFailure("节点失败：" + decision.nodeLabel());
            TeamMemoryService.recordFailure(this.entity, "节点失败：" + decision.nodeLabel(), this.resolveTarget(Map.of("target", "danger")));
            this.llmPlan = null;
            this.forceReplan = true;
        }
        if (decision.status() == BehaviorStatus.SUCCESS && this.activeAction == null) {
            this.currentActionLabel = decision.nodeLabel();
        }
        this.entity.runtimeApplyGoalSummary(this.currentGoal, this.currentPlan, this.currentActionLabel, this.plannerStatus, this.movementController.getPathStatus(), this.memory);
    }

    void applyDirectedGoal(ServerPlayer speaker, AgentGoal goal, boolean pin) {
        TaskRequest request = TaskRequest.fromGoal(goal, pin);
        if (pin) {
            this.blackboard.pinTask(request);
        } else {
            this.blackboard.putValue("soft_task", request.taskType());
        }
        this.llmPlan = null;
        this.brainPlanValidUntilTick = 0;
        this.forceReplan = true;
        this.cancelActiveAction();
        this.memory.noteLearning("收到兼容指令：" + goal.label());
        if (speaker != null) {
            this.entity.runtimeRemember("Brain", speaker.getName().getString() + " -> " + goal.label());
        }
    }

    void clearDirectedGoal() {
        this.blackboard.clearPinnedTask();
        this.llmPlan = null;
        this.forceReplan = true;
        this.cancelActiveAction();
    }

    String currentActionLabel() {
        return this.currentActionLabel;
    }

    String plannerStatus() {
        return this.plannerStatus;
    }

    AgentSnapshot snapshot() {
        return new AgentSnapshot(
                this.entity.captureWorldStateSnapshot(),
                this.currentGoal,
                this.currentPlan,
                this.currentActionLabel,
                this.movementController.getPathStatus(),
                this.plannerStatus,
                AIServiceManager.getLastStatusText(),
                this.memory);
    }

    private TaskRequest resolvePreferredTask() {
        if (this.blackboard.pinnedTask() != null) {
            return this.blackboard.pinnedTask();
        }
        String softTask = this.blackboard.value("soft_task");
        if (!softTask.isBlank()) {
            return new TaskRequest(softTask, softTask, Map.of(), 40, false, "soft", GoalType.SURVIVE.commandName());
        }
        return LocalSafetyPlanner.chooseAutonomousTask(this.worldModel);
    }

    private boolean hasPlanningContextChanged(TaskRequest request) {
        int signature = Objects.hash(
                request == null ? "" : request.taskType(),
                this.worldModel.ownerAvailable(),
                this.worldModel.ownerUnderThreat(),
                this.worldModel.hostileNearby(),
                this.worldModel.lowHealth(),
                this.worldModel.lowFood(),
                this.worldModel.lowTools(),
                this.worldModel.inHazard(),
                this.worldModel.onFire(),
                this.worldModel.night(),
                this.worldModel.nearestResource("wood") == null ? "" : this.worldModel.nearestResource("wood").pos(),
                this.worldModel.nearestResource("ore") == null ? "" : this.worldModel.nearestResource("ore").pos(),
                this.worldModel.nearestStructure("bed") == null ? "" : this.worldModel.nearestStructure("bed").pos(),
                this.sharedMemory.summary());
        boolean changed = signature != this.lastWorldSignature;
        this.lastWorldSignature = signature;
        return changed;
    }

    private AgentBrainResponse choosePlan(AgentBrainResponse localPlan) {
        if (this.llmPlan == null || this.entity.tickCount > this.brainPlanValidUntilTick) {
            return localPlan;
        }
        if (this.blackboard.pinnedTask() != null && this.llmPlan.task().goalType() != this.blackboard.pinnedTask().goalType()) {
            return localPlan;
        }
        return this.llmPlan;
    }

    private void maybeRequestLlmPlan(TaskRequest request, boolean contextChanged) {
        if (!AIServiceManager.canUseBrainPlanningService()) {
            return;
        }
        if (this.pendingBrainResponse || this.entity.tickCount < this.nextBrainPlanTick) {
            return;
        }
        if (!contextChanged && !this.forceReplan && this.llmPlan != null && this.entity.tickCount <= this.brainPlanValidUntilTick) {
            return;
        }
        if (!TeamKnowledge.tryAcquirePlannerBudget(this.entity, this.entity.level().getGameTime())) {
            this.plannerStatus = "团队规划预算忙，暂用本地 Brain";
            return;
        }
        this.pendingBrainResponse = true;
        this.nextBrainPlanTick = this.entity.tickCount + AIServiceManager.getReplanIntervalTicks();
        AgentSnapshot compatibilitySnapshot = this.snapshot();
        AIServiceManager.tryPlanBrainAsync(this.entity, this.worldModel, this.sharedMemory, request, compatibilitySnapshot).whenComplete((response, throwable) -> {
            var server = this.entity.level().getServer();
            if (server == null) {
                this.pendingBrainResponse = false;
                return;
            }
            server.execute(() -> {
                this.pendingBrainResponse = false;
                if (throwable != null || response == null || !response.hasUsablePlan()) {
                    this.llmPlan = null;
                    this.brainPlanValidUntilTick = 0;
                    this.memory.noteFailure("LLM Brain 不可用：" + AIServiceManager.getLastStatusText());
                    return;
                }
                this.llmPlan = response;
                this.brainPlanValidUntilTick = this.entity.tickCount + AIServiceManager.getReplanIntervalTicks() * 2;
                this.forceReplan = true;
                this.memory.noteLearning("LLM Brain 任务：" + response.task().label());
            });
        });
    }

    private String planKey(AgentBrainResponse response) {
        if (response == null) {
            return "none";
        }
        return response.source() + "|" + response.task().taskType() + "|" + response.reasoning() + "|" + this.collectActionTasks(response.subtree()).stream().map(ActionTask::key).toList();
    }

    private BehaviorNodeSpec buildRootSpec(AgentBrainResponse chosen) {
        List<BehaviorNodeSpec> rootChildren = new ArrayList<>();
        rootChildren.add(BehaviorNodeSpec.sequence("EmergencySurvival", List.of(
                BehaviorNodeSpec.condition("NeedEmergency", "emergency"),
                LocalSafetyPlanner.emergencyPlan(this.entity, this.worldModel, this.sharedMemory).subtree())));
        if (this.blackboard.pinnedTask() != null) {
            rootChildren.add(BehaviorNodeSpec.sequence("PinnedPlayerTask", List.of(
                    BehaviorNodeSpec.condition("HasPinnedTask", "has_pinned_task"),
                    LocalSafetyPlanner.plan(this.entity, this.worldModel, this.sharedMemory, this.blackboard.pinnedTask()).subtree())));
        }
        rootChildren.add(BehaviorNodeSpec.sequence("Combat", List.of(
                BehaviorNodeSpec.condition("CombatNeeded", "combat_needed"),
                LocalSafetyPlanner.combatPlan(this.entity, this.worldModel, this.sharedMemory).subtree())));
        if (chosen != null && chosen.subtree() != null) {
            rootChildren.add(BehaviorNodeSpec.sequence("TaskExecution", List.of(
                    BehaviorNodeSpec.condition("TaskAvailable", "task_available"),
                    chosen.subtree())));
        }
        rootChildren.add(LocalSafetyPlanner.idlePlan(this.entity, this.worldModel, this.sharedMemory).subtree());
        return BehaviorNodeSpec.selector("BrainRoot", rootChildren);
    }

    private GoalPlan toCompatibilityPlan(AgentBrainResponse response) {
        List<PlannedAction> actions = this.collectActionTasks(response == null ? null : response.subtree()).stream()
                .limit(10)
                .map(this::toPlannedAction)
                .toList();
        return new GoalPlan(response == null ? this.currentGoal : response.task().toAgentGoal(), actions, response == null ? "brain" : response.reasoning(), response == null ? "brain-local" : response.source());
    }

    private List<ActionTask> collectActionTasks(BehaviorNodeSpec spec) {
        if (spec == null) {
            return List.of();
        }
        List<ActionTask> actions = new ArrayList<>();
        this.collectActionTasks(spec, actions);
        return actions;
    }

    private void collectActionTasks(BehaviorNodeSpec spec, List<ActionTask> sink) {
        if (spec == null || sink == null) {
            return;
        }
        if ("action".equals(spec.type()) && spec.action() != null) {
            sink.add(spec.action());
        }
        for (BehaviorNodeSpec child : spec.children()) {
            this.collectActionTasks(child, sink);
        }
    }

    private PlannedAction toPlannedAction(ActionTask task) {
        if (task == null) {
            return PlannedAction.simple(GoapActionType.OBSERVE_AND_REPORT, "观察");
        }
        return switch (task.actionType()) {
            case "move_to" -> PlannedAction.move(task.label(), task.args().getOrDefault("target", "explore"), this.resolveTarget(task.args()), parseDouble(task.args(), "speed", 1.0D));
            case "chop_tree" -> new PlannedAction(GoapActionType.CHOP_TREE, task.label(), "wood", this.resolveTarget(task.args()), 1.0D, 0);
            case "mine_block" -> new PlannedAction(GoapActionType.MINE_ORE, task.label(), "ore", this.resolveTarget(task.args()), 1.0D, 0);
            case "collect_drops" -> PlannedAction.simple(GoapActionType.COLLECT_DROP, task.label());
            case "harvest_crop" -> PlannedAction.simple(GoapActionType.HARVEST_CROP, task.label());
            case "recover_self" -> PlannedAction.simple(GoapActionType.RETREAT_TO_SAFE_GROUND, task.label());
            case "deliver_item" -> PlannedAction.simple(GoapActionType.DELIVER_ITEM, task.label());
            case "place_block" -> PlannedAction.simple(GoapActionType.BUILD_SHELTER, task.label());
            default -> PlannedAction.simple(GoapActionType.OBSERVE_AND_REPORT, task.label());
        };
    }

    private BehaviorNode compile(BehaviorNodeSpec spec) {
        if (spec == null) {
            return new ActionLeaf(BehaviorNodeSpec.action("待命观察", "observe", Map.of()));
        }
        return switch (spec.type()) {
            case "selector" -> new SelectorNode(spec, spec.children().stream().map(this::compile).toList());
            case "sequence" -> new SequenceNode(spec, spec.children().stream().map(this::compile).toList());
            case "repeat_until" -> new RepeatUntilNode(spec, spec.children().isEmpty() ? new ActionLeaf(BehaviorNodeSpec.action("待命观察", "observe", Map.of())) : this.compile(spec.children().getFirst()));
            case "timeout" -> new TimeoutNode(spec, spec.children().isEmpty() ? new ActionLeaf(BehaviorNodeSpec.action("待命观察", "observe", Map.of())) : this.compile(spec.children().getFirst()));
            case "condition" -> new ConditionLeaf(spec);
            case "action" -> new ActionLeaf(spec);
            default -> new ActionLeaf(BehaviorNodeSpec.action(spec.label(), "observe", spec.args()));
        };
    }

    private boolean evaluateCondition(String condition, Map<String, String> args) {
        String key = condition == null ? "" : condition.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "always", "task_available" -> true;
            case "emergency" -> this.worldModel.inHazard() || this.worldModel.onFire() || this.worldModel.stuckState().stuck() || (this.worldModel.lowHealth() && this.worldModel.hostileNearby());
            case "has_pinned_task" -> this.blackboard.pinnedTask() != null;
            case "combat_needed" -> this.worldModel.hostileNearby() || this.worldModel.ownerUnderThreat();
            case "safe" -> !this.worldModel.inHazard() && !this.worldModel.onFire() && !this.worldModel.stuckState().stuck();
            case "shelter_ready" -> this.worldModel.shelterReady();
            case "low_food" -> this.worldModel.lowFood();
            case "low_tools" -> this.worldModel.lowTools();
            case "has_wood_target" -> this.resolveTarget(Map.of("target", "wood")) != null;
            case "has_ore_target" -> this.resolveTarget(Map.of("target", "ore")) != null;
            case "has_nearby_drops" -> this.worldModel.droppedItemPos() != null;
            case "owner_far" -> this.worldModel.ownerAvailable() && this.worldModel.ownerPos() != null && this.entity.blockPosition().distSqr(this.worldModel.ownerPos()) > 49.0D;
            case "night" -> this.worldModel.night();
            default -> false;
        };
    }

    private BehaviorDecision executeAction(ActionTask task, String nodeLabel) {
        if (task == null) {
            this.currentActionLabel = nodeLabel;
            return BehaviorDecision.success(nodeLabel);
        }
        BrainAction nextAction = this.instantiateAction(task);
        if (nextAction == null) {
            return BehaviorDecision.failure(nodeLabel);
        }
        if (!task.key().equals(this.activeActionKey)) {
            this.cancelActiveAction();
            this.activeAction = nextAction;
            this.activeActionKey = task.key();
            this.activeAction.start();
        }
        BrainActionResult result = this.activeAction.tick();
        this.currentActionLabel = this.activeAction.label();
        this.memory.noteAction(this.currentActionLabel);
        return switch (result) {
            case RUNNING -> BehaviorDecision.running(nodeLabel, task);
            case SUCCESS -> {
                this.memory.noteLearning("完成动作：" + this.currentActionLabel);
                TeamMemoryService.recordTaskOutcome(this.entity, this.currentActionLabel);
                this.activeAction.finish();
                this.activeAction = null;
                this.activeActionKey = "";
                yield BehaviorDecision.success(nodeLabel);
            }
            case FAILURE -> {
                this.memory.noteFailure("动作失败：" + this.currentActionLabel);
                TeamMemoryService.recordFailure(this.entity, "动作失败：" + this.currentActionLabel, this.resolveTarget(task.args()));
                this.cancelActiveAction();
                yield BehaviorDecision.failure(nodeLabel);
            }
            case BLOCKED -> {
                this.memory.noteFailure("动作阻塞：" + this.currentActionLabel);
                TeamMemoryService.recordFailure(this.entity, "动作阻塞：" + this.currentActionLabel, this.resolveTarget(task.args()));
                this.entity.runtimeAttemptImmediateRecovery();
                this.cancelActiveAction();
                yield BehaviorDecision.failure(nodeLabel);
            }
        };
    }

    private void cancelActiveAction() {
        if (this.activeAction != null) {
            this.activeAction.cancel();
        }
        this.activeAction = null;
        this.activeActionKey = "";
    }

    private BrainAction instantiateAction(ActionTask task) {
        return switch (task.actionType()) {
            case "move_to" -> new MoveToAction(task);
            case "look_at" -> new LookAtAction(task);
            case "equip_tool" -> new EquipToolAction(task);
            case "mine_block" -> new HarvestAction(task, false);
            case "chop_tree" -> new ChopTreeAction(task);
            case "collect_drops" -> new CollectDropsAction(task);
            case "attack_target" -> new AttackTargetAction(task);
            case "place_block" -> new PlaceBlockAction(task);
            case "bridge" -> new BridgeAction(task);
            case "tunnel" -> new TunnelAction(task);
            case "open_container" -> new OpenContainerAction(task);
            case "consume_food" -> new ConsumeFoodAction(task);
            case "recover_self" -> new RecoverAction(task);
            case "observe" -> new ObserveAction(task);
            case "harvest_crop" -> new CropHarvestAction(task);
            case "deliver_item" -> new DeliverItemAction(task);
            default -> new ObserveAction(task);
        };
    }

    private BlockPos resolveTarget(Map<String, String> args) {
        if (args == null) {
            return null;
        }
        if (args.containsKey("x") && args.containsKey("y") && args.containsKey("z")) {
            return parseBlockPos(args.get("x"), args.get("y"), args.get("z"));
        }
        String pos = args.getOrDefault("pos", args.getOrDefault("targetPos", ""));
        if (!pos.isBlank()) {
            String[] parts = pos.split(",");
            if (parts.length == 3) {
                return parseBlockPos(parts[0], parts[1], parts[2]);
            }
        }
        String target = args.getOrDefault("target", "").trim().toLowerCase(Locale.ROOT);
        if (target.isBlank()) {
            target = args.getOrDefault("kind", "").trim().toLowerCase(Locale.ROOT);
        }
        return switch (target) {
            case "owner", "follow_owner" -> this.worldModel.ownerPos();
            case "hostile", "enemy" -> this.worldModel.hostilePos();
            case "drop", "drops" -> this.worldModel.droppedItemPos();
            case "wood", "tree", "log" -> this.resolveResourceTarget("wood", ResourceType.WOOD);
            case "ore", "mine" -> this.resolveResourceTarget("ore", ResourceType.ORE);
            case "crop", "food" -> this.resolveResourceTarget("crop", ResourceType.CROP);
            case "bed" -> this.resolveResourceTarget("bed", ResourceType.BED);
            case "chest" -> this.resolveResourceTarget("chest", ResourceType.CHEST);
            case "crafting", "crafting_table" -> this.resolveResourceTarget("crafting", ResourceType.CRAFTING);
            case "furnace" -> this.resolveResourceTarget("furnace", ResourceType.FURNACE);
            case "danger" -> this.worldModel.nearestDanger("hazard") == null ? null : this.worldModel.nearestDanger("hazard").pos();
            case "explore" -> this.entity.runtimeFindExplorationDestination();
            case "shelter_next" -> this.entity.runtimeFindNextShelterPlacement();
            default -> null;
        };
    }

    private BlockPos resolveResourceTarget(String kind, ResourceType type) {
        WorldModelSnapshot.SpatialFact local = this.worldModel.nearestResource(kind);
        if (local != null) {
            return local.pos();
        }
        BlockPos shared = this.sharedMemory.nearestResource(kind, this.entity.blockPosition());
        if (shared != null) {
            return shared;
        }
        return TeamKnowledge.findNearestResource(this.entity, type, this.entity.blockPosition());
    }

    private static BlockPos parseBlockPos(String rawX, String rawY, String rawZ) {
        try {
            return new BlockPos(Integer.parseInt(rawX.trim()), Integer.parseInt(rawY.trim()), Integer.parseInt(rawZ.trim()));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static double parseDouble(Map<String, String> args, String key, double fallback) {
        try {
            String raw = args == null ? null : args.get(key);
            return raw == null || raw.isBlank() ? fallback : Double.parseDouble(raw.trim());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private interface BehaviorNode {
        BehaviorDecision tick();
    }

    private enum BehaviorStatus {
        SUCCESS,
        FAILURE,
        RUNNING
    }

    private record BehaviorDecision(BehaviorStatus status, String nodeLabel, ActionTask action) {
        static BehaviorDecision success(String nodeLabel) {
            return new BehaviorDecision(BehaviorStatus.SUCCESS, nodeLabel, null);
        }

        static BehaviorDecision failure(String nodeLabel) {
            return new BehaviorDecision(BehaviorStatus.FAILURE, nodeLabel, null);
        }

        static BehaviorDecision running(String nodeLabel, ActionTask action) {
            return new BehaviorDecision(BehaviorStatus.RUNNING, nodeLabel, action);
        }
    }

    private final class SelectorNode implements BehaviorNode {
        private final BehaviorNodeSpec spec;
        private final List<BehaviorNode> children;

        private SelectorNode(BehaviorNodeSpec spec, List<BehaviorNode> children) {
            this.spec = spec;
            this.children = children == null ? List.of() : children;
        }

        @Override
        public BehaviorDecision tick() {
            for (BehaviorNode child : this.children) {
                BehaviorDecision decision = child.tick();
                if (decision.status() == BehaviorStatus.FAILURE) {
                    continue;
                }
                return new BehaviorDecision(decision.status(), this.spec.label() + " -> " + decision.nodeLabel(), decision.action());
            }
            return BehaviorDecision.failure(this.spec.label());
        }
    }

    private final class SequenceNode implements BehaviorNode {
        private final BehaviorNodeSpec spec;
        private final List<BehaviorNode> children;

        private SequenceNode(BehaviorNodeSpec spec, List<BehaviorNode> children) {
            this.spec = spec;
            this.children = children == null ? List.of() : children;
        }

        @Override
        public BehaviorDecision tick() {
            int index = AIPlayerBrain.this.blackboard.indexOf(this.spec.id());
            while (index < this.children.size()) {
                BehaviorDecision decision = this.children.get(index).tick();
                if (decision.status() == BehaviorStatus.SUCCESS) {
                    index++;
                    AIPlayerBrain.this.blackboard.setIndex(this.spec.id(), index);
                    continue;
                }
                if (decision.status() == BehaviorStatus.RUNNING) {
                    AIPlayerBrain.this.blackboard.setIndex(this.spec.id(), index);
                    return new BehaviorDecision(BehaviorStatus.RUNNING, this.spec.label() + " -> " + decision.nodeLabel(), decision.action());
                }
                AIPlayerBrain.this.blackboard.clearNodeState(this.spec.id());
                return BehaviorDecision.failure(this.spec.label() + " -> " + decision.nodeLabel());
            }
            AIPlayerBrain.this.blackboard.clearNodeState(this.spec.id());
            return BehaviorDecision.success(this.spec.label());
        }
    }

    private final class RepeatUntilNode implements BehaviorNode {
        private final BehaviorNodeSpec spec;
        private final BehaviorNode child;

        private RepeatUntilNode(BehaviorNodeSpec spec, BehaviorNode child) {
            this.spec = spec;
            this.child = child;
        }

        @Override
        public BehaviorDecision tick() {
            if (AIPlayerBrain.this.evaluateCondition(this.spec.condition(), this.spec.args())) {
                AIPlayerBrain.this.blackboard.clearNodeState(this.spec.id());
                return BehaviorDecision.success(this.spec.label());
            }
            BehaviorDecision decision = this.child.tick();
            return new BehaviorDecision(decision.status(), this.spec.label() + " -> " + decision.nodeLabel(), decision.action());
        }
    }

    private final class TimeoutNode implements BehaviorNode {
        private final BehaviorNodeSpec spec;
        private final BehaviorNode child;

        private TimeoutNode(BehaviorNodeSpec spec, BehaviorNode child) {
            this.spec = spec;
            this.child = child;
        }

        @Override
        public BehaviorDecision tick() {
            int start = AIPlayerBrain.this.blackboard.startTickOf(this.spec.id());
            if (start < 0) {
                AIPlayerBrain.this.blackboard.setStartTick(this.spec.id(), AIPlayerBrain.this.entity.tickCount);
                start = AIPlayerBrain.this.entity.tickCount;
            }
            if (this.spec.timeoutTicks() > 0 && AIPlayerBrain.this.entity.tickCount - start >= this.spec.timeoutTicks()) {
                AIPlayerBrain.this.blackboard.clearNodeState(this.spec.id());
                return BehaviorDecision.failure(this.spec.label() + " 超时");
            }
            BehaviorDecision decision = this.child.tick();
            if (decision.status() != BehaviorStatus.RUNNING) {
                AIPlayerBrain.this.blackboard.clearNodeState(this.spec.id());
            }
            return new BehaviorDecision(decision.status(), this.spec.label() + " -> " + decision.nodeLabel(), decision.action());
        }
    }

    private final class ConditionLeaf implements BehaviorNode {
        private final BehaviorNodeSpec spec;

        private ConditionLeaf(BehaviorNodeSpec spec) {
            this.spec = spec;
        }

        @Override
        public BehaviorDecision tick() {
            return AIPlayerBrain.this.evaluateCondition(this.spec.condition(), this.spec.args())
                    ? BehaviorDecision.success(this.spec.label())
                    : BehaviorDecision.failure(this.spec.label());
        }
    }

    private final class ActionLeaf implements BehaviorNode {
        private final BehaviorNodeSpec spec;

        private ActionLeaf(BehaviorNodeSpec spec) {
            this.spec = spec;
        }

        @Override
        public BehaviorDecision tick() {
            return AIPlayerBrain.this.executeAction(this.spec.action(), this.spec.label());
        }
    }

    private interface BrainAction {
        void start();

        BrainActionResult tick();

        void cancel();

        default void finish() {
        }

        String label();
    }

    private enum BrainActionResult {
        RUNNING,
        SUCCESS,
        FAILURE,
        BLOCKED
    }

    private abstract class BaseBrainAction implements BrainAction {
        protected final ActionTask task;

        private BaseBrainAction(ActionTask task) {
            this.task = task;
        }

        @Override
        public void start() {
        }

        @Override
        public void cancel() {
        }

        @Override
        public String label() {
            return this.task == null ? "待命" : this.task.label();
        }
    }

    private final class MoveToAction extends BaseBrainAction {
        private MoveToAction(ActionTask task) {
            super(task);
        }

        @Override
        public BrainActionResult tick() {
            BlockPos target = AIPlayerBrain.this.resolveTarget(this.task.args());
            if (target == null) {
                return BrainActionResult.FAILURE;
            }
            double reachDistance = parseDouble(this.task.args(), "reachDistance", 2.56D);
            double speed = parseDouble(this.task.args(), "speed", 1.0D);
            if (AIPlayerBrain.this.entity.runtimeIsWithin(target, reachDistance) || AIPlayerBrain.this.movementController.hasReachedTarget(target)) {
                return BrainActionResult.SUCCESS;
            }
            boolean started = AIPlayerBrain.this.entity.runtimeNavigateToPosition(target, speed);
            if (!started && AIPlayerBrain.this.entity.runtimeShouldAttemptImmediateRecovery()) {
                if (AIPlayerBrain.this.entity.runtimeTryDiggableAdvance(target)) {
                    return BrainActionResult.RUNNING;
                }
                AIPlayerBrain.this.entity.runtimeAttemptImmediateRecovery();
            }
            return started ? BrainActionResult.RUNNING : BrainActionResult.BLOCKED;
        }
    }

    private final class LookAtAction extends BaseBrainAction {
        private LookAtAction(ActionTask task) {
            super(task);
        }

        @Override
        public BrainActionResult tick() {
            BlockPos target = AIPlayerBrain.this.resolveTarget(this.task.args());
            if (target == null) {
                return BrainActionResult.FAILURE;
            }
            AIPlayerBrain.this.entity.runtimeLookAt(Vec3.atCenterOf(target), Math.max(6, (int) parseDouble(this.task.args(), "ticks", 12.0D)));
            return BrainActionResult.SUCCESS;
        }
    }

    private final class EquipToolAction extends BaseBrainAction {
        private EquipToolAction(ActionTask task) {
            super(task);
        }

        @Override
        public BrainActionResult tick() {
            String kind = this.task.args().getOrDefault("kind", this.task.args().getOrDefault("target", "wood")).toLowerCase(Locale.ROOT);
            return AIPlayerBrain.this.entity.runtimePrepareHarvestTool(!"ore".equals(kind)) ? BrainActionResult.SUCCESS : BrainActionResult.BLOCKED;
        }
    }

    private final class HarvestAction extends BaseBrainAction {
        private final boolean woodTask;

        private HarvestAction(ActionTask task, boolean woodTask) {
            super(task);
            this.woodTask = woodTask;
        }

        @Override
        public BrainActionResult tick() {
            if (!AIPlayerBrain.this.entity.runtimePrepareHarvestTool(this.woodTask)) {
                return BrainActionResult.BLOCKED;
            }
            if (AIPlayerBrain.this.entity.runtimeHasNearbyDrops()) {
                return AIPlayerBrain.this.entity.runtimeCollectNearbyDrops() ? BrainActionResult.RUNNING : BrainActionResult.SUCCESS;
            }
            AIPlayerEntity.HarvestTaskView view = AIPlayerBrain.this.entity.runtimeBuildHarvestTaskView(this.woodTask);
            if (view.target() == null) {
                if (view.moveTarget() != null) {
                    AIPlayerBrain.this.entity.runtimeNavigateToPosition(view.moveTarget(), 1.0D);
                    return BrainActionResult.RUNNING;
                }
                return BrainActionResult.SUCCESS;
            }
            if (view.obstacle() != null) {
                if (AIPlayerBrain.this.entity.runtimeCanHarvestFromHere(view.obstacle())) {
                    AIPlayerBrain.this.entity.runtimeBreakPathBlock(view.obstacle(), this.woodTask);
                    return BrainActionResult.RUNNING;
                }
                if (view.moveTarget() != null) {
                    AIPlayerBrain.this.entity.runtimeNavigateToPosition(view.moveTarget(), 1.02D);
                    return BrainActionResult.RUNNING;
                }
                return BrainActionResult.BLOCKED;
            }
            if (AIPlayerBrain.this.entity.runtimeCanHarvestFromHere(view.target())) {
                AIPlayerBrain.this.entity.runtimeAdjustViewTo(view.target());
                AIPlayerBrain.this.entity.runtimeHarvestTarget(view.target(), this.woodTask);
                return BrainActionResult.RUNNING;
            }
            if (view.moveTarget() != null) {
                AIPlayerBrain.this.entity.runtimeNavigateToPosition(view.moveTarget(), 1.02D);
                return BrainActionResult.RUNNING;
            }
            return BrainActionResult.BLOCKED;
        }
    }

    private final class ChopTreeAction extends BaseBrainAction {
        private final List<BlockPos> remainingLogs = new ArrayList<>();

        private ChopTreeAction(ActionTask task) {
            super(task);
        }

        @Override
        public void start() {
            BlockPos seed = AIPlayerBrain.this.resolveTarget(Map.of("target", this.task.args().getOrDefault("target", "wood")));
            if (seed == null && AIPlayerBrain.this.worldModel.nearestResource("wood") != null) {
                seed = AIPlayerBrain.this.worldModel.nearestResource("wood").pos();
            }
            if (seed == null || !(AIPlayerBrain.this.entity.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            this.remainingLogs.clear();
            this.remainingLogs.addAll(TreeChopper.collectTreeLogs(serverLevel, seed));
        }

        @Override
        public BrainActionResult tick() {
            if (!AIPlayerBrain.this.entity.runtimePrepareHarvestTool(true)) {
                return BrainActionResult.BLOCKED;
            }
            if (AIPlayerBrain.this.entity.runtimeHasNearbyDrops()) {
                AIPlayerBrain.this.entity.runtimeCollectNearbyDrops();
                return BrainActionResult.RUNNING;
            }
            while (!this.remainingLogs.isEmpty() && !AIPlayerBrain.this.entity.runtimeIsValidHarvestTarget(this.remainingLogs.getFirst(), true)) {
                this.remainingLogs.removeFirst();
            }
            if (this.remainingLogs.isEmpty()) {
                return BrainActionResult.SUCCESS;
            }
            BlockPos target = this.remainingLogs.getFirst();
            BlockPos obstacle = AIPlayerBrain.this.entity.runtimeFindHarvestObstacle(target, true);
            if (obstacle != null) {
                if (AIPlayerBrain.this.entity.runtimeCanHarvestFromHere(obstacle)) {
                    AIPlayerBrain.this.entity.runtimeBreakPathBlock(obstacle, true);
                    return BrainActionResult.RUNNING;
                }
                BlockPos approachObstacle = AIPlayerBrain.this.entity.runtimeFindApproachPosition(obstacle);
                if (approachObstacle != null && AIPlayerBrain.this.entity.runtimeNavigateToPosition(approachObstacle, 1.02D)) {
                    return BrainActionResult.RUNNING;
                }
            }
            if (AIPlayerBrain.this.entity.runtimeCanHarvestFromHere(target)) {
                AIPlayerBrain.this.entity.runtimeAdjustViewTo(target);
                AIPlayerBrain.this.entity.runtimeHarvestTarget(target, true);
                return BrainActionResult.RUNNING;
            }
            BlockPos approach = AIPlayerBrain.this.entity.runtimeFindApproachPosition(target);
            if (approach != null && AIPlayerBrain.this.entity.runtimeNavigateToPosition(approach, 1.02D)) {
                return BrainActionResult.RUNNING;
            }
            return BrainActionResult.BLOCKED;
        }
    }

    private final class CollectDropsAction extends BaseBrainAction {
        private CollectDropsAction(ActionTask task) {
            super(task);
        }

        @Override
        public BrainActionResult tick() {
            if (!AIPlayerBrain.this.entity.runtimeHasNearbyDrops()) {
                return BrainActionResult.SUCCESS;
            }
            return AIPlayerBrain.this.entity.runtimeCollectNearbyDrops() ? BrainActionResult.RUNNING : BrainActionResult.SUCCESS;
        }
    }

    private final class AttackTargetAction extends BaseBrainAction {
        private AttackTargetAction(ActionTask task) {
            super(task);
        }

        @Override
        public BrainActionResult tick() {
            BlockPos hostile = AIPlayerBrain.this.worldModel.hostilePos();
            if (hostile == null || !AIPlayerBrain.this.worldModel.hostileNearby()) {
                AIPlayerBrain.this.entity.runtimeLowerShieldGuard();
                return BrainActionResult.SUCCESS;
            }
            if (AIPlayerBrain.this.entity.runtimeShouldUseShieldNow()) {
                AIPlayerBrain.this.entity.runtimeRaiseShieldGuard();
            }
            AIPlayerBrain.this.entity.runtimeLookAt(Vec3.atCenterOf(hostile), 8);
            if (AIPlayerBrain.this.entity.blockPosition().distSqr(hostile) > 9.0D) {
                AIPlayerBrain.this.entity.runtimeNavigateToPosition(hostile, 1.1D);
            }
            return BrainActionResult.RUNNING;
        }
    }

    private final class PlaceBlockAction extends BaseBrainAction {
        private PlaceBlockAction(ActionTask task) {
            super(task);
        }

        @Override
        public BrainActionResult tick() {
            BlockPos target = AIPlayerBrain.this.resolveTarget(this.task.args());
            if (target == null) {
                target = AIPlayerBrain.this.entity.runtimeFindNextShelterPlacement();
            }
            if (target == null) {
                return BrainActionResult.SUCCESS;
            }
            if (!AIPlayerBrain.this.entity.runtimeHasBuildingMaterials() && !AIPlayerBrain.this.entity.runtimeEnsurePlanksSupply()) {
                return BrainActionResult.BLOCKED;
            }
            if (AIPlayerBrain.this.entity.blockPosition().distSqr(target) > 16.0D) {
                AIPlayerBrain.this.entity.runtimeNavigateToPosition(target, 1.0D);
                return BrainActionResult.RUNNING;
            }
            return AIPlayerBrain.this.entity.runtimePlaceShelterBlock(target) ? BrainActionResult.RUNNING : BrainActionResult.FAILURE;
        }
    }

    private final class BridgeAction extends BaseBrainAction {
        private BridgeAction(ActionTask task) {
            super(task);
        }

        @Override
        public BrainActionResult tick() {
            BlockPos target = AIPlayerBrain.this.resolveTarget(this.task.args());
            if (target == null) {
                return BrainActionResult.FAILURE;
            }
            if (AIPlayerBrain.this.entity.runtimeCanPlacePathSupport(target)) {
                return AIPlayerBrain.this.entity.runtimePlacePathSupport(target) ? BrainActionResult.SUCCESS : BrainActionResult.FAILURE;
            }
            BlockPos stand = target.below();
            return AIPlayerBrain.this.entity.runtimeNavigateToPosition(stand, 1.0D) ? BrainActionResult.RUNNING : BrainActionResult.BLOCKED;
        }
    }

    private final class TunnelAction extends BaseBrainAction {
        private TunnelAction(ActionTask task) {
            super(task);
        }

        @Override
        public BrainActionResult tick() {
            BlockPos target = AIPlayerBrain.this.resolveTarget(this.task.args());
            if (target == null) {
                return BrainActionResult.FAILURE;
            }
            return AIPlayerBrain.this.entity.runtimeTryDiggableAdvance(target) ? BrainActionResult.RUNNING : BrainActionResult.BLOCKED;
        }
    }

    private final class OpenContainerAction extends BaseBrainAction {
        private OpenContainerAction(ActionTask task) {
            super(task);
        }

        @Override
        public BrainActionResult tick() {
            BlockPos target = AIPlayerBrain.this.resolveTarget(this.task.args());
            if (target == null) {
                return BrainActionResult.FAILURE;
            }
            if (!AIPlayerBrain.this.entity.runtimeIsWithin(target, 9.0D)) {
                return AIPlayerBrain.this.entity.runtimeNavigateToPosition(target, 1.0D) ? BrainActionResult.RUNNING : BrainActionResult.BLOCKED;
            }
            AIPlayerBrain.this.entity.runtimeAdjustViewTo(target);
            return BrainActionResult.SUCCESS;
        }
    }

    private final class ConsumeFoodAction extends BaseBrainAction {
        private ConsumeFoodAction(ActionTask task) {
            super(task);
        }

        @Override
        public BrainActionResult tick() {
            return AIPlayerBrain.this.entity.runtimeConsumeRecoveryFood() ? BrainActionResult.SUCCESS : BrainActionResult.FAILURE;
        }
    }

    private final class RecoverAction extends BaseBrainAction {
        private RecoverAction(ActionTask task) {
            super(task);
        }

        @Override
        public BrainActionResult tick() {
            if (!AIPlayerBrain.this.worldModel.inHazard() && !AIPlayerBrain.this.worldModel.onFire() && !AIPlayerBrain.this.worldModel.stuckState().stuck()) {
                return BrainActionResult.SUCCESS;
            }
            return AIPlayerBrain.this.entity.runtimeAttemptImmediateRecovery() ? BrainActionResult.RUNNING : BrainActionResult.BLOCKED;
        }
    }

    private final class ObserveAction extends BaseBrainAction {
        private ObserveAction(ActionTask task) {
            super(task);
        }

        @Override
        public BrainActionResult tick() {
            ActionExecutionResult result = AIPlayerBrain.this.entity.executePlannedAction(PlannedAction.simple(GoapActionType.OBSERVE_AND_REPORT, this.label()), AIPlayerBrain.this.currentGoal);
            return toBrainResult(result);
        }
    }

    private final class CropHarvestAction extends BaseBrainAction {
        private CropHarvestAction(ActionTask task) {
            super(task);
        }

        @Override
        public BrainActionResult tick() {
            ActionExecutionResult result = AIPlayerBrain.this.entity.executePlannedAction(PlannedAction.simple(GoapActionType.HARVEST_CROP, this.label()), AIPlayerBrain.this.currentGoal);
            return toBrainResult(result);
        }
    }

    private final class DeliverItemAction extends BaseBrainAction {
        private DeliverItemAction(ActionTask task) {
            super(task);
        }

        @Override
        public BrainActionResult tick() {
            ActionExecutionResult result = AIPlayerBrain.this.entity.executePlannedAction(PlannedAction.simple(GoapActionType.DELIVER_ITEM, this.label()), AIPlayerBrain.this.currentGoal);
            return toBrainResult(result);
        }
    }

    private static BrainActionResult toBrainResult(ActionExecutionResult result) {
        if (result == null) {
            return BrainActionResult.FAILURE;
        }
        return switch (result) {
            case SUCCESS -> BrainActionResult.SUCCESS;
            case RUNNING -> BrainActionResult.RUNNING;
            case FAILED -> BrainActionResult.FAILURE;
        };
    }

    private static final class LocalSafetyPlanner {
        private LocalSafetyPlanner() {
        }

        private static TaskRequest chooseAutonomousTask(WorldModelSnapshot world) {
            if (world.pendingDelivery()) {
                return new TaskRequest(GoalType.DELIVER_ITEM.commandName(), GoalType.DELIVER_ITEM.displayName(), Map.of(), 90, false, "local", GoalType.FOLLOW_OWNER.commandName());
            }
            if (world.inHazard() || world.onFire() || world.stuckState().stuck()) {
                return new TaskRequest(GoalType.RECOVER_SELF.commandName(), GoalType.RECOVER_SELF.displayName(), Map.of(), 100, false, "local", GoalType.SURVIVE.commandName());
            }
            if (world.ownerUnderThreat() || world.hostileNearby()) {
                return new TaskRequest(GoalType.GUARD_OWNER.commandName(), GoalType.GUARD_OWNER.displayName(), Map.of(), 85, false, "local", GoalType.SURVIVE.commandName());
            }
            if (world.lowFood()) {
                return new TaskRequest(GoalType.COLLECT_FOOD.commandName(), GoalType.COLLECT_FOOD.displayName(), Map.of(), 70, false, "local", GoalType.SURVIVE.commandName());
            }
            if (world.night() && world.buildingUnits() >= 12 && !world.shelterReady()) {
                return new TaskRequest(GoalType.BUILD_SHELTER.commandName(), GoalType.BUILD_SHELTER.displayName(), Map.of(), 75, false, "local", GoalType.SURVIVE.commandName());
            }
            if (world.lowTools() || world.buildingUnits() < 12) {
                return new TaskRequest(GoalType.COLLECT_WOOD.commandName(), GoalType.COLLECT_WOOD.displayName(), Map.of(), 65, false, "local", GoalType.SURVIVE.commandName());
            }
            if (world.freeBackpackSlots() > 2) {
                return new TaskRequest(GoalType.COLLECT_ORE.commandName(), GoalType.COLLECT_ORE.displayName(), Map.of(), 55, false, "local", GoalType.SURVIVE.commandName());
            }
            if (world.ownerAvailable() && world.ownerPos() != null) {
                return new TaskRequest(GoalType.FOLLOW_OWNER.commandName(), GoalType.FOLLOW_OWNER.displayName(), Map.of(), 45, false, "local", GoalType.SURVIVE.commandName());
            }
            return new TaskRequest(GoalType.EXPLORE_AREA.commandName(), GoalType.EXPLORE_AREA.displayName(), Map.of(), 35, false, "local", GoalType.SURVIVE.commandName());
        }

        private static AgentBrainResponse emergencyPlan(AIPlayerEntity entity, WorldModelSnapshot world, SharedMemorySnapshot shared) {
            BehaviorNodeSpec subtree = BehaviorNodeSpec.sequence("RecoverFlow", List.of(
                    BehaviorNodeSpec.action("尝试进食", "consume_food", Map.of()),
                    BehaviorNodeSpec.action("脱离危险", "recover_self", Map.of()),
                    BehaviorNodeSpec.action("观察环境", "observe", Map.of())));
            TaskRequest task = new TaskRequest(GoalType.RECOVER_SELF.commandName(), GoalType.RECOVER_SELF.displayName(), Map.of(), 100, false, "local-emergency", GoalType.SURVIVE.commandName());
            return new AgentBrainResponse(task, task.priority(), ConstraintSet.empty(), subtree, TaskRequest.idle("fallback"), "本地安全兜底：优先脱困", "brain-local-emergency");
        }

        private static AgentBrainResponse combatPlan(AIPlayerEntity entity, WorldModelSnapshot world, SharedMemorySnapshot shared) {
            BehaviorNodeSpec subtree = BehaviorNodeSpec.sequence("CombatFlow", List.of(
                    BehaviorNodeSpec.action("靠近主人", "move_to", Map.of("target", "owner", "reachDistance", "9")),
                    BehaviorNodeSpec.action("压制敌对目标", "attack_target", Map.of("target", "hostile")),
                    BehaviorNodeSpec.action("观察环境", "observe", Map.of())));
            TaskRequest task = new TaskRequest(GoalType.GUARD_OWNER.commandName(), GoalType.GUARD_OWNER.displayName(), Map.of(), 85, false, "local-combat", GoalType.SURVIVE.commandName());
            return new AgentBrainResponse(task, task.priority(), ConstraintSet.empty(), subtree, TaskRequest.idle("fallback"), "本地战斗分支：保护主人并压制威胁", "brain-local-combat");
        }

        private static AgentBrainResponse idlePlan(AIPlayerEntity entity, WorldModelSnapshot world, SharedMemorySnapshot shared) {
            BehaviorNodeSpec subtree = BehaviorNodeSpec.selector("ExploreIdle", List.of(
                    BehaviorNodeSpec.sequence("FollowOwner", List.of(
                            BehaviorNodeSpec.condition("OwnerFar", "owner_far"),
                            BehaviorNodeSpec.action("回到主人附近", "move_to", Map.of("target", "owner", "reachDistance", "6", "speed", "1.05")))),
                    BehaviorNodeSpec.action("主动探索", "move_to", Map.of("target", "explore", "speed", "1.0")),
                    BehaviorNodeSpec.action("观察环境", "observe", Map.of())));
            TaskRequest task = new TaskRequest(GoalType.EXPLORE_AREA.commandName(), GoalType.EXPLORE_AREA.displayName(), Map.of(), 25, false, "local-idle", GoalType.SURVIVE.commandName());
            return new AgentBrainResponse(task, task.priority(), ConstraintSet.empty(), subtree, TaskRequest.idle("fallback"), "默认巡视与待机", "brain-local-idle");
        }

        private static AgentBrainResponse plan(AIPlayerEntity entity, WorldModelSnapshot world, SharedMemorySnapshot shared, TaskRequest preferred) {
            TaskRequest task = preferred == null ? chooseAutonomousTask(world) : preferred;
            BehaviorNodeSpec subtree = switch (task.goalType()) {
                case FOLLOW_OWNER -> BehaviorNodeSpec.sequence("FollowOwner", List.of(
                        BehaviorNodeSpec.action("接近主人", "move_to", Map.of("target", "owner", "reachDistance", "6", "speed", "1.08")),
                        BehaviorNodeSpec.action("观察环境", "observe", Map.of())));
                case GUARD_OWNER -> combatPlan(entity, world, shared).subtree();
                case RECOVER_SELF -> emergencyPlan(entity, world, shared).subtree();
                case COLLECT_WOOD -> BehaviorNodeSpec.sequence("GatherWood", List.of(
                        BehaviorNodeSpec.action("准备斧头", "equip_tool", Map.of("kind", "wood")),
                        BehaviorNodeSpec.action("贴近树木", "move_to", Map.of("target", "wood", "reachDistance", "6", "speed", "1.02")),
                        BehaviorNodeSpec.action("扫描并砍树", "chop_tree", Map.of("target", "wood")),
                        BehaviorNodeSpec.action("收集掉落物", "collect_drops", Map.of())));
                case COLLECT_ORE -> BehaviorNodeSpec.sequence("MineOre", List.of(
                        BehaviorNodeSpec.action("准备镐子", "equip_tool", Map.of("kind", "ore")),
                        BehaviorNodeSpec.action("靠近矿点", "move_to", Map.of("target", "ore", "reachDistance", "6", "speed", "1.0")),
                        BehaviorNodeSpec.action("采掘矿石", "mine_block", Map.of("target", "ore", "kind", "ore")),
                        BehaviorNodeSpec.action("收集掉落物", "collect_drops", Map.of())));
                case COLLECT_FOOD -> BehaviorNodeSpec.sequence("CollectFood", List.of(
                        BehaviorNodeSpec.action("寻找并收割作物", "harvest_crop", Map.of()),
                        BehaviorNodeSpec.action("尝试补充食物", "consume_food", Map.of()),
                        BehaviorNodeSpec.action("观察环境", "observe", Map.of())));
                case BUILD_SHELTER -> new BehaviorNodeSpec(null, "repeat_until", "BuildShelter", "shelter_ready", null, List.of(
                        BehaviorNodeSpec.sequence("ShelterLoop", List.of(
                                BehaviorNodeSpec.action("寻找建造点", "move_to", Map.of("target", "shelter_next", "reachDistance", "12", "speed", "1.0")),
                                BehaviorNodeSpec.action("放置庇护所方块", "place_block", Map.of("target", "shelter_next"))))), Map.of(), 0);
                case DELIVER_ITEM -> BehaviorNodeSpec.sequence("DeliverItem", List.of(
                        BehaviorNodeSpec.action("回到主人附近", "move_to", Map.of("target", "owner", "reachDistance", "6", "speed", "1.08")),
                        BehaviorNodeSpec.action("交付物品", "deliver_item", Map.of())));
                case EXPLORE_AREA -> BehaviorNodeSpec.sequence("ExploreArea", List.of(
                        BehaviorNodeSpec.action("前往探索点", "move_to", Map.of("target", "explore", "speed", "1.0")),
                        BehaviorNodeSpec.action("观察环境", "observe", Map.of())));
                case SURVIVE, TALK_ONLY, IDLE -> idlePlan(entity, world, shared).subtree();
            };
            return new AgentBrainResponse(task, task.priority(), ConstraintSet.empty(), subtree, TaskRequest.idle("fallback"), "本地任务树：" + task.label(), "brain-local-task");
        }
    }
}

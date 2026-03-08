package com.mcmod.aiplayers.entity;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;

final class ResourceGatherSystem {
    private final AIPlayerEntity entity;
    private final PathManager movementController;
    private final TreeScanner treeScanner = new TreeScanner();
    private final TreeSelector treeSelector = new TreeSelector();
    private final TreeExecutor treeExecutor = new TreeExecutor();
    private final OreScanner oreScanner = new OreScanner();
    private final VeinDetector veinDetector = new VeinDetector();
    private final OreSelector oreSelector = new OreSelector();
    private final MineExecutor mineExecutor = new MineExecutor();
    private final DropCollector dropCollector = new DropCollector();

    private GatherResourceTask activeTask;
    private ResourcePlan currentPlan = idlePlan();
    private TreeTarget activeTree;
    private ResourceCluster activeOre;
    private int gatheredAmount;
    private int failureStreak;

    ResourceGatherSystem(AIPlayerEntity entity, PathManager movementController) {
        this.entity = entity;
        this.movementController = movementController;
    }

    ResourcePlan currentPlan() {
        return this.currentPlan;
    }

    void cancel() {
        this.treeExecutor.cancel(this.entity);
        this.mineExecutor.cancel(this.entity);
        this.activeTask = null;
        this.activeTree = null;
        this.activeOre = null;
        this.gatheredAmount = 0;
        this.failureStreak = 0;
        this.currentPlan = idlePlan();
    }

    ResourceExecutionResult tick(ActionTask action, WorldModelSnapshot worldModel, SharedMemorySnapshot sharedMemory) {
        GatherResourceTask task = resolveTask(action);
        if (task == null) {
            return ResourceExecutionResult.failure("resource_task_missing");
        }
        if (this.activeTask == null || !this.activeTask.key().equals(task.key())) {
            this.cancel();
            this.activeTask = task;
            this.currentPlan = new ResourcePlan(task, ResourcePlanStep.SCAN, "开始统一资源采集", 0, null);
        }
        return switch (task.kind()) {
            case TREE -> tickTrees(task, worldModel, sharedMemory);
            case ORE -> tickOres(task, worldModel, sharedMemory);
            case CROP -> tickCrops(task);
            case ANIMAL -> tickAnimals(task);
        };
    }

    private ResourceExecutionResult tickTrees(GatherResourceTask task, WorldModelSnapshot worldModel, SharedMemorySnapshot sharedMemory) {
        if (this.gatheredAmount >= task.amount()) {
            this.currentPlan = new ResourcePlan(task, ResourcePlanStep.COMPLETE, "树木采集完成", this.gatheredAmount, this.activeTree == null ? null : this.activeTree.base());
            return ResourceExecutionResult.success(0, "树木目标达成");
        }
        if (this.activeTree == null) {
            List<TreeTarget> trees = this.treeScanner.scan(this.entity, worldModel);
            this.currentPlan = new ResourcePlan(task, ResourcePlanStep.SCAN, "扫描树木目标", this.gatheredAmount, null);
            if (trees.isEmpty()) {
                BlockPos shared = sharedMemory == null ? null : sharedMemory.nearestResource("wood", this.entity.blockPosition());
                if (shared != null) {
                    this.entity.runtimeNavigateToPosition(shared, 1.02D);
                    this.currentPlan = new ResourcePlan(task, ResourcePlanStep.PATH, "前往共享树木位置", this.gatheredAmount, shared);
                    return ResourceExecutionResult.running("前往共享树木位置");
                }
                this.currentPlan = new ResourcePlan(task, ResourcePlanStep.FAILED, "未发现可采树木", this.gatheredAmount, null);
                return ResourceExecutionResult.blocked("未发现可采树木");
            }
            this.activeTree = this.treeSelector.select(this.entity, trees, sharedMemory);
            if (this.activeTree == null) {
                this.currentPlan = new ResourcePlan(task, ResourcePlanStep.FAILED, "未选出可用树木目标", this.gatheredAmount, null);
                return ResourceExecutionResult.blocked("未选出可用树木目标");
            }
            this.treeExecutor.start(this.activeTree);
            this.currentPlan = new ResourcePlan(task, ResourcePlanStep.SELECT, "锁定树木目标", this.gatheredAmount, this.activeTree.base());
        }
        ResourceExecutionResult result = this.treeExecutor.tick(this.entity, this.movementController, this.dropCollector);
        return handleStructuredResult(task, result, this.activeTree == null ? null : this.activeTree.base(), true);
    }

    private ResourceExecutionResult tickOres(GatherResourceTask task, WorldModelSnapshot worldModel, SharedMemorySnapshot sharedMemory) {
        if (this.gatheredAmount >= task.amount()) {
            this.currentPlan = new ResourcePlan(task, ResourcePlanStep.COMPLETE, "矿物采集完成", this.gatheredAmount, this.activeOre == null ? null : this.activeOre.seed());
            return ResourceExecutionResult.success(0, "矿物目标达成");
        }
        if (this.activeOre == null) {
            List<BlockPos> nodes = this.oreScanner.scanOreNodes(this.entity, worldModel);
            List<ResourceCluster> veins = this.veinDetector.detectVeins(this.entity, nodes);
            this.currentPlan = new ResourcePlan(task, ResourcePlanStep.SCAN, "扫描矿脉目标", this.gatheredAmount, null);
            if (veins.isEmpty()) {
                BlockPos shared = sharedMemory == null ? null : sharedMemory.nearestResource("ore", this.entity.blockPosition());
                if (shared != null) {
                    this.entity.runtimeNavigateToPosition(shared, 1.0D);
                    this.currentPlan = new ResourcePlan(task, ResourcePlanStep.PATH, "前往共享矿点位置", this.gatheredAmount, shared);
                    return ResourceExecutionResult.running("前往共享矿点位置");
                }
                this.currentPlan = new ResourcePlan(task, ResourcePlanStep.FAILED, "未发现可采矿脉", this.gatheredAmount, null);
                return ResourceExecutionResult.blocked("未发现可采矿脉");
            }
            this.activeOre = this.oreSelector.select(this.entity, veins, sharedMemory);
            if (this.activeOre == null) {
                this.currentPlan = new ResourcePlan(task, ResourcePlanStep.FAILED, "未选出可用矿脉目标", this.gatheredAmount, null);
                return ResourceExecutionResult.blocked("未选出可用矿脉目标");
            }
            this.mineExecutor.start(this.activeOre);
            this.currentPlan = new ResourcePlan(task, ResourcePlanStep.SELECT, "锁定矿脉目标", this.gatheredAmount, this.activeOre.seed());
        }
        ResourceExecutionResult result = this.mineExecutor.tick(this.entity, this.movementController, this.dropCollector);
        return handleStructuredResult(task, result, this.activeOre == null ? null : this.activeOre.seed(), false);
    }

    private ResourceExecutionResult tickCrops(GatherResourceTask task) {
        this.currentPlan = new ResourcePlan(task, ResourcePlanStep.GATHER, "执行作物收割适配层", this.gatheredAmount, this.entity.blockPosition());
        ActionExecutionResult result = this.entity.executePlannedAction(PlannedAction.simple(GoapActionType.HARVEST_CROP, "统一收割作物"), AgentGoal.of(GoalType.COLLECT_FOOD, "brain", "统一资源采集"));
        return switch (result) {
            case RUNNING -> ResourceExecutionResult.running("收割成熟作物");
            case SUCCESS -> {
                this.gatheredAmount = Math.min(task.amount(), this.gatheredAmount + 1);
                yield this.gatheredAmount >= task.amount() ? ResourceExecutionResult.success(1, "作物采集完成") : ResourceExecutionResult.running(1, "继续寻找成熟作物");
            }
            case FAILED -> ResourceExecutionResult.failure("作物收割失败");
        };
    }

    private ResourceExecutionResult tickAnimals(GatherResourceTask task) {
        String targetId = task.targetId().isBlank() ? task.targetHint() : task.targetId();
        if (!this.entity.runtimeHasActiveHuntDirective()) {
            if (targetId == null || targetId.isBlank()) {
                return ResourceExecutionResult.blocked("动物任务缺少 targetId");
            }
            this.entity.startAnimalHunt(null, targetId, targetId);
        }
        this.currentPlan = new ResourcePlan(task, ResourcePlanStep.GATHER, "执行动物狩猎适配层", this.gatheredAmount, this.entity.blockPosition());
        if (this.entity.runtimePerformHuntDirective()) {
            return ResourceExecutionResult.running("被动生物狩猎中");
        }
        this.gatheredAmount = Math.min(task.amount(), this.gatheredAmount + 1);
        return this.gatheredAmount >= task.amount()
                ? ResourceExecutionResult.success(1, "动物采集完成")
                : ResourceExecutionResult.running(1, "继续搜索目标动物");
    }

    private ResourceExecutionResult handleStructuredResult(GatherResourceTask task, ResourceExecutionResult result, BlockPos focusPos, boolean treeTask) {
        this.gatheredAmount += Math.max(0, result.progressDelta());
        ResourcePlanStep step = switch (result.status()) {
            case RUNNING -> isGatherStep(result.statusText()) ? ResourcePlanStep.GATHER : ResourcePlanStep.PATH;
            case SUCCESS -> this.gatheredAmount >= task.amount() ? ResourcePlanStep.COMPLETE : ResourcePlanStep.COLLECT;
            case BLOCKED, FAILURE -> ResourcePlanStep.FAILED;
        };
        this.currentPlan = new ResourcePlan(task, step, result.statusText(), this.gatheredAmount, focusPos);
        if (result.status() == ResourceExecutionStatus.SUCCESS) {
            this.failureStreak = 0;
            if (this.gatheredAmount < task.amount()) {
                clearTarget(treeTask);
                return ResourceExecutionResult.running(result.progressDelta(), "切换下一处资源目标");
            }
            return result;
        }
        if (result.status() == ResourceExecutionStatus.BLOCKED || result.status() == ResourceExecutionStatus.FAILURE) {
            this.failureStreak++;
            if (focusPos != null) {
                TeamMemoryService.recordFailure(this.entity, result.statusText(), focusPos);
            }
            clearTarget(treeTask);
            if (this.failureStreak <= 2) {
                return ResourceExecutionResult.running(result.statusText() + "，切换资源目标");
            }
        }
        return result;
    }

    private void clearTarget(boolean treeTask) {
        if (treeTask) {
            this.activeTree = null;
            this.treeExecutor.cancel(this.entity);
            return;
        }
        this.activeOre = null;
        this.mineExecutor.cancel(this.entity);
    }

    private boolean isGatherStep(String status) {
        String normalized = status == null ? "" : status.toLowerCase(Locale.ROOT);
        return normalized.contains("砍") || normalized.contains("采") || normalized.contains("挖") || normalized.contains("收割") || normalized.contains("狩猎");
    }

    private GatherResourceTask resolveTask(ActionTask action) {
        if (action == null) {
            return null;
        }
        Map<String, String> args = action.args();
        ResourceKind kind = ResourceKind.fromText(firstNonBlank(args.get("kind"), args.get("resource"), args.get("resourceKind"), args.get("target")));
        if (kind == null) {
            kind = switch (action.actionType()) {
                case "chop_tree" -> ResourceKind.TREE;
                case "mine_block" -> ResourceKind.ORE;
                case "harvest_crop" -> ResourceKind.CROP;
                default -> null;
            };
        }
        if (kind == null) {
            return null;
        }
        int amount = parsePositive(args.get("amount"), defaultAmount(kind));
        String targetHint = firstNonBlank(args.get("target"), kind.worldKind());
        String targetId = firstNonBlank(args.get("targetId"), "");
        return new GatherResourceTask(kind, amount, targetHint, targetId, false, "brain-action");
    }

    private int defaultAmount(ResourceKind kind) {
        return switch (kind) {
            case TREE -> 8;
            case ORE -> 4;
            case CROP, ANIMAL -> 1;
        };
    }

    private int parsePositive(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static ResourcePlan idlePlan() {
        return new ResourcePlan(new GatherResourceTask(ResourceKind.TREE, 1, "", "", false, "brain"), ResourcePlanStep.SCAN, "idle", 0, null);
    }
}

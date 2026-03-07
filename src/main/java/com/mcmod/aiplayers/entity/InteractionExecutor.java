package com.mcmod.aiplayers.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class InteractionExecutor {
    private InteractionExecutor() {
    }

    public static ActionExecutionResult execute(AIPlayerEntity entity, InteractionPlan plan) {
        if (entity == null || plan == null) {
            return ActionExecutionResult.FAILED;
        }
        if (plan.actions().isEmpty()) {
            if (!plan.emptyReason().isBlank()) {
                entity.runtimeRemember("交互", plan.emptyReason());
            }
            return plan.emptyResult();
        }
        for (InteractionAction action : plan.actions()) {
            ActionExecutionResult result = executeStep(entity, action);
            if (result != ActionExecutionResult.SUCCESS) {
                return result;
            }
        }
        return ActionExecutionResult.SUCCESS;
    }

    private static ActionExecutionResult executeStep(AIPlayerEntity entity, InteractionAction action) {
        return switch (action.type()) {
            case MOVE_NEAR -> executeMove(entity, action);
            case LOOK_AT -> executeLook(entity, action);
            case ADJUST_VIEW -> executeAdjustView(entity, action);
            case EQUIP_TOOL -> entity.runtimePrepareHarvestTool(action.woodTask())
                    ? ActionExecutionResult.SUCCESS
                    : ActionExecutionResult.RUNNING;
            case RAISE_SHIELD -> entity.runtimeRaiseShieldGuard()
                    ? ActionExecutionResult.SUCCESS
                    : ActionExecutionResult.RUNNING;
            case LOWER_SHIELD -> {
                entity.runtimeLowerShieldGuard();
                yield ActionExecutionResult.SUCCESS;
            }
            case CLEAR_PATH -> executeClearPath(entity, action);
            case BREAK_TARGET -> executeBreakTarget(entity, action);
            case PLACE_BLOCK -> executePlaceBlock(entity, action);
            case COLLECT_DROPS -> entity.runtimeCollectNearbyDrops() || !entity.runtimeHasNearbyDrops()
                    ? ActionExecutionResult.SUCCESS
                    : ActionExecutionResult.RUNNING;
            case CRAFT_PLANKS -> entity.runtimeEnsurePlanksSupply()
                    ? ActionExecutionResult.SUCCESS
                    : ActionExecutionResult.FAILED;
            case CRAFT_BREAD -> entity.runtimeCraftBread() || !entity.runtimeHasLowFoodSupply()
                    ? ActionExecutionResult.SUCCESS
                    : ActionExecutionResult.RUNNING;
            case CRAFT_TOOL -> entity.runtimeCraftBasicTools() || !entity.runtimeHasLowTools()
                    ? ActionExecutionResult.SUCCESS
                    : ActionExecutionResult.RUNNING;
        };
    }

    private static ActionExecutionResult executeMove(AIPlayerEntity entity, InteractionAction action) {
        BlockPos target = action.targetPos();
        if (target == null) {
            return ActionExecutionResult.FAILED;
        }
        if (entity.runtimeIsWithin(target, action.completionDistanceSqr())) {
            return ActionExecutionResult.SUCCESS;
        }
        if (!entity.runtimeNavigateToPosition(target, action.speed())) {
            entity.runtimeRemember("交互", "移动受阻，继续重试：" + action.label());
            return ActionExecutionResult.RUNNING;
        }
        return ActionExecutionResult.RUNNING;
    }

    private static ActionExecutionResult executeLook(AIPlayerEntity entity, InteractionAction action) {
        BlockPos target = action.targetPos();
        if (target == null) {
            return ActionExecutionResult.SUCCESS;
        }
        entity.runtimeLookAt(Vec3.atCenterOf(target), 10);
        return ActionExecutionResult.SUCCESS;
    }

    private static ActionExecutionResult executeAdjustView(AIPlayerEntity entity, InteractionAction action) {
        BlockPos target = action.targetPos();
        if (target == null) {
            return ActionExecutionResult.SUCCESS;
        }
        return entity.runtimeAdjustViewTo(target) ? ActionExecutionResult.SUCCESS : ActionExecutionResult.RUNNING;
    }

    private static ActionExecutionResult executeClearPath(AIPlayerEntity entity, InteractionAction action) {
        BlockPos target = action.targetPos();
        if (target == null) {
            return ActionExecutionResult.FAILED;
        }
        if (!entity.runtimeHasBreakablePathBlock(target, action.woodTask())) {
            return ActionExecutionResult.SUCCESS;
        }
        if (!entity.runtimeCanHarvestFromHere(target)) {
            BlockPos approach = entity.runtimeFindApproachPosition(target);
            BlockPos moveTarget = approach != null ? approach : entity.runtimeResolveMovementTarget(target);
            if (moveTarget == null) {
                return ActionExecutionResult.RUNNING;
            }
            if (!entity.runtimeNavigateToPosition(moveTarget, action.speed())) {
                return ActionExecutionResult.RUNNING;
            }
            entity.runtimeLookAt(Vec3.atCenterOf(target), 10);
            return ActionExecutionResult.RUNNING;
        }
        return entity.runtimeBreakPathBlock(target, action.woodTask())
                ? ActionExecutionResult.SUCCESS
                : ActionExecutionResult.FAILED;
    }

    private static ActionExecutionResult executeBreakTarget(AIPlayerEntity entity, InteractionAction action) {
        BlockPos target = action.targetPos();
        if (target == null) {
            return ActionExecutionResult.FAILED;
        }
        if (!entity.runtimeIsValidHarvestTarget(target, action.woodTask())) {
            return ActionExecutionResult.SUCCESS;
        }
        if (!entity.runtimeCanHarvestFromHere(target)) {
            BlockPos obstacle = entity.runtimeFindHarvestObstacle(target, action.woodTask());
            BlockPos focus = obstacle != null ? obstacle : target;
            BlockPos approach = entity.runtimeFindApproachPosition(focus);
            BlockPos moveTarget = approach != null ? approach : entity.runtimeResolveMovementTarget(focus);
            if (moveTarget == null) {
                return ActionExecutionResult.RUNNING;
            }
            if (!entity.runtimeNavigateToPosition(moveTarget, action.speed())) {
                return ActionExecutionResult.RUNNING;
            }
            entity.runtimeLookAt(Vec3.atCenterOf(focus), 10);
            return ActionExecutionResult.RUNNING;
        }
        return entity.runtimeHarvestTarget(target, action.woodTask())
                ? ActionExecutionResult.SUCCESS
                : ActionExecutionResult.FAILED;
    }

    private static ActionExecutionResult executePlaceBlock(AIPlayerEntity entity, InteractionAction action) {
        BlockPos target = action.targetPos();
        if (target == null) {
            return ActionExecutionResult.FAILED;
        }
        if (!entity.runtimeCanPlaceShelterBlockAt(target)) {
            return ActionExecutionResult.SUCCESS;
        }
        if (!entity.runtimeIsWithin(target, action.completionDistanceSqr())) {
            BlockPos approach = entity.runtimeFindApproachPosition(target);
            BlockPos moveTarget = approach != null ? approach : entity.runtimeResolveMovementTarget(target);
            if (moveTarget == null) {
                return ActionExecutionResult.RUNNING;
            }
            if (!entity.runtimeNavigateToPosition(moveTarget, action.speed())) {
                return ActionExecutionResult.RUNNING;
            }
            entity.runtimeLookAt(Vec3.atCenterOf(target), 10);
            return ActionExecutionResult.RUNNING;
        }
        return entity.runtimePlaceShelterBlock(target)
                ? ActionExecutionResult.SUCCESS
                : ActionExecutionResult.FAILED;
    }
}

package com.mcmod.aiplayers.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

public final class InteractionPlanner {
    private InteractionPlanner() {
    }

    public static InteractionPlan forPlannedAction(AIPlayerEntity entity, PlannedAction action, AgentGoal goal) {
        if (entity == null || action == null) {
            return null;
        }
        return switch (action.type()) {
            case CHOP_TREE -> buildHarvestPlan(entity, true);
            case MINE_ORE -> buildHarvestPlan(entity, false);
            case BUILD_SHELTER -> buildShelterPlan(entity);
            case ACQUIRE_TOOL -> InteractionPlan.of(action.label(), List.of(InteractionAction.craftTool("补齐基础工具")));
            case CRAFT_PLANKS -> InteractionPlan.of(action.label(), List.of(InteractionAction.craftPlanks("把原木加工成木板")));
            case CRAFT_BREAD -> InteractionPlan.of(action.label(), List.of(InteractionAction.craftBread("把小麦加工成面包")));
            default -> null;
        };
    }

    private static InteractionPlan buildHarvestPlan(AIPlayerEntity entity, boolean woodTask) {
        BlockPos target = entity.runtimeResolveHarvestTarget(woodTask);
        if (target == null) {
            return InteractionPlan.failed(woodTask ? "砍树交互" : "采矿交互", woodTask ? "未找到可采树木" : "未找到可采矿点");
        }

        List<InteractionAction> actions = new ArrayList<>();
        BlockPos obstacle = entity.runtimeFindHarvestObstacle(target, woodTask);
        BlockPos focus = obstacle != null ? obstacle : target;
        BlockPos approach = entity.runtimeFindApproachPosition(focus);
        BlockPos moveTarget = approach != null ? approach : entity.runtimeResolveMovementTarget(focus);
        if (moveTarget != null && !entity.runtimeIsWithin(moveTarget, 4.0D)) {
            actions.add(InteractionAction.moveNear(moveTarget, 1.05D, 4.0D, obstacle != null ? "靠近障碍并准备清障" : "靠近资源准备交互"));
        }
        actions.add(InteractionAction.lookAt(focus, obstacle != null ? "观察路径障碍" : "观察资源方块"));
        if (obstacle != null) {
            actions.add(InteractionAction.clearPath(obstacle, woodTask, woodTask ? "清理树叶遮挡" : "挖开矿点遮挡"));
        } else {
            actions.add(InteractionAction.breakTarget(target, woodTask, woodTask ? "砍下树干" : "采掘矿点"));
            actions.add(InteractionAction.collectDrops("回收掉落物"));
        }
        return InteractionPlan.of(woodTask ? "砍树交互" : "采矿交互", actions);
    }

    private static InteractionPlan buildShelterPlan(AIPlayerEntity entity) {
        if (!entity.runtimeHasBuildingMaterials()) {
            return InteractionPlan.failed("建造交互", "当前建筑材料不足");
        }
        BlockPos placement = entity.runtimeFindNextShelterPlacement();
        if (placement == null) {
            return InteractionPlan.success("建造交互", "避难所已完成");
        }

        List<InteractionAction> actions = new ArrayList<>();
        if (!entity.runtimeHasPlankSupply()) {
            actions.add(InteractionAction.craftPlanks("先把原木加工成木板"));
        }
        BlockPos obstacle = entity.runtimeFindNavigationObstacle(placement);
        BlockPos focus = obstacle != null ? obstacle : placement;
        BlockPos approach = entity.runtimeFindApproachPosition(focus);
        BlockPos moveTarget = approach != null ? approach : entity.runtimeResolveMovementTarget(focus);
        if (moveTarget != null && !entity.runtimeIsWithin(moveTarget, 4.0D)) {
            actions.add(InteractionAction.moveNear(moveTarget, 1.0D, 4.0D, obstacle != null ? "靠近建造阻挡并准备清障" : "靠近建造点"));
        }
        actions.add(InteractionAction.lookAt(focus, obstacle != null ? "观察建造路径阻挡" : "对准建造点"));
        if (obstacle != null) {
            actions.add(InteractionAction.clearPath(obstacle, false, "清理建造路径阻挡"));
        } else {
            actions.add(InteractionAction.placeBlock(placement, "放置建筑方块"));
        }
        return InteractionPlan.of("建造交互", actions);
    }
}

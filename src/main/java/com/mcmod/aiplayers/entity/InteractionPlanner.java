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
            BlockPos searchTarget = entity.resolveRuntimeTarget("explore", entity.blockPosition());
            if (searchTarget != null && !entity.runtimeIsWithin(searchTarget, 2.56D)) {
                return InteractionPlan.of(woodTask ? "搜索树木交互" : "搜索矿点交互", List.of(
                        InteractionAction.recoverStuck("先尝试脱困"),
                        InteractionAction.equipTool(woodTask, woodTask ? "准备斧头" : "准备镐子"),
                        InteractionAction.moveNear(searchTarget, 1.0D, 2.56D, woodTask ? "搜索附近树木" : "搜索附近矿点"),
                        InteractionAction.adjustView(searchTarget, woodTask ? "抬头观察树冠线索" : "观察地形与矿线"),
                        InteractionAction.lookAt(searchTarget, woodTask ? "观察树木线索" : "观察矿点线索")));
            }
            return InteractionPlan.failed(woodTask ? "砍树交互" : "采矿交互", woodTask ? "未找到可采树木" : "未找到可采矿点");
        }

        List<InteractionAction> actions = new ArrayList<>();
        actions.add(InteractionAction.recoverStuck("先处理当前卡位"));
        actions.add(InteractionAction.equipTool(woodTask, woodTask ? "切换斧头采集" : "切换镐子采集"));
        if (entity.runtimeShouldUseShieldNow()) {
            actions.add(InteractionAction.raiseShield("进入采集前先举盾防御"));
        }
        BlockPos obstacle = entity.runtimeFindHarvestObstacle(target, woodTask);
        BlockPos focus = obstacle != null ? obstacle : target;
        BlockPos approach = entity.runtimeFindApproachPosition(focus);
        BlockPos moveTarget = approach != null ? approach : entity.runtimeResolveMovementTarget(focus);
        if (moveTarget != null && !entity.runtimeIsWithin(moveTarget, 2.56D)) {
            actions.add(InteractionAction.moveNear(moveTarget, 1.05D, 2.56D, obstacle != null ? "靠近障碍并准备清障" : "靠近资源准备交互"));
        }
        actions.add(InteractionAction.adjustView(focus, obstacle != null ? "调整视角以清障" : "调整视角锁定资源"));
        actions.add(InteractionAction.lookAt(focus, obstacle != null ? "观察路径障碍" : "观察资源方块"));
        if (obstacle != null) {
            actions.add(InteractionAction.clearPath(obstacle, woodTask, woodTask ? "清理树叶遮挡" : "挖开矿点遮挡"));
        } else {
            actions.add(InteractionAction.lowerShield("切换到采集动作"));
            actions.add(InteractionAction.breakTarget(target, woodTask, woodTask ? "砍下树干" : "采掘矿点"));
            actions.add(InteractionAction.collectDrops("回收掉落物"));
        }
        actions.add(InteractionAction.lowerShield("结束当前采集防御状态"));
        return InteractionPlan.of(woodTask ? "砍树交互" : "采矿交互", actions);
    }

    private static InteractionPlan buildShelterPlan(AIPlayerEntity entity) {
        if (!entity.runtimeHasBuildingMaterials()) {
            BlockPos searchTarget = entity.resolveRuntimeTarget("explore", entity.blockPosition());
            if (searchTarget != null && !entity.runtimeIsWithin(searchTarget, 2.56D)) {
                return InteractionPlan.of("建材搜索交互", List.of(
                        InteractionAction.recoverStuck("先处理卡位后再搜索建材"),
                        InteractionAction.moveNear(searchTarget, 1.0D, 2.56D, "搜索可用建材"),
                        InteractionAction.lookAt(searchTarget, "观察周边可采资源")));
            }
            return InteractionPlan.failed("建造交互", "当前建筑材料不足");
        }

        BlockPos placement = entity.runtimeFindNextShelterPlacement();
        if (placement == null) {
            return InteractionPlan.success("建造交互", "避难所已完成");
        }

        List<InteractionAction> actions = new ArrayList<>();
        actions.add(InteractionAction.recoverStuck("先处理当前卡位"));
        actions.add(InteractionAction.equipTool(true, "切换建造工具"));
        if (entity.runtimeShouldUseShieldNow()) {
            actions.add(InteractionAction.raiseShield("建造前先举盾观察威胁"));
        }
        if (!entity.runtimeHasPlankSupply()) {
            actions.add(InteractionAction.craftPlanks("先把原木加工成木板"));
        }
        BlockPos obstacle = entity.runtimeFindNavigationObstacle(placement);
        BlockPos focus = obstacle != null ? obstacle : placement;
        BlockPos approach = entity.runtimeFindApproachPosition(focus);
        BlockPos moveTarget = approach != null ? approach : entity.runtimeResolveMovementTarget(focus);
        if (moveTarget != null && !entity.runtimeIsWithin(moveTarget, 2.56D)) {
            actions.add(InteractionAction.moveNear(moveTarget, 1.0D, 2.56D, obstacle != null ? "靠近建造阻挡并准备清障" : "靠近建造点"));
        }
        actions.add(InteractionAction.adjustView(focus, obstacle != null ? "抬头检查建造阻挡" : "抬头对准建造点"));
        actions.add(InteractionAction.lookAt(focus, obstacle != null ? "观察建造路径阻挡" : "对准建造点"));
        if (obstacle != null) {
            actions.add(InteractionAction.clearPath(obstacle, false, "清理建造路径阻挡"));
        } else {
            actions.add(InteractionAction.lowerShield("切换到放置动作"));
            actions.add(InteractionAction.placeBlock(placement, "放置建筑方块"));
        }
        actions.add(InteractionAction.lowerShield("结束当前建造防御状态"));
        return InteractionPlan.of("建造交互", actions);
    }
}

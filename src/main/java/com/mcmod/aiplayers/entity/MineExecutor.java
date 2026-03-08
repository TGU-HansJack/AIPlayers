package com.mcmod.aiplayers.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;

final class MineExecutor implements ResourceExecutor<ResourceCluster> {
    private ResourceCluster target;
    private final List<BlockPos> remainingOres = new ArrayList<>();

    @Override
    public void start(ResourceCluster target) {
        this.target = target;
        this.remainingOres.clear();
        if (target != null) {
            this.remainingOres.addAll(target.blocks());
        }
    }

    @Override
    public ResourceExecutionResult tick(AIPlayerEntity entity, PathManager movementController, DropCollector dropCollector) {
        if (entity == null || this.target == null) {
            return ResourceExecutionResult.failure("ore_target_missing");
        }
        if (!entity.runtimePrepareHarvestTool(false)) {
            return ResourceExecutionResult.blocked("缺少可用镐子");
        }
        int progress = pruneBrokenOres(entity);
        if (this.remainingOres.isEmpty()) {
            ResourceExecutionResult collect = dropCollector.tick(entity, null, this.target.seed());
            if (collect.status() == ResourceExecutionStatus.RUNNING) {
                return ResourceExecutionResult.running(progress + collect.progressDelta(), "回收矿物掉落物");
            }
            return ResourceExecutionResult.success(progress, "矿脉采掘完成");
        }
        BlockPos ore = selectNextOre(entity);
        if (ore == null) {
            return ResourceExecutionResult.success(progress, "矿脉已清空");
        }
        BlockPos obstacle = entity.runtimeFindHarvestObstacle(ore, false);
        if (obstacle != null) {
            if (entity.runtimeCanHarvestFromHere(obstacle) && entity.runtimeHasBreakablePathBlock(obstacle, false)) {
                entity.runtimeBreakPathBlock(obstacle, false);
                return ResourceExecutionResult.running(progress, "打通矿脉入口");
            }
            BlockPos tunnelStand = entity.runtimeFindApproachPosition(obstacle);
            if (tunnelStand != null) {
                entity.runtimeNavigateToPosition(tunnelStand, 1.0D);
                return ResourceExecutionResult.running(progress, "调整局部隧道站位");
            }
        }
        if (entity.runtimeCanHarvestFromHere(ore)) {
            entity.runtimeAdjustViewTo(ore);
            if (entity.runtimeHarvestTarget(ore, false)) {
                return ResourceExecutionResult.running(progress, "采掘矿脉");
            }
        }
        BlockPos stand = nearestStand(entity);
        if (stand != null) {
            entity.runtimeNavigateToPosition(stand, 1.0D);
            return ResourceExecutionResult.running(progress, "前往矿脉站位");
        }
        if (entity.runtimeTryDiggableAdvance(ore)) {
            return ResourceExecutionResult.running(progress, "尝试局部 tunneling");
        }
        return ResourceExecutionResult.blocked("无法推进到矿脉");
    }

    @Override
    public void cancel(AIPlayerEntity entity) {
        this.target = null;
        this.remainingOres.clear();
    }

    @Override
    public ResourceCluster currentTarget() {
        return this.target;
    }

    private int pruneBrokenOres(AIPlayerEntity entity) {
        int progress = 0;
        for (Iterator<BlockPos> iterator = this.remainingOres.iterator(); iterator.hasNext(); ) {
            BlockPos pos = iterator.next();
            if (!entity.runtimeIsValidHarvestTarget(pos, false)) {
                iterator.remove();
                progress++;
            }
        }
        return progress;
    }

    private BlockPos selectNextOre(AIPlayerEntity entity) {
        if (this.remainingOres.isEmpty()) {
            return null;
        }
        this.remainingOres.sort(Comparator
                .comparing((BlockPos pos) -> !entity.runtimeCanHarvestFromHere(pos))
                .thenComparingDouble(pos -> entity.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)));
        return this.remainingOres.getFirst();
    }

    private BlockPos nearestStand(AIPlayerEntity entity) {
        if (this.target == null || this.target.standPositions().isEmpty()) {
            return null;
        }
        return this.target.standPositions().stream()
                .min(Comparator.comparingDouble(pos -> entity.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)))
                .orElse(null);
    }
}

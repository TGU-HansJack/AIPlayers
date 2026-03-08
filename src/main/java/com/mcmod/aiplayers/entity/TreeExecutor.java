package com.mcmod.aiplayers.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

final class TreeExecutor implements ResourceExecutor<TreeTarget> {
    private final TreeScanner scanner = new TreeScanner();
    private TreeTarget target;
    private final List<BlockPos> remainingLogs = new ArrayList<>();

    @Override
    public void start(TreeTarget target) {
        this.target = target;
        this.remainingLogs.clear();
        if (target != null) {
            this.remainingLogs.addAll(target.logs());
            this.remainingLogs.sort(Comparator.comparingInt(BlockPos::getY));
        }
    }

    @Override
    public ResourceExecutionResult tick(AIPlayerEntity entity, PathManager movementController, DropCollector dropCollector) {
        if (entity == null || this.target == null) {
            return ResourceExecutionResult.failure("tree_target_missing");
        }
        if (!entity.runtimePrepareHarvestTool(true)) {
            return ResourceExecutionResult.blocked("缺少可用斧头");
        }
        int progress = pruneBrokenLogs(entity);
        if (progress > 0) {
            revalidate(entity);
        }
        if (this.remainingLogs.isEmpty()) {
            ResourceExecutionResult collect = dropCollector.tick(entity, null, this.target.base());
            if (collect.status() == ResourceExecutionStatus.RUNNING) {
                return ResourceExecutionResult.running(progress + collect.progressDelta(), "回收树木掉落物");
            }
            return ResourceExecutionResult.success(progress, "整棵树采集完成");
        }
        BlockPos log = this.remainingLogs.getFirst();
        BlockPos stand = nearestStand(entity);
        if (stand != null && !entity.runtimeIsWithin(stand, 5.0D) && !entity.runtimeCanHarvestFromHere(log)) {
            entity.runtimeNavigateToPosition(stand, 1.02D);
            return ResourceExecutionResult.running(progress, "前往树干站位");
        }
        BlockPos obstacle = entity.runtimeFindHarvestObstacle(log, true);
        if (obstacle != null) {
            if (entity.runtimeCanHarvestFromHere(obstacle)) {
                entity.runtimeBreakPathBlock(obstacle, true);
                return ResourceExecutionResult.running(progress, "清理树木障碍");
            }
            BlockPos approachObstacle = entity.runtimeFindApproachPosition(obstacle);
            if (approachObstacle != null) {
                entity.runtimeNavigateToPosition(approachObstacle, 1.02D);
                return ResourceExecutionResult.running(progress, "调整到清障站位");
            }
        }
        if (entity.runtimeCanHarvestFromHere(log)) {
            entity.runtimeAdjustViewTo(log);
            if (entity.runtimeHarvestTarget(log, true)) {
                return ResourceExecutionResult.running(progress, "自底向上砍树");
            }
        }
        BlockPos approach = stand != null ? stand : entity.runtimeFindApproachPosition(log);
        if (approach != null) {
            entity.runtimeNavigateToPosition(approach, 1.02D);
            return ResourceExecutionResult.running(progress, "贴近树干");
        }
        return ResourceExecutionResult.blocked("无法贴近树木");
    }

    @Override
    public void cancel(AIPlayerEntity entity) {
        this.target = null;
        this.remainingLogs.clear();
    }

    @Override
    public TreeTarget currentTarget() {
        return this.target;
    }

    private int pruneBrokenLogs(AIPlayerEntity entity) {
        int progress = 0;
        while (!this.remainingLogs.isEmpty() && !entity.runtimeIsValidHarvestTarget(this.remainingLogs.getFirst(), true)) {
            this.remainingLogs.removeFirst();
            progress++;
        }
        return progress;
    }

    private void revalidate(AIPlayerEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || this.target == null) {
            return;
        }
        TreeTarget refreshed = this.scanner.scanTree(level, entity, this.target.base());
        if (refreshed == null) {
            return;
        }
        this.target = refreshed;
        this.remainingLogs.clear();
        for (BlockPos log : refreshed.logs()) {
            if (entity.runtimeIsValidHarvestTarget(log, true)) {
                this.remainingLogs.add(log);
            }
        }
        this.remainingLogs.sort(Comparator.comparingInt(BlockPos::getY));
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

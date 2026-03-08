package com.mcmod.aiplayers.entity;

import net.minecraft.core.BlockPos;

final class DropCollector {
    ResourceExecutionResult tick(AIPlayerEntity entity, WorldModelSnapshot worldModel, BlockPos focusPos) {
        if (entity == null) {
            return ResourceExecutionResult.failure("drop_collector_no_entity");
        }
        if (entity.runtimeHasNearbyDrops()) {
            entity.runtimeCollectNearbyDrops();
            return ResourceExecutionResult.running("收集附近掉落物");
        }
        if (worldModel != null && worldModel.droppedItemPos() != null) {
            entity.runtimeNavigateToPosition(worldModel.droppedItemPos(), 1.02D);
            return ResourceExecutionResult.running("前往掉落物");
        }
        if (focusPos != null && !entity.runtimeIsWithin(focusPos, 12.0D)) {
            entity.runtimeNavigateToPosition(focusPos, 1.0D);
            return ResourceExecutionResult.running("返回采集点检查掉落物");
        }
        return ResourceExecutionResult.success(0, "掉落物回收完成");
    }
}

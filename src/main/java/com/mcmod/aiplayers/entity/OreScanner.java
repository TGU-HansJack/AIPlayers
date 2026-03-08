package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.knowledge.KnowledgeManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

final class OreScanner {
    private static final int SCAN_RADIUS = 16;

    List<BlockPos> scanOreNodes(AIPlayerEntity entity, WorldModelSnapshot worldModel) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) {
            return List.of();
        }
        BlockPos origin = entity.blockPosition();
        List<BlockPos> ores = new ArrayList<>();
        BlockPos min = origin.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS);
        BlockPos max = origin.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (KnowledgeManager.isKnownOre(state, pos.getY())) {
                ores.add(pos.immutable());
            }
        }
        ores.sort(Comparator.comparingDouble(origin::distSqr));
        return ores;
    }
}

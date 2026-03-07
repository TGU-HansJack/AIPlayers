package com.mcmod.aiplayers.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class AgentPathPlanner {
    private AgentPathPlanner() {
    }

    public static List<PathNode> plan(AIPlayerEntity entity, BlockPos target) {
        if (target == null) {
            return List.of();
        }
        return PathCache.getOrCompute(entity, target, AgentConfigManager.getConfig().movementProfile(), () -> compute(entity, target));
    }

    private static List<PathNode> compute(AIPlayerEntity entity, BlockPos target) {
        List<PathNode> nodes = new ArrayList<>();
        Path path = entity.getNavigation().createPath(target, 0);
        if (path != null && path.getNodeCount() > 0) {
            for (int index = path.getNextNodeIndex(); index < path.getNodeCount(); index++) {
                nodes.add(new PathNode(path.getEntityPosAtNode(entity, index)));
            }
        }
        if (nodes.isEmpty()) {
            nodes.add(new PathNode(Vec3.atCenterOf(target)));
        }
        return nodes;
    }
}

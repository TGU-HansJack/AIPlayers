package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.PathGoal;
import com.mcmod.aiplayers.vendor.baritone.pathing.calc.AStarPathFinder;
import com.mcmod.aiplayers.vendor.baritone.pathing.calc.PathCalculationResult;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.CalculationContext;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.Movement;
import com.mcmod.aiplayers.vendor.baritone.pathing.path.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class AgentPathPlanner {
    private static final long PRIMARY_TIMEOUT_MS = 30L;
    private static final long FAILURE_TIMEOUT_MS = 60L;

    private AgentPathPlanner() {
    }

    public static List<PathNode> plan(AIPlayerEntity entity, BlockPos target) {
        if (entity == null || target == null) {
            return List.of();
        }
        return PathCache.getOrCompute(entity, target, AgentConfigManager.getConfig().movementProfile(), () -> {
            Path path = plan(entity, BaritoneGoalAdapter.forBlock(target));
            return toPathNodes(path);
        });
    }

    public static Path plan(AIPlayerEntity entity, PathGoal goal) {
        if (entity == null || goal == null) {
            return null;
        }
        BaritoneEntityContextAdapter contextAdapter = new BaritoneEntityContextAdapter(entity);
        CalculationContext calculationContext = new CalculationContext(contextAdapter);
        AStarPathFinder pathFinder = new AStarPathFinder(contextAdapter.playerFeet(), goal, calculationContext);
        PathCalculationResult result = pathFinder.calculate(PRIMARY_TIMEOUT_MS, FAILURE_TIMEOUT_MS);
        return result.path();
    }

    static List<PathNode> toPathNodes(Path path) {
        if (path == null || path.movements().isEmpty()) {
            return List.of();
        }
        List<PathNode> nodes = new ArrayList<>(path.movements().size());
        for (Movement movement : path.movements()) {
            if (movement == null) {
                continue;
            }
            nodes.add(PathNode.withAction(
                    Vec3.atCenterOf(movement.getDest()),
                    resolveAction(movement),
                    movement.getActionPos(),
                    movement.jumpRequired()));
        }
        return nodes;
    }

    static PathPlan toPlan(BlockPos target, Path path, String status) {
        if (path == null || path.movements().isEmpty()) {
            return new PathPlan(target, List.of(), status);
        }
        List<PathStep> steps = new ArrayList<>(path.movements().size());
        for (Movement movement : path.movements()) {
            if (movement == null) {
                continue;
            }
            steps.add(new PathStep(
                    Vec3.atCenterOf(movement.getDest()),
                    resolvePrimitive(movement),
                    movement.getActionPos(),
                    movement.jumpRequired()));
        }
        return new PathPlan(target, steps, status);
    }

    private static PathPrimitive resolvePrimitive(Movement movement) {
        String name = movement == null ? "" : movement.name();
        if (name.contains("Break")) {
            return PathPrimitive.BREAK;
        }
        if (name.contains("Place")) {
            return PathPrimitive.PLACE;
        }
        if (name.contains("Diagonal")) {
            return PathPrimitive.DIAGONAL;
        }
        if (name.contains("Ascend") || movement.jumpRequired()) {
            return PathPrimitive.ASCEND;
        }
        if (name.contains("Descend") || name.contains("Downward") || name.contains("Fall")) {
            return PathPrimitive.DESCEND;
        }
        return PathPrimitive.WALK;
    }

    private static PathNodeAction resolveAction(Movement movement) {
        PathPrimitive primitive = resolvePrimitive(movement);
        return switch (primitive) {
            case BREAK -> PathNodeAction.BREAK_BLOCK;
            case PLACE -> PathNodeAction.PLACE_SUPPORT;
            case ASCEND -> movement != null && movement.jumpRequired() ? PathNodeAction.JUMP_ASCEND : PathNodeAction.NONE;
            default -> PathNodeAction.NONE;
        };
    }
}

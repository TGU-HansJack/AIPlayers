package com.mcmod.aiplayers.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class AgentPathPlanner {
    private static final int MAX_CANDIDATE_TARGETS = 10;
    private static final int MAX_SMOOTH_LOOKAHEAD = 4;

    private AgentPathPlanner() {
    }

    public static List<PathNode> plan(AIPlayerEntity entity, BlockPos target) {
        if (target == null) {
            return List.of();
        }
        return PathCache.getOrCompute(entity, target, AgentConfigManager.getConfig().movementProfile(), () -> compute(entity, target));
    }

    private static List<PathNode> compute(AIPlayerEntity entity, BlockPos target) {
        List<BlockPos> candidates = collectCandidateTargets(entity, target);
        List<PathNode> bestNodes = List.of();
        double bestScore = Double.MAX_VALUE;

        for (BlockPos candidate : candidates) {
            List<PathNode> nodes = computeRaw(entity, candidate);
            double score = scorePath(entity, target, candidate, nodes);
            if (score < bestScore) {
                bestScore = score;
                bestNodes = nodes;
            }
        }

        if (bestNodes.isEmpty()) {
            BlockPos resolved = entity.runtimeResolveMovementTarget(target);
            BlockPos fallback = resolved != null ? resolved : target;
            return List.of(new PathNode(Vec3.atCenterOf(fallback)));
        }
        return smooth(entity, bestNodes);
    }

    private static List<BlockPos> collectCandidateTargets(AIPlayerEntity entity, BlockPos target) {
        LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
        BlockPos resolved = entity.runtimeResolveMovementTarget(target);
        addCandidate(candidates, resolved);
        addCandidate(candidates, target);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            addCandidate(candidates, target.relative(direction));
            addCandidate(candidates, target.relative(direction).below());
            addCandidate(candidates, target.relative(direction, 2));
            addCandidate(candidates, target.relative(direction, 2).below());
            addCandidate(candidates, target.relative(direction).above());
        }
        addCandidate(candidates, target.north().east());
        addCandidate(candidates, target.north().west());
        addCandidate(candidates, target.south().east());
        addCandidate(candidates, target.south().west());

        return candidates.stream()
                .filter(candidate -> candidate.equals(target) || entity.runtimeCanStandAt(candidate))
                .sorted(Comparator
                        .comparingDouble((BlockPos candidate) -> candidate.distSqr(target))
                        .thenComparingDouble(candidate -> entity.distanceToSqr(Vec3.atCenterOf(candidate))))
                .limit(MAX_CANDIDATE_TARGETS)
                .toList();
    }

    private static void addCandidate(LinkedHashSet<BlockPos> candidates, BlockPos pos) {
        if (pos != null) {
            candidates.add(pos.immutable());
        }
    }

    private static List<PathNode> computeRaw(AIPlayerEntity entity, BlockPos target) {
        List<PathNode> nodes = new ArrayList<>();
        Path path = entity.getNavigation().createPath(target, 0);
        if (path != null && path.getNodeCount() > 0) {
            for (int index = path.getNextNodeIndex(); index < path.getNodeCount(); index++) {
                nodes.add(new PathNode(path.getEntityPosAtNode(entity, index)));
            }
        }
        if (nodes.isEmpty() && entity.runtimeCanDirectlyTraverse(entity.position(), Vec3.atCenterOf(target))) {
            nodes.add(new PathNode(Vec3.atCenterOf(target)));
        }
        return nodes;
    }

    private static double scorePath(AIPlayerEntity entity, BlockPos originalTarget, BlockPos actualTarget, List<PathNode> nodes) {
        if (nodes.isEmpty()) {
            return Double.MAX_VALUE;
        }
        Vec3 last = nodes.get(nodes.size() - 1).position();
        double pathDistance = entity.position().distanceToSqr(nodes.get(0).position());
        for (int i = 1; i < nodes.size(); i++) {
            pathDistance += nodes.get(i - 1).position().distanceToSqr(nodes.get(i).position());
        }
        double targetOffset = last.distanceToSqr(Vec3.atCenterOf(originalTarget));
        double landingOffset = actualTarget.distSqr(originalTarget);
        return pathDistance + targetOffset * 2.0D + landingOffset * 3.0D + nodes.size() * 0.35D;
    }

    private static List<PathNode> smooth(AIPlayerEntity entity, List<PathNode> nodes) {
        if (nodes.size() <= 2) {
            return nodes;
        }
        List<PathNode> smoothed = new ArrayList<>();
        Vec3 anchor = entity.position();
        int index = 0;
        while (index < nodes.size()) {
            int bestIndex = index;
            int limit = Math.min(nodes.size() - 1, index + MAX_SMOOTH_LOOKAHEAD);
            for (int probe = limit; probe > index; probe--) {
                if (entity.runtimeCanDirectlyTraverse(anchor, nodes.get(probe).position())) {
                    bestIndex = probe;
                    break;
                }
            }
            smoothed.add(nodes.get(bestIndex));
            anchor = nodes.get(bestIndex).position();
            index = bestIndex + 1;
        }
        return smoothed;
    }
}

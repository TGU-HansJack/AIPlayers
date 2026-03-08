package com.mcmod.aiplayers.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class AgentPathPlanner {
    private static final int MAX_CANDIDATE_TARGETS = 24;
    private static final int MAX_SMOOTH_LOOKAHEAD = 4;
    private static final int SEARCH_NODE_LIMIT = 3200;
    private static final int MAX_STEP_HEIGHT = 2;
    private static final int MAX_DROP_HEIGHT = 3;

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
        CustomNodeEvaluator evaluator = new CustomNodeEvaluator(entity);

        for (BlockPos candidate : candidates) {
            List<PathNode> nodes = computeRaw(entity, candidate, evaluator);
            double score = scorePath(entity, target, candidate, nodes);
            if (score < bestScore) {
                bestScore = score;
                bestNodes = nodes;
            }
        }

        if (bestNodes.isEmpty()) {
            BlockPos resolved = entity.runtimeResolveMovementTarget(target);
            if (resolved != null && entity.runtimeCanStandAt(resolved, true)) {
                return List.of(PathNode.move(Vec3.atCenterOf(resolved)));
            }
            return List.of();
        }
        return smooth(entity, bestNodes);
    }

    private static List<BlockPos> collectCandidateTargets(AIPlayerEntity entity, BlockPos target) {
        LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
        BlockPos resolved = entity.runtimeResolveMovementTarget(target);
        addCandidate(candidates, resolved);
        addCandidate(candidates, target);
        ServerPlayer owner = entity.getRuntimeOwnerPlayer();
        if (owner != null && (entity.getMode() == AIPlayerMode.FOLLOW || entity.getMode() == AIPlayerMode.GUARD || entity.getMode() == AIPlayerMode.IDLE)) {
            addCandidate(candidates, owner.blockPosition());
            addCandidate(candidates, owner.blockPosition().above());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                addCandidate(candidates, owner.blockPosition().relative(direction));
            }
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int step = 1; step <= 4; step++) {
                BlockPos lateral = target.relative(direction, step);
                addCandidate(candidates, lateral);
                addCandidate(candidates, lateral.above());
                addCandidate(candidates, lateral.below());
                addCandidate(candidates, lateral.below().below());
            }
        }
        addCandidate(candidates, target.north().east());
        addCandidate(candidates, target.north().west());
        addCandidate(candidates, target.south().east());
        addCandidate(candidates, target.south().west());
        addCandidate(candidates, target.north(2).east(2));
        addCandidate(candidates, target.north(2).west(2));
        addCandidate(candidates, target.south(2).east(2));
        addCandidate(candidates, target.south(2).west(2));

        for (int radius = 1; radius <= 3; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) {
                        continue;
                    }
                    for (int y = -2; y <= 2; y++) {
                        addCandidate(candidates, target.offset(x, y, z));
                    }
                }
            }
        }

        return candidates.stream()
                .filter(candidate -> candidate.equals(target) || entity.runtimeCanStandAt(candidate, true))
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

    private static List<PathNode> computeRaw(AIPlayerEntity entity, BlockPos target, CustomNodeEvaluator evaluator) {
        BlockPos start = entity.runtimeResolveMovementTarget(entity.blockPosition());
        if (start == null) {
            start = entity.blockPosition();
        }
        if (entity.runtimeCanDirectlyTraverse(entity.position(), Vec3.atCenterOf(target))) {
            return List.of(PathNode.move(Vec3.atCenterOf(target)));
        }

        PriorityQueue<SearchNode> frontier = new PriorityQueue<>(Comparator.comparingDouble(SearchNode::fScore));
        Map<BlockPos, SearchNode> best = new HashMap<>();
        SearchNode startNode = new SearchNode(start, 0.0D, heuristic(start, target), null, PathNodeAction.NONE, null, false);
        frontier.add(startNode);
        best.put(start, startNode);

        int expansions = 0;
        while (!frontier.isEmpty() && expansions < SEARCH_NODE_LIMIT) {
            SearchNode current = frontier.poll();
            expansions++;
            if (hasReached(current.pos(), target)) {
                return buildPath(current);
            }
            for (PathStep step : neighbors(entity, current.pos(), evaluator)) {
                double nextG = current.gScore() + step.cost();
                SearchNode previous = best.get(step.to());
                if (previous != null && previous.gScore() <= nextG) {
                    continue;
                }
                SearchNode next = new SearchNode(
                        step.to(),
                        nextG,
                        nextG + heuristic(step.to(), target),
                        current,
                        step.action(),
                        step.actionPos(),
                        step.jumpRequired());
                best.put(step.to(), next);
                frontier.add(next);
            }
        }

        return List.of();
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
        double ownerBias = 0.0D;
        ServerPlayer owner = entity.getRuntimeOwnerPlayer();
        if (owner != null && (entity.getMode() == AIPlayerMode.FOLLOW || entity.getMode() == AIPlayerMode.GUARD || entity.getMode() == AIPlayerMode.IDLE)) {
            ownerBias = last.distanceToSqr(owner.position()) * 0.35D;
        }
        return pathDistance + targetOffset * 2.0D + landingOffset * 3.0D + nodes.size() * 0.35D + ownerBias;
    }

    private static List<PathNode> smooth(AIPlayerEntity entity, List<PathNode> nodes) {
        if (nodes.size() <= 2) {
            return nodes;
        }
        List<PathNode> smoothed = new ArrayList<>();
        Vec3 anchor = entity.position();
        int index = 0;
        while (index < nodes.size()) {
            if (nodes.get(index).action() != PathNodeAction.NONE) {
                smoothed.add(nodes.get(index));
                anchor = nodes.get(index).position();
                index++;
                continue;
            }
            int bestIndex = index;
            int limit = Math.min(nodes.size() - 1, index + MAX_SMOOTH_LOOKAHEAD);
            for (int probe = limit; probe > index; probe--) {
                if (nodes.get(probe).action() != PathNodeAction.NONE) {
                    continue;
                }
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

    private static List<PathStep> neighbors(AIPlayerEntity entity, BlockPos from, CustomNodeEvaluator evaluator) {
        List<PathStep> steps = new ArrayList<>();
        boolean fromWater = evaluator.isWaterNode(from);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos base = from.relative(direction);

            BlockPos same = base;
            if (evaluator.canStandAt(same, true) && !evaluator.isLavaNode(same)) {
                steps.add(new PathStep(same, evaluator.movementPenalty(same, false, PathNodeAction.NONE), PathNodeAction.NONE, null, false));
            }

            for (int up = 1; up <= MAX_STEP_HEIGHT; up++) {
                BlockPos upPos = base.above(up);
                if (evaluator.canStandAt(upPos, true) && !evaluator.isLavaNode(upPos)) {
                    steps.add(new PathStep(upPos, evaluator.movementPenalty(upPos, true, PathNodeAction.JUMP_ASCEND), PathNodeAction.JUMP_ASCEND, null, true));
                    break;
                }
            }

            for (int down = 1; down <= MAX_DROP_HEIGHT; down++) {
                BlockPos downPos = base.below(down);
                if (evaluator.canStandAt(downPos, true) && !evaluator.isLavaNode(downPos)) {
                    steps.add(new PathStep(downPos, evaluator.movementPenalty(downPos, false, PathNodeAction.NONE) + 0.2D * down, PathNodeAction.NONE, null, false));
                    break;
                }
            }

            if (fromWater && evaluator.isWaterNode(base) && !evaluator.isLavaNode(base)) {
                steps.add(new PathStep(base, evaluator.movementPenalty(base, false, PathNodeAction.NONE), PathNodeAction.NONE, null, true));
            }

            BlockPos feetBlock = base;
            BlockPos headBlock = base.above();
            if (evaluator.canBreakForPath(feetBlock) || evaluator.canBreakForPath(headBlock)) {
                BlockPos clearTarget = evaluator.canBreakForPath(feetBlock) ? feetBlock : headBlock;
                if (evaluator.canStandAt(base, true) || evaluator.isWaterNode(base) || evaluator.canOccupyAfterBreak(base, clearTarget)) {
                    PathNodeAction action = PathNodeAction.BREAK_BLOCK;
                    double penalty = evaluator.movementPenalty(base, false, action) + evaluator.breakPenalty(clearTarget);
                    if (evaluator.isSoftBreakable(clearTarget)) {
                        penalty = Math.max(0.85D, penalty - 0.25D);
                    }
                    steps.add(new PathStep(base, penalty, action, clearTarget, false));
                }
            }

            BlockPos supportPos = base.below();
            if (evaluator.canPlaceSupportAt(supportPos) && evaluator.canStandAt(base, true)) {
                PathNodeAction action = PathNodeAction.PLACE_SUPPORT;
                steps.add(new PathStep(base, evaluator.movementPenalty(base, true, action), action, supportPos, true));
            }

            if (evaluator.isWaterNode(base) && !evaluator.isLavaNode(base)) {
                BlockPos swimUp = base.above();
                if (evaluator.canStandAt(swimUp, true) && !evaluator.isLavaNode(swimUp)) {
                    steps.add(new PathStep(swimUp, evaluator.movementPenalty(swimUp, true, PathNodeAction.NONE) + 0.06D, PathNodeAction.NONE, null, true));
                }
                BlockPos swimDown = base.below();
                if (evaluator.canStandAt(swimDown, true) && !evaluator.isLavaNode(swimDown)) {
                    steps.add(new PathStep(swimDown, evaluator.movementPenalty(swimDown, false, PathNodeAction.NONE) + 0.08D, PathNodeAction.NONE, null, false));
                }
            }
        }
        return steps;
    }

    private static double heuristic(BlockPos from, BlockPos to) {
        return Math.abs(from.getX() - to.getX())
                + Math.abs(from.getY() - to.getY()) * 1.35D
                + Math.abs(from.getZ() - to.getZ());
    }

    private static boolean hasReached(BlockPos pos, BlockPos target) {
        int dx = Math.abs(pos.getX() - target.getX());
        int dy = Math.abs(pos.getY() - target.getY());
        int dz = Math.abs(pos.getZ() - target.getZ());
        return dx <= 1 && dz <= 1 && dy <= 1;
    }

    private static List<PathNode> buildPath(SearchNode tail) {
        List<PathNode> reversed = new ArrayList<>();
        SearchNode cursor = tail;
        while (cursor != null) {
            reversed.add(PathNode.withAction(Vec3.atCenterOf(cursor.pos()), cursor.action(), cursor.actionPos(), cursor.jumpRequired()));
            cursor = cursor.parent();
        }
        List<PathNode> path = new ArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            path.add(reversed.get(i));
        }
        if (!path.isEmpty()) {
            path.remove(0);
        }
        return path;
    }

    private record SearchNode(
            BlockPos pos,
            double gScore,
            double fScore,
            SearchNode parent,
            PathNodeAction action,
            BlockPos actionPos,
            boolean jumpRequired) {
    }

    private record PathStep(
            BlockPos to,
            double cost,
            PathNodeAction action,
            BlockPos actionPos,
            boolean jumpRequired) {
    }
}

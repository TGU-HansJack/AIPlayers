package com.mcmod.aiplayers.vendor.baritone.pathing.calc;

import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.Goal;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.CalculationContext;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.Movement;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.Moves;
import com.mcmod.aiplayers.vendor.baritone.pathing.path.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.core.BlockPos;

// Upstream reference: baritone-1.21.11/src/main/java/baritone/pathing/calc/AStarPathFinder.java
public final class AStarPathFinder extends AbstractNodeCostSearch {
    private static final int DEFAULT_NODE_LIMIT = 16000;
    private static final double COST_EPSILON = 1.0E-6D;

    private final int nodeLimit;
    private int nodesConsidered;

    public AStarPathFinder(BlockPos start, Goal goal, CalculationContext context) {
        this(start, goal, context, DEFAULT_NODE_LIMIT);
    }

    public AStarPathFinder(BlockPos start, Goal goal, CalculationContext context, int nodeLimit) {
        super(start, goal, context);
        this.nodeLimit = Math.max(1024, nodeLimit);
    }

    public int getNodesConsidered() {
        return this.nodesConsidered;
    }

    @Override
    protected PathCalculationResult calculate0(long primaryTimeoutMs, long failureTimeoutMs) {
        if (this.goal == null) {
            return new PathCalculationResult(PathCalculationResult.Type.FAILURE, null);
        }
        BlockPos actualStart = this.context.context.resolveMovementTarget(this.start);
        if (actualStart == null) {
            actualStart = this.start;
        }
        SearchNode startNode = new SearchNode(actualStart, null, null, 0.0D, this.goal.heuristic(actualStart));
        if (this.goal.isInGoal(actualStart)) {
            return new PathCalculationResult(PathCalculationResult.Type.SUCCESS_TO_GOAL, new Path(actualStart, List.of(), true, 0));
        }

        PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator.comparingDouble(SearchNode::combinedCost));
        Map<BlockPos, SearchNode> bestByPos = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();
        open.add(startNode);
        bestByPos.put(actualStart, startNode);

        long startedAt = System.currentTimeMillis();
        long deadline = startedAt + Math.max(10L, Math.max(primaryTimeoutMs, failureTimeoutMs));
        SearchNode bestPartial = startNode;
        double bestPartialScore = partialScore(startNode);
        this.nodesConsidered = 0;

        while (!open.isEmpty() && this.nodesConsidered < this.nodeLimit && !this.cancelRequested) {
            if (System.currentTimeMillis() >= deadline) {
                break;
            }
            SearchNode current = open.poll();
            if (!closed.add(current.pos())) {
                continue;
            }
            this.nodesConsidered++;

            if (this.goal.isInGoal(current.pos())) {
                return new PathCalculationResult(PathCalculationResult.Type.SUCCESS_TO_GOAL, this.buildPath(actualStart, current, true));
            }

            double currentPartialScore = partialScore(current);
            if (currentPartialScore + COST_EPSILON < bestPartialScore
                    || (Math.abs(currentPartialScore - bestPartialScore) <= COST_EPSILON && current.heuristic() < bestPartial.heuristic())) {
                bestPartial = current;
                bestPartialScore = currentPartialScore;
            }

            for (Movement movement : Moves.generateAll(this.context, current.pos())) {
                BlockPos nextPos = movement.getDest();
                if (nextPos == null || !this.context.bsi.isLoaded(nextPos) || this.context.isLavaNode(nextPos)) {
                    continue;
                }
                double tentativeCost = current.cost() + movement.getCost();
                SearchNode known = bestByPos.get(nextPos);
                if (known != null && tentativeCost + COST_EPSILON >= known.cost()) {
                    continue;
                }
                SearchNode updated = new SearchNode(nextPos, current, movement, tentativeCost, this.goal.heuristic(nextPos));
                bestByPos.put(nextPos, updated);
                open.add(updated);
            }
        }

        if (this.cancelRequested) {
            return new PathCalculationResult(PathCalculationResult.Type.CANCELLED, null);
        }
        if (bestPartial != null && !bestPartial.pos().equals(actualStart)) {
            return new PathCalculationResult(
                    bestPartial.reachesGoal(this.goal) ? PathCalculationResult.Type.SUCCESS_TO_GOAL : PathCalculationResult.Type.SUCCESS_SEGMENT,
                    this.buildPath(actualStart, bestPartial, bestPartial.reachesGoal(this.goal)));
        }
        return new PathCalculationResult(PathCalculationResult.Type.FAILURE, null);
    }

    private Path buildPath(BlockPos actualStart, SearchNode goalNode, boolean reachesGoal) {
        List<Movement> movements = new ArrayList<>();
        SearchNode cursor = goalNode;
        while (cursor != null && cursor.previous() != null && cursor.via() != null) {
            movements.add(cursor.via());
            cursor = cursor.previous();
        }
        java.util.Collections.reverse(movements);
        return new Path(actualStart, movements, reachesGoal, this.nodesConsidered);
    }

    private static double partialScore(SearchNode node) {
        return node.heuristic() + node.cost() * 0.18D;
    }

    private record SearchNode(BlockPos pos, SearchNode previous, Movement via, double cost, double heuristic) {
        private SearchNode {
            pos = pos == null ? BlockPos.ZERO : pos.immutable();
        }

        double combinedCost() {
            return this.cost + this.heuristic;
        }

        boolean reachesGoal(Goal goal) {
            return goal != null && goal.isInGoal(this.pos);
        }
    }
}

package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.vendor.baritone.pathing.calc.AStarPathFinder;
import com.mcmod.aiplayers.vendor.baritone.pathing.calc.PathCalculationResult;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.CalculationContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import com.mcmod.aiplayers.vendor.baritone.api.pathing.goals.PathGoal;

public final class AsyncPathfindingService {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)),
            new PathThreadFactory());

    private AsyncPathfindingService() {
    }

    public static CompletableFuture<PathTaskResult> submit(long token, BlockPos start, PathGoal goal, CalculationContext context) {
        return CompletableFuture.supplyAsync(() -> {
            AStarPathFinder pathFinder = new AStarPathFinder(start, goal, context, context.searchNodeLimit());
            PathCalculationResult result = pathFinder.calculate(context.primaryTimeoutMs(), context.failureTimeoutMs());
            return new PathTaskResult(token, result);
        }, EXECUTOR);
    }

    public record PathTaskResult(long token, PathCalculationResult result) {
    }

    private static final class PathThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "AIPlayers-Pathfinder-" + COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}

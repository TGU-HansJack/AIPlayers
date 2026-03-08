package com.mcmod.aiplayers.entity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

public record SharedMemorySnapshot(
        String teamId,
        Map<String, List<BlockPos>> resourceLocations,
        List<BlockPos> dangerZones,
        List<BlockPos> baseLocations,
        List<String> exploredChunks,
        List<String> recentFailures,
        List<String> taskOutcomes) {
    public SharedMemorySnapshot {
        resourceLocations = resourceLocations == null ? Map.of() : copy(resourceLocations);
        dangerZones = dangerZones == null ? List.of() : copyPositions(dangerZones);
        baseLocations = baseLocations == null ? List.of() : copyPositions(baseLocations);
        exploredChunks = exploredChunks == null ? List.of() : List.copyOf(exploredChunks);
        recentFailures = recentFailures == null ? List.of() : List.copyOf(recentFailures);
        taskOutcomes = taskOutcomes == null ? List.of() : List.copyOf(taskOutcomes);
        teamId = teamId == null ? "solo" : teamId;
    }

    public static SharedMemorySnapshot empty() {
        return new SharedMemorySnapshot("solo", Map.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public BlockPos nearestResource(String kind, BlockPos origin) {
        if (kind == null || origin == null) {
            return null;
        }
        List<BlockPos> positions = this.resourceLocations.get(kind.toLowerCase());
        if (positions == null || positions.isEmpty()) {
            return null;
        }
        return positions.stream().min((left, right) -> Double.compare(left.distSqr(origin), right.distSqr(origin))).orElse(null);
    }

    public String summary() {
        return "team=" + this.teamId
                + " resources=" + this.resourceLocations.keySet()
                + " dangers=" + this.dangerZones.size()
                + " bases=" + this.baseLocations.size()
                + " explored=" + this.exploredChunks.size()
                + " failures=" + this.recentFailures.size()
                + " outcomes=" + this.taskOutcomes.size();
    }

    private static Map<String, List<BlockPos>> copy(Map<String, List<BlockPos>> source) {
        Map<String, List<BlockPos>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<BlockPos>> entry : source.entrySet()) {
            copy.put(entry.getKey(), copyPositions(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private static List<BlockPos> copyPositions(List<BlockPos> source) {
        return source == null ? List.of() : source.stream().filter(pos -> pos != null).map(BlockPos::immutable).toList();
    }
}

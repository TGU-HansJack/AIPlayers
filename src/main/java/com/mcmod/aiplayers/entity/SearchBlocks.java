package com.mcmod.aiplayers.entity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class SearchBlocks {
    private static final Set<Long> VISITED = ConcurrentHashMap.newKeySet();

    private SearchBlocks() {
    }

    public static void resetVisited() {
        VISITED.clear();
    }

    public static BlockPos searchNearest(
            ServerLevel level,
            BlockPos origin,
            int horizontalRadius,
            int verticalDown,
            int verticalUp,
            BlockMatcher matcher) {
        if (level == null || origin == null || matcher == null) {
            return null;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;
        double originX = origin.getX() + 0.5D;
        double originY = origin.getY() + 0.5D;
        double originZ = origin.getZ() + 0.5D;

        for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
            for (int y = -verticalDown; y <= verticalUp; y++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (!VISITED.add(cursor.asLong())) {
                        continue;
                    }

                    BlockState state = level.getBlockState(cursor);
                    if (!matcher.matches(state, cursor)) {
                        continue;
                    }

                    double dx = (cursor.getX() + 0.5D) - originX;
                    double dy = (cursor.getY() + 0.5D) - originY;
                    double dz = (cursor.getZ() + 0.5D) - originZ;
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = cursor.immutable();
                    }
                }
            }
        }

        return bestPos;
    }

    @FunctionalInterface
    public interface BlockMatcher {
        boolean matches(BlockState state, BlockPos pos);
    }
}

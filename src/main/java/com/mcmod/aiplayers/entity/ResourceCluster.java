package com.mcmod.aiplayers.entity;

import java.util.List;
import net.minecraft.core.BlockPos;

public record ResourceCluster(
        ResourceKind kind,
        BlockPos seed,
        List<BlockPos> blocks,
        List<BlockPos> standPositions,
        int exposedFaces,
        int blockingBlocks) implements ResourceTarget {
    public ResourceCluster {
        kind = kind == null ? ResourceKind.ORE : kind;
        seed = seed == null ? BlockPos.ZERO : seed.immutable();
        blocks = blocks == null ? List.of() : blocks.stream().filter(pos -> pos != null).map(BlockPos::immutable).toList();
        standPositions = standPositions == null ? List.of() : standPositions.stream().filter(pos -> pos != null).map(BlockPos::immutable).toList();
        exposedFaces = Math.max(0, exposedFaces);
        blockingBlocks = Math.max(0, blockingBlocks);
    }

    @Override
    public BlockPos seedPos() {
        return this.seed;
    }

    @Override
    public String label() {
        return this.kind.displayName() + "@" + this.seed.getX() + "," + this.seed.getY() + "," + this.seed.getZ();
    }
}

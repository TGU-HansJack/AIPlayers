package com.mcmod.aiplayers.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record TreeTarget(
        BlockPos base,
        List<BlockPos> standPositions,
        List<BlockPos> logs,
        List<BlockPos> leaves,
        BoundingBox canopyBounds) implements ResourceTarget {
    public TreeTarget {
        base = base == null ? BlockPos.ZERO : base.immutable();
        standPositions = standPositions == null ? List.of() : standPositions.stream().filter(pos -> pos != null).map(BlockPos::immutable).toList();
        logs = logs == null ? List.of() : logs.stream().filter(pos -> pos != null).map(BlockPos::immutable).toList();
        leaves = leaves == null ? List.of() : leaves.stream().filter(pos -> pos != null).map(BlockPos::immutable).toList();
    }

    @Override
    public ResourceKind kind() {
        return ResourceKind.TREE;
    }

    @Override
    public BlockPos seedPos() {
        return this.base;
    }

    @Override
    public String label() {
        return "tree@" + this.base.getX() + "," + this.base.getY() + "," + this.base.getZ();
    }

    public int height() {
        return this.logs.size();
    }
}

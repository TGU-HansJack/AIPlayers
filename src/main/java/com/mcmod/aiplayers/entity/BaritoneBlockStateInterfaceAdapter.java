package com.mcmod.aiplayers.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public final class BaritoneBlockStateInterfaceAdapter {
    private final AIPlayerEntity entity;
    private final ChunkSnapshotCache.RegionSnapshot snapshot;

    public BaritoneBlockStateInterfaceAdapter(AIPlayerEntity entity) {
        this(entity, null);
    }

    public BaritoneBlockStateInterfaceAdapter(AIPlayerEntity entity, ChunkSnapshotCache.RegionSnapshot snapshot) {
        this.entity = entity;
        this.snapshot = snapshot;
    }

    public BlockState getBlockState(BlockPos pos) {
        if (this.snapshot != null && !this.snapshot.chunks().isEmpty()) {
            return this.snapshot.getBlockState(pos);
        }
        return this.entity.level().getBlockState(pos);
    }

    public FluidState getFluidState(BlockPos pos) {
        if (this.snapshot != null && !this.snapshot.chunks().isEmpty()) {
            return this.snapshot.getFluidState(pos);
        }
        return this.entity.level().getFluidState(pos);
    }

    public boolean isLoaded(BlockPos pos) {
        if (this.snapshot != null && !this.snapshot.chunks().isEmpty()) {
            return this.snapshot.isLoaded(pos);
        }
        return pos != null && this.entity.level().isLoaded(pos);
    }
}

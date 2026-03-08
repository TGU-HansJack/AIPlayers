package com.mcmod.aiplayers.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public final class BaritoneBlockStateInterfaceAdapter {
    private final AIPlayerEntity entity;

    public BaritoneBlockStateInterfaceAdapter(AIPlayerEntity entity) {
        this.entity = entity;
    }

    public BlockState getBlockState(BlockPos pos) {
        return this.entity.level().getBlockState(pos);
    }

    public FluidState getFluidState(BlockPos pos) {
        return this.entity.level().getFluidState(pos);
    }

    public boolean isLoaded(BlockPos pos) {
        return pos != null && this.entity.level().isLoaded(pos);
    }
}

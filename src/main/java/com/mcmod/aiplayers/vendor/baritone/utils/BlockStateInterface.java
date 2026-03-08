package com.mcmod.aiplayers.vendor.baritone.utils;

import com.mcmod.aiplayers.entity.BaritoneBlockStateInterfaceAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

// Upstream reference: baritone-1.21.11/src/main/java/baritone/utils/BlockStateInterface.java
public class BlockStateInterface {
    private final BaritoneBlockStateInterfaceAdapter adapter;

    public BlockStateInterface(BaritoneBlockStateInterfaceAdapter adapter) {
        this.adapter = adapter;
    }

    public BlockState get(BlockPos pos) {
        return this.adapter.getBlockState(pos);
    }

    public FluidState getFluid(BlockPos pos) {
        return this.adapter.getFluidState(pos);
    }

    public boolean isLoaded(BlockPos pos) {
        return this.adapter.isLoaded(pos);
    }
}

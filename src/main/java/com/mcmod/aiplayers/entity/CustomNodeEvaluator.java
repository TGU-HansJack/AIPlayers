package com.mcmod.aiplayers.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class CustomNodeEvaluator {
    private final AIPlayerEntity entity;

    CustomNodeEvaluator(AIPlayerEntity entity) {
        this.entity = entity;
    }

    boolean canStandAt(BlockPos pos, boolean allowWater) {
        return this.entity.runtimeCanStandAt(pos, allowWater);
    }

    boolean canBreakForPath(BlockPos pos) {
        return this.entity.runtimeCanBreakPathBlock(pos);
    }

    boolean canPlaceSupportAt(BlockPos pos) {
        return this.entity.runtimeCanPlacePathSupport(pos);
    }

    boolean isWaterNode(BlockPos pos) {
        return this.entity.level().getFluidState(pos).is(FluidTags.WATER)
                || this.entity.level().getFluidState(pos.above()).is(FluidTags.WATER);
    }

    boolean isLavaNode(BlockPos pos) {
        return this.entity.level().getFluidState(pos).is(FluidTags.LAVA)
                || this.entity.level().getFluidState(pos.above()).is(FluidTags.LAVA);
    }

    double movementPenalty(BlockPos pos, boolean jumped, PathNodeAction action) {
        double penalty = 1.0D;
        if (this.isWaterNode(pos)) {
            penalty += 1.3D;
        }
        if (jumped) {
            penalty += 0.7D;
        }
        if (action == PathNodeAction.BREAK_BLOCK) {
            penalty += 1.1D;
        } else if (action == PathNodeAction.PLACE_SUPPORT) {
            penalty += 1.3D;
        }
        BlockState floor = this.entity.level().getBlockState(pos.below());
        if (floor.is(BlockTags.LEAVES)) {
            penalty += 0.8D;
        }
        if (floor.is(Blocks.SOUL_SAND) || floor.is(Blocks.HONEY_BLOCK)) {
            penalty += 0.9D;
        }
        return penalty;
    }
}

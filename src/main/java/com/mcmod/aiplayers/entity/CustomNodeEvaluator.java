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

    boolean canOccupyAfterBreak(BlockPos standPos, BlockPos breakPos) {
        if (standPos == null || breakPos == null) {
            return false;
        }
        BlockPos feetPos = standPos;
        BlockPos headPos = standPos.above();
        boolean breakFeet = breakPos.equals(feetPos);
        boolean breakHead = breakPos.equals(headPos);
        if (!breakFeet && !breakHead) {
            return false;
        }

        BlockState feetState = this.entity.level().getBlockState(feetPos);
        BlockState headState = this.entity.level().getBlockState(headPos);
        boolean feetClear = breakFeet || feetState.getCollisionShape(this.entity.level(), feetPos).isEmpty();
        boolean headClear = breakHead || headState.getCollisionShape(this.entity.level(), headPos).isEmpty();
        if (!feetClear || !headClear) {
            return false;
        }

        if (this.entity.level().getFluidState(feetPos).is(FluidTags.LAVA) || this.entity.level().getFluidState(headPos).is(FluidTags.LAVA)) {
            return false;
        }

        boolean waterFoot = this.entity.level().getFluidState(feetPos).is(FluidTags.WATER);
        boolean waterHead = this.entity.level().getFluidState(headPos).is(FluidTags.WATER);
        if (waterFoot || waterHead) {
            return true;
        }

        BlockPos floorPos = standPos.below();
        BlockState floorState = this.entity.level().getBlockState(floorPos);
        boolean stableFloor = !floorState.getCollisionShape(this.entity.level(), floorPos).isEmpty()
                && !this.entity.level().getFluidState(floorPos).is(FluidTags.LAVA);
        return stableFloor || this.canPlaceSupportAt(floorPos);
    }

    boolean isWaterNode(BlockPos pos) {
        return this.entity.level().getFluidState(pos).is(FluidTags.WATER)
                || this.entity.level().getFluidState(pos.above()).is(FluidTags.WATER);
    }

    boolean isLavaNode(BlockPos pos) {
        return this.entity.level().getFluidState(pos).is(FluidTags.LAVA)
                || this.entity.level().getFluidState(pos.above()).is(FluidTags.LAVA);
    }

    boolean isSoftBreakable(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        BlockState state = this.entity.level().getBlockState(pos);
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND);
    }

    double breakPenalty(BlockPos pos) {
        if (pos == null) {
            return 1.0D;
        }
        BlockState state = this.entity.level().getBlockState(pos);
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE_BY_TREES)) {
            return 0.3D;
        }
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)) {
            return 0.2D;
        }
        if (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) {
            return 0.55D;
        }
        return 1.2D;
    }

    double movementPenalty(BlockPos pos, boolean jumped, PathNodeAction action) {
        double penalty = 1.0D;
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

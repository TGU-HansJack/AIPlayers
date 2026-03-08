package com.mcmod.aiplayers.vendor.baritone.pathing.movement;

import com.mcmod.aiplayers.entity.BaritoneEntityContextAdapter;
import com.mcmod.aiplayers.vendor.baritone.utils.BlockStateInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// Upstream reference: baritone-1.21.11/src/main/java/baritone/pathing/movement/CalculationContext.java
public class CalculationContext {
    public static final int MAX_STEP_HEIGHT = 3;
    public static final int MAX_DROP_HEIGHT = 4;

    public final BaritoneEntityContextAdapter context;
    public final BlockStateInterface bsi;

    public CalculationContext(BaritoneEntityContextAdapter context) {
        this.context = context;
        this.bsi = new BlockStateInterface(context.blockStateInterface());
    }

    public ServerLevel level() {
        return this.context.level();
    }

    public boolean canStandAt(BlockPos pos, boolean allowWater) {
        return this.context.canStandAt(pos, allowWater);
    }

    public boolean canBreakForPath(BlockPos pos) {
        return this.context.playerController().canBreakBlock(pos);
    }

    public boolean canPlaceSupportAt(BlockPos pos) {
        return this.context.playerController().canPlaceSupport(pos);
    }

    public boolean canOccupyAfterBreak(BlockPos standPos, BlockPos breakPos) {
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
        BlockState feetState = this.bsi.get(feetPos);
        BlockState headState = this.bsi.get(headPos);
        boolean feetClear = breakFeet || feetState.getCollisionShape(this.level(), feetPos).isEmpty();
        boolean headClear = breakHead || headState.getCollisionShape(this.level(), headPos).isEmpty();
        if (!feetClear || !headClear) {
            return false;
        }
        if (this.bsi.getFluid(feetPos).is(FluidTags.LAVA) || this.bsi.getFluid(headPos).is(FluidTags.LAVA)) {
            return false;
        }
        boolean waterFoot = this.bsi.getFluid(feetPos).is(FluidTags.WATER);
        boolean waterHead = this.bsi.getFluid(headPos).is(FluidTags.WATER);
        if (waterFoot || waterHead) {
            return true;
        }
        BlockPos floorPos = standPos.below();
        BlockState floorState = this.bsi.get(floorPos);
        boolean stableFloor = !floorState.getCollisionShape(this.level(), floorPos).isEmpty() && !this.bsi.getFluid(floorPos).is(FluidTags.LAVA);
        return stableFloor || this.canPlaceSupportAt(floorPos);
    }

    public boolean canOccupyAfterPlace(BlockPos standPos, BlockPos supportPos) {
        if (standPos == null || supportPos == null || !supportPos.equals(standPos.below())) {
            return false;
        }
        BlockPos feetPos = standPos;
        BlockPos headPos = standPos.above();
        BlockState feetState = this.bsi.get(feetPos);
        BlockState headState = this.bsi.get(headPos);
        if (!feetState.getCollisionShape(this.level(), feetPos).isEmpty() || !headState.getCollisionShape(this.level(), headPos).isEmpty()) {
            return false;
        }
        if (this.bsi.getFluid(feetPos).is(FluidTags.LAVA) || this.bsi.getFluid(headPos).is(FluidTags.LAVA)) {
            return false;
        }
        BlockState floorState = this.bsi.get(supportPos);
        if (!floorState.getCollisionShape(this.level(), supportPos).isEmpty()) {
            return true;
        }
        return this.canPlaceSupportAt(supportPos);
    }

    public boolean isWaterNode(BlockPos pos) {
        return this.bsi.getFluid(pos).is(FluidTags.WATER) || this.bsi.getFluid(pos.above()).is(FluidTags.WATER);
    }

    public boolean isLavaNode(BlockPos pos) {
        return this.bsi.getFluid(pos).is(FluidTags.LAVA) || this.bsi.getFluid(pos.above()).is(FluidTags.LAVA);
    }

    public boolean isSoftBreakable(BlockPos pos) {
        BlockState state = this.bsi.get(pos);
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK);
    }

    public double breakPenalty(BlockPos pos) {
        BlockState state = this.bsi.get(pos);
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE_BY_TREES)) {
            return 0.3D;
        }
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)) {
            return 0.2D;
        }
        if (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) {
            return 0.42D;
        }
        if (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.NETHERRACK)) {
            return 0.85D;
        }
        return 1.2D;
    }

    public double movementPenalty(BlockPos pos, boolean jumped, String actionKind) {
        double penalty = 1.0D;
        if (jumped) {
            penalty += 0.7D;
        }
        if ("break".equals(actionKind)) {
            penalty += 1.1D;
        } else if ("place".equals(actionKind)) {
            penalty += 1.3D;
        }
        BlockState floor = this.bsi.get(pos.below());
        if (floor.is(BlockTags.LEAVES)) {
            penalty += 0.8D;
        }
        if (floor.is(Blocks.SOUL_SAND) || floor.is(Blocks.HONEY_BLOCK)) {
            penalty += 0.9D;
        }
        if (this.isWaterNode(pos)) {
            penalty += 0.35D;
        }
        if (this.isLavaAdjacent(pos)) {
            penalty += 2.2D;
        }
        if (this.isDarkNode(pos)) {
            penalty += 0.18D;
        }
        return penalty;
    }

    public BlockPos findWaterEscapeTarget(BlockPos from) {
        if (from == null || !this.isWaterNode(from)) {
            return null;
        }
        return this.context.findNearbyDryStandPosition(from, 6, 4);
    }

    private boolean isLavaAdjacent(BlockPos pos) {
        if (this.isLavaNode(pos)) {
            return true;
        }
        for (BlockPos neighbor : new BlockPos[]{pos.north(), pos.south(), pos.east(), pos.west(), pos.below(), pos.above()}) {
            if (this.bsi.getFluid(neighbor).is(FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDarkNode(BlockPos pos) {
        return !this.level().canSeeSky(pos) && this.level().getMaxLocalRawBrightness(pos) <= 6;
    }
}

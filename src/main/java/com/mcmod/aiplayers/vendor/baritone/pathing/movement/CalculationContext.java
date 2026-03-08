package com.mcmod.aiplayers.vendor.baritone.pathing.movement;

import com.mcmod.aiplayers.entity.BaritoneBlockStateInterfaceAdapter;
import com.mcmod.aiplayers.entity.BaritoneEntityContextAdapter;
import com.mcmod.aiplayers.entity.ChunkSnapshotCache;
import com.mcmod.aiplayers.vendor.baritone.utils.BlockStateInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// Upstream reference: baritone-1.21.11/src/main/java/baritone/pathing/movement/CalculationContext.java
public class CalculationContext {
    public static final int MAX_STEP_HEIGHT = 3;
    public static final int MAX_DROP_HEIGHT = 4;
    private static final int DEFAULT_NODE_LIMIT = 4096;
    private static final long DEFAULT_PRIMARY_TIMEOUT_MS = 20L;
    private static final long DEFAULT_FAILURE_TIMEOUT_MS = 45L;

    public final BaritoneEntityContextAdapter context;
    public final BlockStateInterface bsi;
    public final ChunkSnapshotCache.RegionSnapshot snapshot;
    private final boolean allowBreak;
    private final boolean allowPlaceSupport;
    private final int searchNodeLimit;
    private final long primaryTimeoutMs;
    private final long failureTimeoutMs;

    public CalculationContext(BaritoneEntityContextAdapter context) {
        this(context, ChunkSnapshotCache.capture(context.level(), context.playerFeet(), context.playerFeet()), DEFAULT_NODE_LIMIT, DEFAULT_PRIMARY_TIMEOUT_MS, DEFAULT_FAILURE_TIMEOUT_MS);
    }

    public CalculationContext(BaritoneEntityContextAdapter context, ChunkSnapshotCache.RegionSnapshot snapshot, int searchNodeLimit) {
        this(context, snapshot, searchNodeLimit, DEFAULT_PRIMARY_TIMEOUT_MS, DEFAULT_FAILURE_TIMEOUT_MS);
    }

    public CalculationContext(BaritoneEntityContextAdapter context, ChunkSnapshotCache.RegionSnapshot snapshot, int searchNodeLimit, long primaryTimeoutMs, long failureTimeoutMs) {
        this.context = context;
        this.snapshot = snapshot == null ? ChunkSnapshotCache.RegionSnapshot.empty() : snapshot;
        BaritoneBlockStateInterfaceAdapter adapter = this.snapshot.chunks().isEmpty()
                ? context.blockStateInterface()
                : context.blockStateInterface(this.snapshot);
        this.bsi = new BlockStateInterface(adapter);
        this.allowBreak = context.mobGriefingAllowed();
        this.allowPlaceSupport = this.allowBreak && context.hasPathSupportBlocks();
        this.searchNodeLimit = Math.max(512, searchNodeLimit);
        this.primaryTimeoutMs = Math.max(5L, primaryTimeoutMs);
        this.failureTimeoutMs = Math.max(this.primaryTimeoutMs, failureTimeoutMs);
    }

    public boolean canStandAt(BlockPos pos, boolean allowWater) {
        if (pos == null || !this.bsi.isLoaded(pos) || !this.bsi.isLoaded(pos.above()) || !this.bsi.isLoaded(pos.below())) {
            return false;
        }
        BlockState feet = this.bsi.get(pos);
        BlockState head = this.bsi.get(pos.above());
        BlockState floor = this.bsi.get(pos.below());
        boolean feetInWater = this.bsi.getFluid(pos).is(FluidTags.WATER);
        boolean headInWater = this.bsi.getFluid(pos.above()).is(FluidTags.WATER);
        boolean feetClear = this.isPassable(feet, pos) && !this.bsi.getFluid(pos).is(FluidTags.LAVA) && (allowWater || !feetInWater);
        boolean headClear = this.isPassable(head, pos.above()) && !this.bsi.getFluid(pos.above()).is(FluidTags.LAVA) && (allowWater || !headInWater);
        boolean floorStable = !this.isPassable(floor, pos.below()) && !floor.is(BlockTags.LEAVES) && !this.bsi.getFluid(pos.below()).is(FluidTags.LAVA);
        if (allowWater && (feetInWater || headInWater)) {
            return feetClear && headClear;
        }
        return feetClear && headClear && floorStable;
    }

    public boolean canBreakForPath(BlockPos pos) {
        if (!this.allowBreak || pos == null || !this.bsi.isLoaded(pos)) {
            return false;
        }
        BlockState state = this.bsi.get(pos);
        if (state.isAir() || this.bsi.getFluid(pos).is(FluidTags.LAVA)) {
            return false;
        }
        if (state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER) || state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.COMMAND_BLOCK) || state.is(Blocks.REPEATING_COMMAND_BLOCK) || state.is(Blocks.CHAIN_COMMAND_BLOCK)) {
            return false;
        }
        return state.getDestroySpeed(EmptyBlockGetter.INSTANCE, pos) >= 0.0F;
    }

    public boolean canPlaceSupportAt(BlockPos pos) {
        if (!this.allowPlaceSupport || pos == null || !this.bsi.isLoaded(pos) || !this.bsi.isLoaded(pos.below())) {
            return false;
        }
        if (!this.bsi.get(pos).isAir()) {
            return false;
        }
        BlockState floor = this.bsi.get(pos.below());
        return !this.isPassable(floor, pos.below()) && !this.bsi.getFluid(pos.below()).is(FluidTags.LAVA);
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
        boolean feetClear = breakFeet || this.isPassable(feetState, feetPos);
        boolean headClear = breakHead || this.isPassable(headState, headPos);
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
        boolean stableFloor = !this.isPassable(floorState, floorPos) && !this.bsi.getFluid(floorPos).is(FluidTags.LAVA);
        return stableFloor || this.canPlaceSupportAt(floorPos);
    }

    public boolean canOccupyAfterPlace(BlockPos standPos, BlockPos supportPos) {
        if (standPos == null || supportPos == null || !supportPos.equals(standPos.below())) {
            return false;
        }
        BlockPos feetPos = standPos;
        BlockPos headPos = standPos.above();
        if (!this.isPassable(this.bsi.get(feetPos), feetPos) || !this.isPassable(this.bsi.get(headPos), headPos)) {
            return false;
        }
        if (this.bsi.getFluid(feetPos).is(FluidTags.LAVA) || this.bsi.getFluid(headPos).is(FluidTags.LAVA)) {
            return false;
        }
        BlockState floorState = this.bsi.get(supportPos);
        return !this.isPassable(floorState, supportPos) || this.canPlaceSupportAt(supportPos);
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
        return penalty;
    }

    public BlockPos findWaterEscapeTarget(BlockPos from) {
        if (from == null || !this.isWaterNode(from)) {
            return null;
        }
        return this.findNearbyStandPosition(from, 6, 4, false, false);
    }

    public BlockPos resolveMovementTarget(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        if (this.canStandAt(pos, true)) {
            return pos.immutable();
        }
        return this.findNearbyStandPosition(pos, 6, 5, true, true);
    }

    public int searchNodeLimit() {
        return this.searchNodeLimit;
    }

    public long primaryTimeoutMs() {
        return this.primaryTimeoutMs;
    }

    public long failureTimeoutMs() {
        return this.failureTimeoutMs;
    }

    public long snapshotSignature() {
        return this.snapshot.signature();
    }

    private BlockPos findNearbyStandPosition(BlockPos center, int horizontalRadius, int verticalRadius, boolean allowWater, boolean preferNearY) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
            for (int y = -verticalRadius; y <= verticalRadius; y++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    BlockPos candidate = center.offset(x, y, z);
                    if (!this.canStandAt(candidate, allowWater)) {
                        continue;
                    }
                    double score = x * x + z * z + y * y * (preferNearY ? 1.8D : 0.9D);
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate.immutable();
                    }
                }
            }
        }
        return best;
    }

    private boolean isPassable(BlockState state, BlockPos pos) {
        return state == null || state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos).isEmpty();
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
}

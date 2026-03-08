package com.mcmod.aiplayers.vendor.baritone.pathing.movement;

import com.mcmod.aiplayers.vendor.baritone.pathing.movement.movements.MovementAscend;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.movements.MovementBreakAndTraverse;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.movements.MovementDescend;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.movements.MovementDiagonal;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.movements.MovementDownward;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.movements.MovementFall;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.movements.MovementPlaceAndTraverse;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.movements.MovementTraverse;
import com.mcmod.aiplayers.vendor.baritone.pathing.movement.movements.MovementWaterEscape;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

// Upstream reference: baritone-1.21.11/src/main/java/baritone/pathing/movement/Moves.java
public enum Moves {
    TRAVERSE,
    DIAGONAL,
    ASCEND,
    DESCEND,
    DOWNWARD,
    FALL,
    BREAK,
    PLACE,
    WATER_ESCAPE;

    public static List<Movement> generateAll(CalculationContext context, BlockPos from) {
        List<Movement> steps = new ArrayList<>();
        if (from == null) {
            return steps;
        }
        boolean fromWater = context.isWaterNode(from);
        int[][] offsets = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] offset : offsets) {
            int dx = offset[0];
            int dz = offset[1];
            boolean diagonal = Math.abs(dx) + Math.abs(dz) == 2;
            double diagonalPenalty = diagonal ? 0.18D : 0.0D;
            BlockPos base = from.offset(dx, 0, dz);
            if (context.canStandAt(base, true) && !context.isLavaNode(base)) {
                steps.add(diagonal
                        ? new MovementDiagonal(from, base, context.movementPenalty(base, false, "move") + diagonalPenalty)
                        : new MovementTraverse(from, base, context.movementPenalty(base, false, "move") + diagonalPenalty));
            }
            for (int up = 1; up <= CalculationContext.MAX_STEP_HEIGHT; up++) {
                BlockPos upPos = base.above(up);
                if (context.canStandAt(upPos, true) && !context.isLavaNode(upPos)) {
                    steps.add(new MovementAscend(from, upPos, context.movementPenalty(upPos, true, "move") + diagonalPenalty + 0.08D * up));
                    break;
                }
            }
            for (int down = 1; down <= CalculationContext.MAX_DROP_HEIGHT; down++) {
                BlockPos downPos = base.below(down);
                if (context.canStandAt(downPos, true) && !context.isLavaNode(downPos)) {
                    if (down == 1) {
                        steps.add(new MovementDescend(from, downPos, context.movementPenalty(downPos, false, "move") + diagonalPenalty + 0.2D * down));
                    } else {
                        steps.add(new MovementFall(from, downPos, context.movementPenalty(downPos, false, "move") + diagonalPenalty + 0.2D * down));
                    }
                    break;
                }
            }
            BlockPos feetBlock = base;
            BlockPos headBlock = base.above();
            for (BlockPos clearTarget : List.of(feetBlock, headBlock)) {
                if (!context.canBreakForPath(clearTarget)) {
                    continue;
                }
                if (!context.canStandAt(base, true) && !context.isWaterNode(base) && !context.canOccupyAfterBreak(base, clearTarget)) {
                    continue;
                }
                double penalty = context.movementPenalty(base, false, "break") + context.breakPenalty(clearTarget) + diagonalPenalty;
                if (context.isSoftBreakable(clearTarget)) {
                    penalty = Math.max(0.82D, penalty - 0.3D);
                }
                steps.add(new MovementBreakAndTraverse(from, base, clearTarget, penalty));
            }
            BlockPos supportPos = base.below();
            if (context.canPlaceSupportAt(supportPos) && context.canOccupyAfterPlace(base, supportPos)) {
                steps.add(new MovementPlaceAndTraverse(from, base, supportPos, context.movementPenalty(base, true, "place") + diagonalPenalty, false));
            }
            if (fromWater && context.canStandAt(base, true) && !context.isWaterNode(base) && !context.isLavaNode(base)) {
                steps.add(new MovementWaterEscape(from, base, context.movementPenalty(base, true, "move") + 0.06D + diagonalPenalty));
            }
        }
        BlockPos downward = from.below();
        if (context.canStandAt(downward, true) && !context.isLavaNode(downward)) {
            steps.add(new MovementDownward(from, downward, context.movementPenalty(downward, false, "move") + 0.15D));
        }
        return steps;
    }
}

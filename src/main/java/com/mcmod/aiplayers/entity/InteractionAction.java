package com.mcmod.aiplayers.entity;

import net.minecraft.core.BlockPos;

public record InteractionAction(
        InteractionType type,
        BlockPos targetPos,
        double speed,
        double completionDistanceSqr,
        String label,
        boolean woodTask) {
    public InteractionAction {
        type = type == null ? InteractionType.LOOK_AT : type;
        speed = speed <= 0.0D ? 1.0D : speed;
        completionDistanceSqr = completionDistanceSqr <= 0.0D ? 4.0D : completionDistanceSqr;
        label = label == null || label.isBlank() ? type.name() : label;
    }

    static InteractionAction moveNear(BlockPos pos, double speed, double completionDistanceSqr, String label) {
        return new InteractionAction(InteractionType.MOVE_NEAR, pos, speed, completionDistanceSqr, label, false);
    }

    static InteractionAction lookAt(BlockPos pos, String label) {
        return new InteractionAction(InteractionType.LOOK_AT, pos, 1.0D, 1.0D, label, false);
    }

    static InteractionAction adjustView(BlockPos pos, String label) {
        return new InteractionAction(InteractionType.ADJUST_VIEW, pos, 1.0D, 1.0D, label, false);
    }

    static InteractionAction equipTool(boolean woodTask, String label) {
        return new InteractionAction(InteractionType.EQUIP_TOOL, null, 1.0D, 1.0D, label, woodTask);
    }

    static InteractionAction raiseShield(String label) {
        return new InteractionAction(InteractionType.RAISE_SHIELD, null, 1.0D, 1.0D, label, false);
    }

    static InteractionAction lowerShield(String label) {
        return new InteractionAction(InteractionType.LOWER_SHIELD, null, 1.0D, 1.0D, label, false);
    }

    static InteractionAction recoverStuck(String label) {
        return new InteractionAction(InteractionType.RECOVER_STUCK, null, 1.0D, 1.0D, label, false);
    }

    static InteractionAction clearPath(BlockPos pos, boolean woodTask, String label) {
        return new InteractionAction(InteractionType.CLEAR_PATH, pos, 1.05D, 9.0D, label, woodTask);
    }

    static InteractionAction breakTarget(BlockPos pos, boolean woodTask, String label) {
        return new InteractionAction(InteractionType.BREAK_TARGET, pos, 1.05D, 9.0D, label, woodTask);
    }

    static InteractionAction placeBlock(BlockPos pos, String label) {
        return new InteractionAction(InteractionType.PLACE_BLOCK, pos, 1.0D, 9.0D, label, false);
    }

    static InteractionAction collectDrops(String label) {
        return new InteractionAction(InteractionType.COLLECT_DROPS, null, 1.0D, 4.0D, label, false);
    }

    static InteractionAction craftPlanks(String label) {
        return new InteractionAction(InteractionType.CRAFT_PLANKS, null, 1.0D, 4.0D, label, false);
    }

    static InteractionAction craftBread(String label) {
        return new InteractionAction(InteractionType.CRAFT_BREAD, null, 1.0D, 4.0D, label, false);
    }

    static InteractionAction craftTool(String label) {
        return new InteractionAction(InteractionType.CRAFT_TOOL, null, 1.0D, 4.0D, label, false);
    }
}

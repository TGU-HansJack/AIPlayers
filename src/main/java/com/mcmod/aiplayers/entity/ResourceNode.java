package com.mcmod.aiplayers.entity;

import net.minecraft.core.BlockPos;

public record ResourceNode(ResourceType type, BlockPos pos, double distanceSqr, String label) {
}

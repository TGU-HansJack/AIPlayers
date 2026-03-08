package com.mcmod.aiplayers.entity;

import java.util.List;
import net.minecraft.core.BlockPos;

public interface ResourceTarget {
    ResourceKind kind();

    BlockPos seedPos();

    List<BlockPos> standPositions();

    String label();
}

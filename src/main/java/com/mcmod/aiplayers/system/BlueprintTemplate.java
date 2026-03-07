package com.mcmod.aiplayers.system;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

public record BlueprintTemplate(String id, String displayName, List<Placement> placements) {
    public int requiredUnits() {
        return this.placements.size();
    }

    public record Placement(int x, int y, int z, Block block) {
        public BlockPos resolve(BlockPos anchor) {
            return this == null ? anchor : anchor.offset(this.x, this.y, this.z);
        }
    }
}

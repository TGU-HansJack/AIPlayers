package com.mcmod.aiplayers.system;

import com.mcmod.aiplayers.entity.AIPlayerMode;

public record AIAgentWorldState(
        AIPlayerMode mode,
        boolean ownerAvailable,
        double ownerDistance,
        boolean hostileNearby,
        boolean lowHealth,
        boolean inHazard,
        boolean navigationStuck,
        boolean pendingDelivery,
        boolean hasWoodTarget,
        boolean hasOreTarget,
        boolean night,
        int buildingUnits,
        int usedBackpackSlots,
        int freeBackpackSlots,
        String observation,
        String inventoryPreview) {
}

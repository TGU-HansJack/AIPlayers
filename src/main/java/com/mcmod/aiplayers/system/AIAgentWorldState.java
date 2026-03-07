package com.mcmod.aiplayers.system;

import com.mcmod.aiplayers.entity.AIPlayerMode;

public record AIAgentWorldState(
        AIPlayerMode mode,
        boolean ownerAvailable,
        double ownerDistance,
        boolean ownerUnderThreat,
        boolean hostileNearby,
        boolean lowHealth,
        boolean inHazard,
        boolean onFire,
        boolean navigationStuck,
        boolean pendingDelivery,
        boolean hasWoodTarget,
        boolean hasOreTarget,
        boolean lowFoodSupply,
        boolean foodSourceKnown,
        boolean nearDroppedItems,
        boolean bedKnown,
        boolean workstationKnown,
        boolean lowTools,
        boolean night,
        int buildingUnits,
        int usedBackpackSlots,
        int freeBackpackSlots,
        boolean progressStalled,
        int taskFailureStreak,
        int taskSuccessStreak,
        String observation,
        String inventoryPreview,
        String activeTask,
        String lastTaskFeedback,
        String cognitionSummary) {
}

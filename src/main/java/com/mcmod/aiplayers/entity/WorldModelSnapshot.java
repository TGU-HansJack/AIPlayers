package com.mcmod.aiplayers.entity;

import java.util.List;
import net.minecraft.core.BlockPos;

public record WorldModelSnapshot(
        long gameTime,
        AIPlayerMode uiMode,
        List<SpatialFact> nearbyBlocks,
        List<EntityFact> nearbyEntities,
        List<SpatialFact> resources,
        List<SpatialFact> dangers,
        List<SpatialFact> structures,
        List<InventoryFact> inventory,
        List<EquipmentFact> equipment,
        NavigationState navigation,
        StuckState stuckState,
        List<SpatialFact> currentInteractables,
        BlockPos ownerPos,
        BlockPos hostilePos,
        BlockPos droppedItemPos,
        boolean ownerAvailable,
        boolean ownerUnderThreat,
        boolean hostileNearby,
        boolean lowHealth,
        boolean lowFood,
        boolean lowTools,
        boolean inHazard,
        boolean onFire,
        boolean night,
        boolean pendingDelivery,
        boolean shelterReady,
        int buildingUnits,
        int freeBackpackSlots,
        String observation,
        String cognition) {
    public WorldModelSnapshot {
        uiMode = uiMode == null ? AIPlayerMode.IDLE : uiMode;
        nearbyBlocks = nearbyBlocks == null ? List.of() : List.copyOf(nearbyBlocks);
        nearbyEntities = nearbyEntities == null ? List.of() : List.copyOf(nearbyEntities);
        resources = resources == null ? List.of() : List.copyOf(resources);
        dangers = dangers == null ? List.of() : List.copyOf(dangers);
        structures = structures == null ? List.of() : List.copyOf(structures);
        inventory = inventory == null ? List.of() : List.copyOf(inventory);
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        navigation = navigation == null ? NavigationState.idle() : navigation;
        stuckState = stuckState == null ? StuckState.clear() : stuckState;
        currentInteractables = currentInteractables == null ? List.of() : List.copyOf(currentInteractables);
        observation = observation == null ? "" : observation;
        cognition = cognition == null ? "" : cognition;
    }

    public static WorldModelSnapshot empty() {
        return new WorldModelSnapshot(0L, AIPlayerMode.IDLE, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), NavigationState.idle(), StuckState.clear(), List.of(), null, null, null, false, false, false, false, false, false, false, false, false, false, false, 0, 0, "", "");
    }

    public SpatialFact nearestResource(String kind) {
        return findSpatial(this.resources, kind);
    }

    public SpatialFact nearestDanger(String kind) {
        return findSpatial(this.dangers, kind);
    }

    public SpatialFact nearestStructure(String kind) {
        return findSpatial(this.structures, kind);
    }

    public EntityFact nearestEntity(String kind) {
        if (kind == null) {
            return null;
        }
        return this.nearbyEntities.stream()
                .filter(entry -> kind.equalsIgnoreCase(entry.kind()) || kind.equalsIgnoreCase(entry.label()))
                .findFirst()
                .orElse(null);
    }

    public String summary() {
        return "mode=" + this.uiMode.commandName()
                + " owner=" + this.ownerAvailable
                + " hostile=" + this.hostileNearby
                + " hazard=" + this.inHazard
                + " foodLow=" + this.lowFood
                + " toolsLow=" + this.lowTools
                + " night=" + this.night
                + " resources=" + this.resources.size()
                + " structures=" + this.structures.size();
    }

    private static SpatialFact findSpatial(List<SpatialFact> values, String kind) {
        if (kind == null || values == null) {
            return null;
        }
        return values.stream()
                .filter(entry -> kind.equalsIgnoreCase(entry.kind()) || kind.equalsIgnoreCase(entry.label()))
                .findFirst()
                .orElse(null);
    }

    public record SpatialFact(String kind, String label, BlockPos pos, double distanceSqr) {
        public SpatialFact {
            kind = kind == null ? "unknown" : kind.trim().toLowerCase();
            label = label == null || label.isBlank() ? kind : label;
            pos = pos == null ? null : pos.immutable();
            distanceSqr = Math.max(0.0D, distanceSqr);
        }
    }

    public record EntityFact(String kind, String label, BlockPos pos, double distanceSqr, boolean hostile, boolean alive) {
        public EntityFact {
            kind = kind == null ? "unknown" : kind.trim().toLowerCase();
            label = label == null || label.isBlank() ? kind : label;
            pos = pos == null ? null : pos.immutable();
            distanceSqr = Math.max(0.0D, distanceSqr);
        }
    }

    public record InventoryFact(String itemId, String label, int count) {
        public InventoryFact {
            itemId = itemId == null ? "minecraft:air" : itemId;
            label = label == null || label.isBlank() ? itemId : label;
            count = Math.max(0, count);
        }
    }

    public record EquipmentFact(String slot, String itemId, String label) {
        public EquipmentFact {
            slot = slot == null ? "unknown" : slot;
            itemId = itemId == null ? "minecraft:air" : itemId;
            label = label == null || label.isBlank() ? itemId : label;
        }
    }

    public record NavigationState(BlockPos targetPos, boolean active, boolean recovering, String status) {
        public NavigationState {
            targetPos = targetPos == null ? null : targetPos.immutable();
            status = status == null ? "idle" : status;
        }

        public static NavigationState idle() {
            return new NavigationState(null, false, false, "idle");
        }
    }

    public record StuckState(boolean stuck, int stuckTicks, boolean recovering) {
        public StuckState {
            stuckTicks = Math.max(0, stuckTicks);
        }

        public static StuckState clear() {
            return new StuckState(false, 0, false);
        }
    }
}

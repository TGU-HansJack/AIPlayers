package com.mcmod.aiplayers.menu;

import com.mcmod.aiplayers.entity.AIPlayerEntity;
import com.mcmod.aiplayers.registry.ModMenus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

public class AIPlayerBackpackMenu extends AbstractCraftingMenu {
    private static final int AI_SLOT_COUNT = 36;
    private static final int RESULT_SLOT = 0;
    private static final int CRAFT_START = 1;
    private static final int CRAFT_END = 5;
    private static final int ARMOR_START = 5;
    private static final int ARMOR_END = 9;
    private static final int INVENTORY_START = 9;
    private static final int INVENTORY_END = 36;
    private static final int HOTBAR_START = 36;
    private static final int HOTBAR_END = 45;
    private static final int OFFHAND_SLOT = 45;
    private static final Map<EquipmentSlot, Identifier> EMPTY_ARMOR_ICONS = Map.of(
            EquipmentSlot.HEAD, InventoryMenu.EMPTY_ARMOR_SLOT_HELMET,
            EquipmentSlot.CHEST, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            EquipmentSlot.LEGS, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            EquipmentSlot.FEET, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS);

    private final Player viewer;
    private final AIPlayerEntity entity;
    private final Container backpack;
    private final Container armor;
    private final Container offhand;
    private boolean placingRecipe;

    public AIPlayerBackpackMenu(int containerId, Inventory playerInventory) {
        this(
                containerId,
                playerInventory,
                null,
                new SimpleContainer(AI_SLOT_COUNT),
                new SimpleContainer(4),
                new SimpleContainer(1));
    }

    public static AIPlayerBackpackMenu server(int containerId, Inventory playerInventory, AIPlayerEntity entity) {
        return new AIPlayerBackpackMenu(
                containerId,
                playerInventory,
                entity,
                new AIPlayerBackpackContainer(entity),
                new AIPlayerEquipmentContainer(entity, new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}),
                new AIPlayerEquipmentContainer(entity, new EquipmentSlot[]{EquipmentSlot.OFFHAND}));
    }

    private AIPlayerBackpackMenu(
            int containerId,
            Inventory playerInventory,
            AIPlayerEntity entity,
            Container backpack,
            Container armor,
            Container offhand) {
        super(ModMenus.AI_PLAYER_BACKPACK.get(), containerId, 2, 2);
        checkContainerSize(backpack, AI_SLOT_COUNT);
        checkContainerSize(armor, 4);
        checkContainerSize(offhand, 1);
        this.viewer = playerInventory.player;
        this.entity = entity;
        this.backpack = backpack;
        this.armor = armor;
        this.offhand = offhand;

        this.backpack.startOpen(this.viewer);
        this.armor.startOpen(this.viewer);
        this.offhand.startOpen(this.viewer);

        this.addResultSlot(this.viewer, 154, 28);
        this.addCraftingGridSlots(98, 18);

        this.addSlot(new EquipmentSlotSlot(this.armor, this.entity, EquipmentSlot.HEAD, 0, 8, 8, InventoryMenu.EMPTY_ARMOR_SLOT_HELMET));
        this.addSlot(new EquipmentSlotSlot(this.armor, this.entity, EquipmentSlot.CHEST, 1, 8, 26, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE));
        this.addSlot(new EquipmentSlotSlot(this.armor, this.entity, EquipmentSlot.LEGS, 2, 8, 44, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS));
        this.addSlot(new EquipmentSlotSlot(this.armor, this.entity, EquipmentSlot.FEET, 3, 8, 62, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(this.backpack, col + (row + 1) * 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(this.backpack, col, 8 + col * 18, 142));
        }
        this.addSlot(new EquipmentSlotSlot(this.offhand, this.entity, EquipmentSlot.OFFHAND, 0, 77, 62, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD));
    }

    @Override
    public void slotsChanged(Container container) {
        if (this.placingRecipe) {
            return;
        }
        if (this.viewer.level() instanceof ServerLevel serverLevel) {
            this.updateCraftingResult(serverLevel, null);
        }
    }

    @Override
    public void beginPlacingRecipe() {
        this.placingRecipe = true;
    }

    @Override
    public void finishPlacingRecipe(ServerLevel level, RecipeHolder<CraftingRecipe> recipe) {
        this.placingRecipe = false;
        this.updateCraftingResult(level, recipe);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.backpack.stillValid(player) && this.armor.stillValid(player) && this.offhand.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack empty = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return empty;
        }

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        EquipmentSlot equipmentSlot = this.entity != null ? this.entity.getEquipmentSlotForItem(copy) : player.getEquipmentSlotForItem(copy);

        if (index == RESULT_SLOT) {
            if (!this.moveItemStackTo(stack, INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, copy);
        } else if (index >= CRAFT_START && index < CRAFT_END) {
            if (!this.moveItemStackTo(stack, INVENTORY_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= ARMOR_START && index < ARMOR_END) {
            if (!this.moveItemStackTo(stack, INVENTORY_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (equipmentSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            int armorSlot = 8 - equipmentSlot.getIndex();
            if (!this.slots.get(armorSlot).hasItem()) {
                if (!this.moveItemStackTo(stack, armorSlot, armorSlot + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= INVENTORY_START && index < INVENTORY_END) {
                if (!this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= HOTBAR_START && index < HOTBAR_END) {
                if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, INVENTORY_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (equipmentSlot == EquipmentSlot.OFFHAND && !this.slots.get(OFFHAND_SLOT).hasItem()) {
            if (!this.moveItemStackTo(stack, OFFHAND_SLOT, OFFHAND_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= INVENTORY_START && index < INVENTORY_END) {
            if (!this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= HOTBAR_START && index < HOTBAR_END) {
            if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index == OFFHAND_SLOT) {
            if (!this.moveItemStackTo(stack, INVENTORY_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, INVENTORY_START, HOTBAR_END, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY, copy);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);
        if (index == RESULT_SLOT) {
            player.drop(stack, false);
        }
        return copy;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public Slot getResultSlot() {
        return this.slots.get(RESULT_SLOT);
    }

    @Override
    public List<Slot> getInputGridSlots() {
        return this.slots.subList(CRAFT_START, CRAFT_END);
    }

    @Override
    public RecipeBookType getRecipeBookType() {
        return RecipeBookType.CRAFTING;
    }

    @Override
    protected Player owner() {
        return this.viewer;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.resultSlots.clearContent();
        this.backpack.stopOpen(player);
        this.armor.stopOpen(player);
        this.offhand.stopOpen(player);
        if (player.level().isClientSide()) {
            return;
        }
        this.returnCraftingGridItems(player);
    }

    private void updateCraftingResult(ServerLevel level, RecipeHolder<CraftingRecipe> knownRecipe) {
        if (!(this.viewer instanceof ServerPlayer serverPlayer)) {
            return;
        }
        CraftingInput craftingInput = this.craftSlots.asCraftInput();
        ResultContainer result = this.resultSlots;
        ItemStack crafted = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> optional = level.getServer()
                .getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, craftingInput, level, knownRecipe);
        if (optional.isPresent()) {
            RecipeHolder<CraftingRecipe> recipe = optional.get();
            CraftingRecipe craftingRecipe = recipe.value();
            if (result.setRecipeUsed(serverPlayer, recipe)) {
                ItemStack assembled = craftingRecipe.assemble(craftingInput, level.registryAccess());
                if (assembled.isItemEnabled(level.enabledFeatures())) {
                    crafted = assembled;
                }
            }
        }
        result.setItem(0, crafted);
        this.setRemoteSlot(RESULT_SLOT, crafted);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(this.containerId, this.incrementStateId(), RESULT_SLOT, crafted));
    }

    private void returnCraftingGridItems(Player player) {
        for (int index = 0; index < this.craftSlots.getContainerSize(); index++) {
            ItemStack stack = this.craftSlots.removeItemNoUpdate(index);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remaining = this.moveToBackpack(stack);
            if (!remaining.isEmpty()) {
                player.drop(remaining, false);
            }
        }
    }

    private ItemStack moveToBackpack(ItemStack incoming) {
        ItemStack remaining = incoming.copy();
        for (int index = 0; index < this.backpack.getContainerSize(); index++) {
            ItemStack slotStack = this.backpack.getItem(index);
            if (slotStack.isEmpty() || !ItemStack.isSameItemSameComponents(slotStack, remaining)) {
                continue;
            }
            int transferable = Math.min(remaining.getCount(), slotStack.getMaxStackSize() - slotStack.getCount());
            if (transferable <= 0) {
                continue;
            }
            ItemStack merged = slotStack.copy();
            merged.grow(transferable);
            this.backpack.setItem(index, merged);
            remaining.shrink(transferable);
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }

        for (int index = 0; index < this.backpack.getContainerSize(); index++) {
            ItemStack slotStack = this.backpack.getItem(index);
            if (!slotStack.isEmpty()) {
                continue;
            }
            int placed = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            ItemStack split = remaining.copy();
            split.setCount(placed);
            this.backpack.setItem(index, split);
            remaining.shrink(placed);
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }

        return remaining;
    }

    private static final class EquipmentSlotSlot extends Slot {
        private final AIPlayerEntity entity;
        private final EquipmentSlot equipmentSlot;
        private final Identifier emptyIcon;

        private EquipmentSlotSlot(
                Container container,
                AIPlayerEntity entity,
                EquipmentSlot equipmentSlot,
                int slotIndex,
                int x,
                int y,
                Identifier emptyIcon) {
            super(container, slotIndex, x, y);
            this.entity = entity;
            this.equipmentSlot = equipmentSlot;
            this.emptyIcon = emptyIcon;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (this.equipmentSlot == EquipmentSlot.OFFHAND) {
                return true;
            }
            return this.entity == null || stack.canEquip(this.equipmentSlot, this.entity);
        }

        @Override
        public Identifier getNoItemIcon() {
            if (this.equipmentSlot == EquipmentSlot.OFFHAND) {
                return InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
            }
            return EMPTY_ARMOR_ICONS.getOrDefault(this.equipmentSlot, this.emptyIcon);
        }
    }
}

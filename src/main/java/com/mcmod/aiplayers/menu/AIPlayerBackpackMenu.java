package com.mcmod.aiplayers.menu;

import com.mcmod.aiplayers.entity.AIPlayerEntity;
import com.mcmod.aiplayers.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class AIPlayerBackpackMenu extends AbstractContainerMenu {
    private static final int AI_SLOT_COUNT = 36;
    private static final int PLAYER_INV_START_Y = 116;
    private static final int PLAYER_HOTBAR_Y = 174;
    private final Container backpack;

    public AIPlayerBackpackMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(AI_SLOT_COUNT));
    }

    public static AIPlayerBackpackMenu server(int containerId, Inventory playerInventory, AIPlayerEntity entity) {
        return new AIPlayerBackpackMenu(containerId, playerInventory, new AIPlayerBackpackContainer(entity));
    }

    private AIPlayerBackpackMenu(int containerId, Inventory playerInventory, Container backpack) {
        super(ModMenus.AI_PLAYER_BACKPACK.get(), containerId);
        checkContainerSize(backpack, AI_SLOT_COUNT);
        this.backpack = backpack;
        this.backpack.startOpen(playerInventory.player);

        int slotIndex = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(this.backpack, slotIndex++, 8 + col * 18, 18 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(this.backpack, slotIndex++, 8 + col * 18, 76));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_START_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, PLAYER_HOTBAR_Y));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.backpack.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack empty = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return empty;
        }
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        if (index < AI_SLOT_COUNT) {
            if (!this.moveItemStackTo(original, AI_SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(original, 0, AI_SLOT_COUNT, false)) {
            return ItemStack.EMPTY;
        }
        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.backpack.stopOpen(player);
    }
}

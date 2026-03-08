package com.mcmod.aiplayers.menu;

import com.mcmod.aiplayers.entity.AIPlayerEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class AIPlayerEquipmentContainer implements Container {
    private final AIPlayerEntity entity;
    private final EquipmentSlot[] slots;

    AIPlayerEquipmentContainer(AIPlayerEntity entity, EquipmentSlot[] slots) {
        this.entity = entity;
        this.slots = slots == null ? new EquipmentSlot[0] : slots.clone();
    }

    @Override
    public int getContainerSize() {
        return this.slots.length;
    }

    @Override
    public boolean isEmpty() {
        for (EquipmentSlot slot : this.slots) {
            if (!this.entity.getItemBySlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        if (!this.isValidIndex(index)) {
            return ItemStack.EMPTY;
        }
        return this.entity.getItemBySlot(this.slots[index]);
    }

    @Override
    public ItemStack removeItem(int index, int amount) {
        if (!this.isValidIndex(index) || amount <= 0) {
            return ItemStack.EMPTY;
        }
        EquipmentSlot slot = this.slots[index];
        ItemStack current = this.entity.getItemBySlot(slot);
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (current.getCount() <= amount) {
            ItemStack removed = current.copy();
            this.entity.setItemSlot(slot, ItemStack.EMPTY);
            this.entity.onBackpackChanged();
            return removed;
        }
        ItemStack remaining = current.copy();
        ItemStack removed = remaining.split(amount);
        this.entity.setItemSlot(slot, remaining);
        this.entity.onBackpackChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        if (!this.isValidIndex(index)) {
            return ItemStack.EMPTY;
        }
        EquipmentSlot slot = this.slots[index];
        ItemStack current = this.entity.getItemBySlot(slot);
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = current.copy();
        this.entity.setItemSlot(slot, ItemStack.EMPTY);
        this.entity.onBackpackChanged();
        return removed;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (!this.isValidIndex(index)) {
            return;
        }
        ItemStack safeStack = stack == null ? ItemStack.EMPTY : stack;
        this.entity.setItemSlot(this.slots[index], safeStack);
        this.entity.onBackpackChanged();
    }

    @Override
    public void setChanged() {
        this.entity.onBackpackChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.entity.isAlive() && this.entity.canOpenBackpack(player);
    }

    @Override
    public void clearContent() {
        for (EquipmentSlot slot : this.slots) {
            this.entity.setItemSlot(slot, ItemStack.EMPTY);
        }
        this.entity.onBackpackChanged();
    }

    private boolean isValidIndex(int index) {
        return index >= 0 && index < this.slots.length;
    }
}

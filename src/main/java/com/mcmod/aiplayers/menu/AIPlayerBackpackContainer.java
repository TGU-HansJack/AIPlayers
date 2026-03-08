package com.mcmod.aiplayers.menu;

import com.mcmod.aiplayers.entity.AIPlayerEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class AIPlayerBackpackContainer implements Container {
    private final AIPlayerEntity entity;

    public AIPlayerBackpackContainer(AIPlayerEntity entity) {
        this.entity = entity;
    }

    @Override
    public int getContainerSize() {
        return this.entity.getBackpackSize();
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < this.getContainerSize(); slot++) {
            if (!this.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.entity.getBackpackStack(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return this.entity.removeBackpackStack(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return this.entity.removeBackpackStackNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.entity.setBackpackStack(slot, stack);
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
        for (int slot = 0; slot < this.getContainerSize(); slot++) {
            this.setItem(slot, ItemStack.EMPTY);
        }
    }
}

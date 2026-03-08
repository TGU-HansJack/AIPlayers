package com.mcmod.aiplayers.client.screen;

import com.mcmod.aiplayers.menu.AIPlayerBackpackMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;

public class AIPlayerBackpackScreen extends AbstractContainerScreen<AIPlayerBackpackMenu> {
    private static final Identifier INVENTORY_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/container/inventory.png");
    private static final int TEXTURE_SIZE = 256;
    private float xMouse;
    private float yMouse;

    public AIPlayerBackpackScreen(AIPlayerBackpackMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 97;
        this.titleLabelY = 8;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.blit(INVENTORY_TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, TEXTURE_SIZE, TEXTURE_SIZE);
        LivingEntity preview = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player : null;
        if (preview != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    guiGraphics,
                    x + 26,
                    y + 8,
                    x + 75,
                    y + 78,
                    30,
                    0.0625F,
                    (float)(x + 51) - this.xMouse,
                    (float)(y + 75 - 50) - this.yMouse,
                    preview);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.xMouse = mouseX;
        this.yMouse = mouseY;
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}

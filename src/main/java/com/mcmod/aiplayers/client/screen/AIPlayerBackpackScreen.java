package com.mcmod.aiplayers.client.screen;

import com.mcmod.aiplayers.menu.AIPlayerBackpackMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class AIPlayerBackpackScreen extends AbstractContainerScreen<AIPlayerBackpackMenu> {
    private static final Identifier INVENTORY_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/container/inventory.png");
    private static final int PANEL_HEIGHT = 166;
    private static final int TEXTURE_SIZE = 256;

    public AIPlayerBackpackScreen(AIPlayerBackpackMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = PANEL_HEIGHT;
        this.titleLabelY = 6;
        this.inventoryLabelY = 74;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.blit(INVENTORY_TEXTURE, x, y, 0, 0, this.imageWidth, PANEL_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        guiGraphics.drawString(this.font, Component.literal("背包"), 8, this.inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}

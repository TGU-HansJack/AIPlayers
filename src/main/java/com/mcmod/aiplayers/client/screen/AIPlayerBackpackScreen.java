package com.mcmod.aiplayers.client.screen;

import com.mcmod.aiplayers.menu.AIPlayerBackpackMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class AIPlayerBackpackScreen extends AbstractContainerScreen<AIPlayerBackpackMenu> {

    public AIPlayerBackpackScreen(AIPlayerBackpackMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 198;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF1E1E1E);
        guiGraphics.fill(x + 7, y + 17, x + this.imageWidth - 7, y + 94, 0xFF2B2B2B);
        guiGraphics.fill(x + 7, y + 115, x + this.imageWidth - 7, y + 192, 0xFF2B2B2B);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 4, 0x404040, false);
        guiGraphics.drawString(this.font, Component.literal("AI Backpack"), 8, 6, 0x404040, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, 8, 102, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}

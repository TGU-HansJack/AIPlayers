package com.mcmod.aiplayers.client.screen;

import com.mcmod.aiplayers.client.AIPlayersClient;
import com.mcmod.aiplayers.client.voice.AIPlayersVoiceClient;
import com.mcmod.aiplayers.entity.AIPlayerEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class AIPlayersControlScreen extends Screen {
    private static final int HEADER_TOP = 12;
    private static final int CONTENT_TOP = 48;
    private static final int SUMMARY_HEIGHT = 116;
    private static final int FOOTER_HEIGHT = 52;
    private static final int BUTTON_WIDTH = 100;
    private final List<AIPlayerEntity> nearby = new ArrayList<>();
    private AIPlayerEntity selected;
    private EditBox commandBox;
    private EditBox itemRequestBox;
    private EditBox amountBox;
    private int refreshTicks;
    private int scrollOffset;
    private int maxScroll;

    public AIPlayersControlScreen() {
        super(Component.literal("AI Players \u63a7\u5236\u9762\u677f"));
    }

    @Override
    protected void init() {
        this.refreshNearby();
        this.rebuildPanel();
    }

    @Override
    public void tick() {
        super.tick();
        this.refreshTicks++;
        if (this.refreshTicks >= 20) {
            this.refreshTicks = 0;
            this.refreshNearby();
            this.rebuildPanel();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
            this.sendCustomMessage();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        int previous = this.scrollOffset;
        this.scrollOffset = this.clampScroll(this.scrollOffset - (int)Math.round(scrollY * 18.0D));
        if (this.scrollOffset != previous) {
            this.rebuildPanel();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, 12, HEADER_TOP, 0xFFFFFF);
        graphics.drawString(this.font, "\u9644\u8fd1 AI\uff1a" + this.nearby.size(), 12, HEADER_TOP + 16, 0xC0C0C0);
        graphics.drawString(this.font, "\u5f53\u524d\u76ee\u6807\uff1a" + this.getSelectedName(), 220, HEADER_TOP + 16, 0xC0C0C0);
        graphics.drawString(this.font, AIPlayersVoiceClient.getStatusSummary(), 220, HEADER_TOP + 32, 0x88D8FF);

        int summaryY = this.getSummaryTop();
        if (this.selected != null) {
            graphics.drawString(this.font, this.selected.getClientStatusLine(), 12, summaryY, 0xD8FFD8);
            graphics.drawString(this.font, "\u89c2\u5bdf\uff1a" + this.selected.getClientObservationSummary(), 12, summaryY + 16, 0xC8E6FF);
            graphics.drawString(this.font, "\u80cc\u5305\uff1a" + this.selected.getClientInventorySummary(), 12, summaryY + 32, 0xFFE7A8);
        }
        if (this.maxScroll > 0) {
            graphics.drawString(this.font, "\u6eda\u8f6e\u53ef\u4e0a\u4e0b\u6eda\u52a8 (" + this.scrollOffset + "/" + this.maxScroll + ")", this.width - 180, summaryY, 0x909090);
        }
        graphics.drawString(this.font, "\u63d0\u793a\uff1a\u53ef\u76f4\u63a5\u4e0e AI \u5bf9\u8bdd\uff0c\u4e5f\u53ef\u7528\u5feb\u6377\u6309\u94ae\u53d1\u9001\u547d\u4ee4\u3002", 12, this.height - 18, 0x909090);
    }

    private void rebuildPanel() {
        String commandValue = this.commandBox == null ? "" : this.commandBox.getValue();
        String itemValue = this.itemRequestBox == null ? "" : this.itemRequestBox.getValue();
        String amountValue = this.amountBox == null ? "" : this.amountBox.getValue();
        boolean focusCommand = this.commandBox != null && this.commandBox.isFocused();
        boolean focusItem = this.itemRequestBox != null && this.itemRequestBox.isFocused();
        boolean focusAmount = this.amountBox != null && this.amountBox.isFocused();
        int rowHeight = 22;
        int shown = this.nearby.size();
        int contentHeight = Math.max(shown * rowHeight, 288);
        this.maxScroll = Math.max(0, contentHeight - this.getVisibleContentHeight());
        this.scrollOffset = this.clampScroll(this.scrollOffset);

        this.clearWidgets();
        int leftX = 12;
        int topY = CONTENT_TOP - this.scrollOffset;
        int buttonWidth = 180;
        int gridX = 220;
        int gridY = topY + 24;

        for (int index = 0; index < shown; index++) {
            AIPlayerEntity entity = this.nearby.get(index);
            String label = entity.getAIName();
            if (this.selected != null && entity.getUUID().equals(this.selected.getUUID())) {
                label = "> " + label;
            }
            this.addRenderableWidget(Button.builder(Component.literal(label), ignored -> {
                this.selected = entity;
                AIPlayersClient.setSelectedAiName(entity.getAIName());
                this.rebuildPanel();
            }).bounds(leftX, topY + index * rowHeight, buttonWidth, 20).build());
        }

        this.addActionButton(gridX, gridY, "\u8ddf\u968f", () -> this.sendTargetMessage("\u8ddf\u968f"));
        this.addActionButton(gridX + 110, gridY, "\u62a4\u536b", () -> this.sendTargetMessage("\u62a4\u536b"));
        this.addActionButton(gridX, gridY + 24, "\u780d\u6811", () -> this.sendTargetMessage("\u780d\u6811"));
        this.addActionButton(gridX + 110, gridY + 24, "\u6316\u77ff", () -> this.sendTargetMessage("\u6316\u77ff"));
        this.addActionButton(gridX, gridY + 48, "\u63a2\u7d22", () -> this.sendTargetMessage("\u63a2\u7d22"));
        this.addActionButton(gridX + 110, gridY + 48, "\u5efa\u9020", () -> this.sendTargetMessage("\u5efa\u9020"));
        this.addActionButton(gridX, gridY + 72, "\u907f\u96be\u6240", () -> this.sendTargetMessage("\u5efa\u9020\u907f\u96be\u6240"));
        this.addActionButton(gridX + 110, gridY + 72, "\u751f\u5b58", () -> this.sendTargetMessage("\u751f\u5b58"));
        this.addActionButton(gridX, gridY + 96, "\u8df3\u8dc3", () -> this.sendTargetMessage("\u8df3\u4e00\u4e0b"));
        this.addActionButton(gridX + 110, gridY + 96, "\u8e72\u4e0b", () -> this.sendTargetMessage("\u8e72\u4e0b"));
        this.addActionButton(gridX, gridY + 120, "\u62ac\u5934", () -> this.sendTargetMessage("\u62ac\u5934"));
        this.addActionButton(gridX + 110, gridY + 120, "\u4f4e\u5934", () -> this.sendTargetMessage("\u4f4e\u5934"));
        this.addActionButton(gridX, gridY + 144, AIPlayersVoiceClient.isRecording() ? "\u505c\u6b62\u5f55\u97f3" : "\u5f00\u59cb\u5f55\u97f3", () -> AIPlayersVoiceClient.toggleRecording(this.getSelectedName()));
        this.addActionButton(gridX + 110, gridY + 144, "\u5237\u65b0", this::refreshNearbyAndRebuild);
        this.addActionButton(gridX, gridY + 168, "\u72b6\u6001", () -> this.sendTargetMessage("\u72b6\u6001"));
        this.addActionButton(gridX + 110, gridY + 168, "\u80cc\u5305", () -> this.sendTargetMessage("\u770b\u770b\u80cc\u5305"));
        this.addActionButton(gridX, gridY + 192, "\u8ba1\u5212", () -> this.sendTargetMessage("\u8ba1\u5212"));
        this.addActionButton(gridX + 110, gridY + 192, "\u8131\u56f0", () -> this.sendTargetMessage("\u8131\u56f0"));

        this.itemRequestBox = this.addRenderableWidget(new EditBox(this.font, gridX, gridY + 220, 120, 20, Component.literal("\u7269\u54c1")));
        this.itemRequestBox.setHint(Component.literal("\u7269\u54c1\uff0c\u4f8b\u5982\uff1a\u6728\u5934"));
        this.itemRequestBox.setMaxLength(40);
        this.itemRequestBox.setValue(itemValue);
        this.amountBox = this.addRenderableWidget(new EditBox(this.font, gridX + 126, gridY + 220, 38, 20, Component.literal("\u6570\u91cf")));
        this.amountBox.setHint(Component.literal("\u5168\u90e8"));
        this.amountBox.setMaxLength(4);
        this.amountBox.setValue(amountValue);
        this.addRenderableWidget(Button.builder(Component.literal("\u53d6\u7269"), ignored -> this.sendItemRequest())
                .bounds(gridX + 170, gridY + 220, 50, 20)
                .build());
        this.addActionButton(gridX, gridY + 244, "\u7ed9\u6211\u6728\u5934", () -> this.sendTargetMessage("\u628a\u6728\u5934\u7ed9\u6211"));
        this.addActionButton(gridX + 110, gridY + 244, "\u7ed9\u6211\u6728\u677f", () -> this.sendTargetMessage("\u628a\u6728\u677f\u7ed9\u6211"));

        this.commandBox = this.addRenderableWidget(new EditBox(this.font, 12, this.height - 42, this.width - 96, 20, Component.literal("\u8f93\u5165\u547d\u4ee4")));
        this.commandBox.setMaxLength(160);
        this.commandBox.setHint(Component.literal("\u4f8b\u5982\uff1a\u8ddf\u968f\u6211 / \u780d\u6811 / \u5efa\u9020\u907f\u96be\u6240"));
        this.commandBox.setValue(commandValue);
        this.addRenderableWidget(Button.builder(Component.literal("\u53d1\u9001"), ignored -> this.sendCustomMessage())
                .bounds(this.width - 76, this.height - 42, 64, 20)
                .build());

        if (focusCommand) {
            this.setFocused(this.commandBox);
        } else if (focusItem) {
            this.setFocused(this.itemRequestBox);
        } else if (focusAmount) {
            this.setFocused(this.amountBox);
        }
    }

    private void addActionButton(int x, int y, String label, Runnable action) {
        this.addRenderableWidget(Button.builder(Component.literal(label), ignored -> action.run()).bounds(x, y, 100, 20).build());
    }

    private int drawSummaryLine(GuiGraphics graphics, int y, String text, int color, int maxWidth) {
        graphics.drawString(this.font, this.font.plainSubstrByWidth(text, maxWidth), 12, y, color);
        return y + 12;
    }

    private void refreshNearbyAndRebuild() {
        this.refreshNearby();
        this.rebuildPanel();
    }

    private int getSummaryTop() {
        return this.height - FOOTER_HEIGHT - SUMMARY_HEIGHT - 4;
    }

    private int getVisibleContentHeight() {
        return Math.max(80, this.getSummaryTop() - CONTENT_TOP - 8);
    }

    private int clampScroll(int value) {
        return Math.max(0, Math.min(this.maxScroll, value));
    }

    private void refreshNearby() {
        this.nearby.clear();
        Minecraft minecraft = this.getMinecraft();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            this.selected = null;
            return;
        }

        this.nearby.addAll(minecraft.level.getEntitiesOfClass(AIPlayerEntity.class, minecraft.player.getBoundingBox().inflate(64.0D)).stream()
                .sorted(Comparator.comparingDouble(entity -> minecraft.player.distanceToSqr(entity)))
                .toList());

        if (this.selected != null) {
            this.selected = this.nearby.stream()
                    .filter(entity -> entity.getUUID().equals(this.selected.getUUID()))
                    .findFirst()
                    .orElse(null);
        }
        if (this.selected == null && !this.nearby.isEmpty()) {
            this.selected = this.nearby.getFirst();
            AIPlayersClient.setSelectedAiName(this.selected.getAIName());
        }
    }

    private void sendCustomMessage() {
        if (this.commandBox == null || this.commandBox.getValue().isBlank()) {
            return;
        }
        String message = this.commandBox.getValue().trim();
        this.commandBox.setValue("");
        this.sendTargetMessage(message);
    }

    private void sendItemRequest() {
        if (this.itemRequestBox == null || this.itemRequestBox.getValue().isBlank()) {
            return;
        }
        String itemName = this.itemRequestBox.getValue().trim();
        String amount = this.amountBox == null ? "" : this.amountBox.getValue().trim();
        String message = amount.isBlank() ? ("把" + itemName + "给我") : ("给我 " + amount + " 个" + itemName);
        this.sendTargetMessage(message);
        this.itemRequestBox.setValue("");
        if (this.amountBox != null) {
            this.amountBox.setValue("");
        }
    }

    private void sendTargetMessage(String message) {
        Minecraft minecraft = this.getMinecraft();
        if (minecraft == null || minecraft.getConnection() == null) {
            return;
        }
        minecraft.getConnection().sendChat("@" + this.getSelectedName() + " " + message);
    }

    private String getSelectedName() {
        if (this.selected != null && !this.selected.getAIName().isBlank()) {
            return this.selected.getAIName();
        }
        String remembered = AIPlayersClient.getSelectedAiName();
        return remembered == null || remembered.isBlank() ? "ai" : remembered;
    }
}

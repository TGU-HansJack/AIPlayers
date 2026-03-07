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
    private final List<AIPlayerEntity> nearby = new ArrayList<>();
    private AIPlayerEntity selected;
    private EditBox commandBox;
    private int refreshTicks;

    public AIPlayersControlScreen() {
        super(Component.literal("AI Players ????"));
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, 12, 12, 0xFFFFFF);
        graphics.drawString(this.font, "?? AI?" + this.nearby.size(), 12, 28, 0xC0C0C0);
        graphics.drawString(this.font, "?????" + this.getSelectedName(), 220, 28, 0xC0C0C0);
        graphics.drawString(this.font, AIPlayersVoiceClient.getStatusSummary(), 220, 44, 0x88D8FF);
        graphics.drawString(this.font, "?????? AI?????????/??????????????", 12, this.height - 18, 0x909090);
    }

    private void rebuildPanel() {
        this.clearWidgets();
        int leftX = 12;
        int topY = 48;
        int buttonWidth = 180;
        int rowHeight = 22;

        int shown = Math.min(6, this.nearby.size());
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

        int gridX = 220;
        int gridY = 72;
        this.addActionButton(gridX, gridY, "??", () -> this.sendTargetMessage("??"));
        this.addActionButton(gridX + 110, gridY, "??", () -> this.sendTargetMessage("??"));
        this.addActionButton(gridX, gridY + 24, "??", () -> this.sendTargetMessage("??"));
        this.addActionButton(gridX + 110, gridY + 24, "??", () -> this.sendTargetMessage("??"));
        this.addActionButton(gridX, gridY + 48, "??", () -> this.sendTargetMessage("??"));
        this.addActionButton(gridX + 110, gridY + 48, "??", () -> this.sendTargetMessage("??"));
        this.addActionButton(gridX, gridY + 72, "???", () -> this.sendTargetMessage("?????"));
        this.addActionButton(gridX + 110, gridY + 72, "??", () -> this.sendTargetMessage("????"));
        this.addActionButton(gridX, gridY + 96, "??", () -> this.sendTargetMessage("????"));
        this.addActionButton(gridX + 110, gridY + 96, "??", () -> this.sendTargetMessage("??"));
        this.addActionButton(gridX, gridY + 120, "??", () -> this.sendTargetMessage("??"));
        this.addActionButton(gridX + 110, gridY + 120, "??", () -> this.sendTargetMessage("??"));
        this.addActionButton(gridX, gridY + 144, AIPlayersVoiceClient.isRecording() ? "????" : "????", () -> AIPlayersVoiceClient.toggleRecording(this.getSelectedName()));
        this.addActionButton(gridX + 110, gridY + 144, "??", this::refreshNearbyAndRebuild);

        this.commandBox = this.addRenderableWidget(new EditBox(this.font, 12, this.height - 42, this.width - 96, 20, Component.literal("?????")));
        this.commandBox.setMaxLength(160);
        this.commandBox.setHint(Component.literal("????????? / ?? / ???"));
        this.commandBox.setValue("");
        this.addRenderableWidget(Button.builder(Component.literal("??"), ignored -> this.sendCustomMessage())
                .bounds(this.width - 76, this.height - 42, 64, 20)
                .build());
    }

    private void addActionButton(int x, int y, String label, Runnable action) {
        this.addRenderableWidget(Button.builder(Component.literal(label), ignored -> action.run()).bounds(x, y, 100, 20).build());
    }

    private void refreshNearbyAndRebuild() {
        this.refreshNearby();
        this.rebuildPanel();
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

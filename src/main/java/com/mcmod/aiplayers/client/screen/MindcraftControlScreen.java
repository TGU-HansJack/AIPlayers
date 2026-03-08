package com.mcmod.aiplayers.client.screen;

import com.mcmod.aiplayers.client.AIPlayersClient;
import com.mcmod.aiplayers.client.voice.AIPlayersVoiceClient;
import com.mcmod.aiplayers.mindcraft.MindcraftBotInfo;
import com.mcmod.aiplayers.mindcraft.MindcraftBridgeClient;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class MindcraftControlScreen extends Screen {
    private final List<MindcraftBotInfo> bots = new ArrayList<>();
    private EditBox commandBox;
    private int selectedIndex = -1;
    private int refreshTicks;
    private boolean refreshing;
    private String statusMessage = "正在读取本地 mindcraft 面板...";

    public MindcraftControlScreen() {
        super(Component.literal("AIPlayers Mindcraft 控制面板"));
    }

    @Override
    protected void init() {
        this.commandBox = new EditBox(this.font, 12, this.height - 46, this.width - 170, 20, Component.literal("命令"));
        this.commandBox.setHint(Component.literal("输入内容后发送到当前 bot"));
        this.addRenderableWidget(this.commandBox);
        this.addRenderableWidget(Button.builder(Component.literal("发送"), button -> sendMessage())
                .bounds(this.width - 148, this.height - 46, 64, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("刷新"), button -> refreshBots())
                .bounds(this.width - 80, this.height - 46, 64, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("打开面板"), button -> openPanel())
                .bounds(this.width - 148, this.height - 22, 132, 20)
                .build());
        refreshBots();
    }

    @Override
    public void tick() {
        super.tick();
        this.refreshTicks++;
        if (this.refreshTicks >= 20) {
            this.refreshTicks = 0;
            refreshBots();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
            sendMessage();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, 12, 12, 0xFFFFFF);
        graphics.drawString(this.font, this.statusMessage, 12, 28, 0x88D8FF);
        graphics.drawString(this.font, AIPlayersVoiceClient.getStatusSummary(), 12, 42, 0xC8E6FF);

        int y = 66;
        for (int index = 0; index < this.bots.size(); index++) {
            MindcraftBotInfo bot = this.bots.get(index);
            int color = index == this.selectedIndex ? 0xA8FFB0 : 0xE0E0E0;
            graphics.drawString(this.font, (index == this.selectedIndex ? "> " : "  ") + bot.compactSummary(), 12, y, color);
            y += 14;
        }

        MindcraftBotInfo selected = selectedBot();
        int detailsTop = Math.max(120, y + 8);
        graphics.drawString(this.font, "当前目标：" + (selected == null ? "未选择" : selected.name), 12, detailsTop, 0xFFFFFF);
        if (selected != null) {
            graphics.drawString(this.font, selected.gameplaySummary(), 12, detailsTop + 16, 0xC0FFC0);
            graphics.drawString(this.font, "动作：" + selected.actionSummary(), 12, detailsTop + 32, 0xFFE8A8);
            graphics.drawString(this.font, "最近回复：" + selected.lastMessage(), 12, detailsTop + 48, 0xC8E6FF);
            graphics.drawString(this.font, "Viewer: localhost:" + selected.viewerPort + " | Panel: " + MindcraftBridgeClient.panelUri(), 12, detailsTop + 64, 0xAAAAAA);
        }
    }

    private void refreshBots() {
        if (this.refreshing) {
            return;
        }
        this.refreshing = true;
        CompletableFuture.supplyAsync(() -> {
            try {
                return MindcraftBridgeClient.listBots();
            } catch (IOException | InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }).whenComplete((list, throwable) -> Minecraft.getInstance().execute(() -> {
            this.refreshing = false;
            if (throwable != null) {
                Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                this.statusMessage = "本地面板不可达：" + cause.getMessage();
                return;
            }
            this.bots.clear();
            this.bots.addAll(list);
            this.statusMessage = this.bots.isEmpty() ? "当前没有 bot。先用 /aiplayers spawn <name> 创建。" : "已同步本地 bot 状态。";
            if (this.bots.isEmpty()) {
                this.selectedIndex = -1;
                AIPlayersClient.setSelectedAiName("");
            } else if (this.selectedIndex < 0 || this.selectedIndex >= this.bots.size()) {
                this.selectedIndex = 0;
                AIPlayersClient.setSelectedAiName(this.bots.getFirst().name);
            }
            rebuildBotButtons();
        }));
    }

    private void rebuildBotButtons() {
        this.clearWidgets();
        this.addRenderableWidget(this.commandBox);
        this.addRenderableWidget(Button.builder(Component.literal("发送"), button -> sendMessage())
                .bounds(this.width - 148, this.height - 46, 64, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("刷新"), button -> refreshBots())
                .bounds(this.width - 80, this.height - 46, 64, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("打开面板"), button -> openPanel())
                .bounds(this.width - 148, this.height - 22, 132, 20)
                .build());

        int y = 62;
        for (int index = 0; index < this.bots.size(); index++) {
            MindcraftBotInfo bot = this.bots.get(index);
            final int targetIndex = index;
            this.addRenderableWidget(Button.builder(Component.literal(bot.name), button -> selectBot(targetIndex))
                    .bounds(this.width - 140, y - 4, 124, 16)
                    .build());
            y += 18;
        }
    }

    private void selectBot(int index) {
        if (index < 0 || index >= this.bots.size()) {
            return;
        }
        this.selectedIndex = index;
        AIPlayersClient.setSelectedAiName(this.bots.get(index).name);
    }

    private void sendMessage() {
        MindcraftBotInfo selected = selectedBot();
        Minecraft minecraft = Minecraft.getInstance();
        if (selected == null || this.commandBox == null || this.commandBox.getValue().isBlank()) {
            return;
        }
        if (minecraft.getConnection() != null) {
            AIPlayersClient.setSelectedAiName(selected.name);
            minecraft.getConnection().sendChat("@" + selected.name + " " + this.commandBox.getValue().trim());
            this.statusMessage = "已发送到 " + selected.name;
            this.commandBox.setValue("");
        }
    }

    private void openPanel() {
        try {
            URI uri = MindcraftBridgeClient.panelUri();
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(uri);
            }
            this.statusMessage = "已尝试打开：" + uri;
        } catch (Exception ex) {
            this.statusMessage = "打开面板失败：" + ex.getMessage();
        }
    }

    private MindcraftBotInfo selectedBot() {
        return this.selectedIndex >= 0 && this.selectedIndex < this.bots.size() ? this.bots.get(this.selectedIndex) : null;
    }
}

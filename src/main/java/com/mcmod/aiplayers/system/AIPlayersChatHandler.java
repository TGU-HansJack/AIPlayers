package com.mcmod.aiplayers.system;

import com.mcmod.aiplayers.mindcraft.MindcraftIntegrationService;
import com.mcmod.aiplayers.mindcraft.MindcraftSessionSavedData;
import java.io.IOException;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;

public final class AIPlayersChatHandler {
    private AIPlayersChatHandler() {
    }

    public static void onServerChat(ServerChatEvent event) {
        String raw = event.getRawText().trim();
        if (!raw.startsWith("@") || raw.length() <= 1) {
            return;
        }

        ServerPlayer player = event.getPlayer();
        int firstSpace = raw.indexOf(' ');
        String botName = firstSpace >= 0 ? raw.substring(1, firstSpace) : raw.substring(1);
        String content = firstSpace >= 0 ? raw.substring(firstSpace + 1).trim() : "";
        MindcraftSessionSavedData.MindcraftBotSession session = MindcraftIntegrationService.findOwnedSession(player, botName);
        if (session == null) {
            return;
        }
        if (content.isBlank()) {
            player.sendSystemMessage(Component.literal("[" + botName + "] 请输入要发送的内容。"));
            return;
        }
        try {
            MindcraftIntegrationService.sendMessage(player, botName, content);
            player.sendSystemMessage(Component.literal("[" + botName + "] 已收到。"));
        } catch (IOException | InterruptedException ex) {
            player.sendSystemMessage(Component.literal("[" + botName + "] 转发失败：" + ex.getMessage()));
        }
    }
}

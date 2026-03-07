package com.mcmod.aiplayers.system;

import com.mcmod.aiplayers.entity.AIPlayerEntity;
import java.util.Comparator;
import java.util.List;
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
        String targetToken = firstSpace >= 0 ? raw.substring(1, firstSpace) : raw.substring(1);
        String content = firstSpace >= 0 ? raw.substring(firstSpace + 1).trim() : "";

        AIPlayerEntity companion = findTargetCompanion(player, targetToken);
        if (companion == null) {
            return;
        }

        String response = companion.executeConversation(player, content);
        player.sendSystemMessage(Component.literal("[" + companion.getAIName() + "] " + response));
    }

    private static AIPlayerEntity findTargetCompanion(ServerPlayer player, String targetToken) {
        List<AIPlayerEntity> nearby = player.level()
                .getEntitiesOfClass(AIPlayerEntity.class, player.getBoundingBox().inflate(64.0D))
                .stream()
                .sorted(Comparator.comparingDouble(entity -> player.distanceToSqr(entity)))
                .toList();

        if (nearby.isEmpty()) {
            return null;
        }

        if (targetToken.equalsIgnoreCase("ai") || targetToken.equalsIgnoreCase("bot")) {
            return nearby.stream().filter(entity -> entity.canReceiveOrdersFrom(player)).findFirst().orElse(nearby.getFirst());
        }

        return nearby.stream()
                .filter(entity -> entity.getAIName().equalsIgnoreCase(targetToken))
                .findFirst()
                .orElse(null);
    }
}
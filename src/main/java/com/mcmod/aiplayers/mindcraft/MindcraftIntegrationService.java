package com.mcmod.aiplayers.mindcraft;

import com.mcmod.aiplayers.mindcraft.MindcraftBridgeClient.CreateBotRequest;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class MindcraftIntegrationService {
    private MindcraftIntegrationService() {
    }

    public static MindcraftBotInfo spawnBot(ServerPlayer owner, String botName) throws IOException, InterruptedException {
        MinecraftServer server = owner.level().getServer();
        ensureReady(server);
        int port = resolveJoinPort(server);
        CreateBotRequest request = new CreateBotRequest();
        request.name = botName;
        request.ownerName = owner.getName().getString();
        request.ownerUuid = owner.getUUID().toString();
        request.host = MindcraftConfigManager.getConfig().host();
        request.port = port;
        request.profilePath = MindcraftConfigManager.getConfig().defaultProfile();
        MindcraftBotInfo created = MindcraftBridgeClient.createBot(request);
        MindcraftSessionSavedData.get(server).upsert(created, owner.getUUID(), owner.getName().getString());
        return created;
    }

    public static List<MindcraftBotInfo> listLiveBots(MinecraftServer server) throws IOException, InterruptedException {
        ensureReady(server);
        List<MindcraftBotInfo> bots = MindcraftBridgeClient.listBots();
        MindcraftSessionSavedData data = MindcraftSessionSavedData.get(server);
        for (MindcraftBotInfo bot : bots) {
            data.upsert(bot, null, bot.ownerName);
        }
        return bots;
    }

    public static Collection<MindcraftSessionSavedData.MindcraftBotSession> listStoredSessions(MinecraftServer server) {
        return MindcraftSessionSavedData.get(server).sessions();
    }

    public static MindcraftBotInfo getBotStatus(MinecraftServer server, String botName) throws IOException, InterruptedException {
        ensureReady(server);
        MindcraftBotInfo info = MindcraftBridgeClient.getBot(botName);
        if (info != null) {
            MindcraftSessionSavedData.get(server).upsert(info, null, info.ownerName);
        }
        return info;
    }

    public static void sendMessage(ServerPlayer sender, String botName, String message) throws IOException, InterruptedException {
        MindcraftSessionSavedData.MindcraftBotSession session = requireOwnedSession(sender, botName);
        ensureReady(sender.level().getServer());
        MindcraftBridgeClient.sendMessage(session.name(), sender.getName().getString(), message);
    }

    public static void stopBot(ServerPlayer sender, String botName) throws IOException, InterruptedException {
        MindcraftSessionSavedData.MindcraftBotSession session = requireOwnedSession(sender, botName);
        ensureReady(sender.level().getServer());
        MindcraftBridgeClient.stopBot(session.name());
    }

    public static void removeBot(ServerPlayer sender, String botName) throws IOException, InterruptedException {
        MindcraftSessionSavedData.MindcraftBotSession session = requireOwnedSession(sender, botName);
        ensureReady(sender.level().getServer());
        MindcraftBridgeClient.removeBot(session.name());
        MindcraftSessionSavedData.get(sender.level().getServer()).remove(session.name());
    }

    public static MindcraftSessionSavedData.MindcraftBotSession findOwnedSession(ServerPlayer sender, String botName) {
        MindcraftSessionSavedData.MindcraftBotSession session = MindcraftSessionSavedData.get(sender.level().getServer()).get(botName);
        if (session != null && session.isOwnedBy(sender)) {
            return session;
        }
        return null;
    }

    public static String panelUrl() {
        return MindcraftBridgeClient.panelUri().toString();
    }

    public static void ensureReady(MinecraftServer server) throws IOException, InterruptedException {
        MindcraftConfigManager.initialize();
        validateServerJoinability(server);
        MindcraftSidecarManager.ensureRunning(server);
    }

    private static MindcraftSessionSavedData.MindcraftBotSession requireOwnedSession(ServerPlayer sender, String botName) throws IOException, InterruptedException {
        MindcraftSessionSavedData.MindcraftBotSession session = findOwnedSession(sender, botName);
        if (session != null) {
            return session;
        }
        for (MindcraftBotInfo bot : listLiveBots(sender.level().getServer())) {
            if (bot.name.equalsIgnoreCase(botName) && (bot.ownerUuid == null || bot.ownerUuid.isBlank() || bot.ownerUuid.equals(sender.getUUID().toString()))) {
                MindcraftSessionSavedData.get(sender.level().getServer()).upsert(bot, sender.getUUID(), sender.getName().getString());
                return MindcraftSessionSavedData.get(sender.level().getServer()).get(botName);
            }
        }
        throw new IOException("未找到你拥有的 bot：" + botName);
    }

    private static void validateServerJoinability(MinecraftServer server) throws IOException {
        if (resolveJoinPort(server) <= 0) {
            throw new IOException("当前世界没有对真实玩家开放端口。请使用本地专用服，或先把单人世界开放为 LAN。 ");
        }
    }

    private static int resolveJoinPort(MinecraftServer server) {
        return server == null ? -1 : server.getPort();
    }
}

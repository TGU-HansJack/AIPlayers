package com.mcmod.aiplayers.system;

import com.mcmod.aiplayers.AIPlayersMod;
import com.mcmod.aiplayers.mindcraft.MindcraftBotInfo;
import com.mcmod.aiplayers.mindcraft.MindcraftIntegrationService;
import com.mcmod.aiplayers.mindcraft.MindcraftSessionSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.io.IOException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class AIPlayersCommands {
    private static final SimpleCommandExceptionType PLAYER_ONLY = new SimpleCommandExceptionType(Component.literal("该命令只能由玩家执行。"));

    private AIPlayersCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("aiplayers")
                .then(Commands.literal("spawn")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(AIPlayersCommands::spawn)))
                .then(Commands.literal("list")
                        .executes(AIPlayersCommands::list))
                .then(Commands.literal("status")
                        .executes(AIPlayersCommands::list)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(AIPlayersCommands::status)))
                .then(Commands.literal("send")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(AIPlayersCommands::send))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(AIPlayersCommands::stop)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(AIPlayersCommands::remove)))
                .then(Commands.literal("panel")
                        .executes(AIPlayersCommands::panel))
                .then(Commands.literal("voice")
                        .then(Commands.literal("status").executes(AIPlayersCommands::voiceStatus))
                        .then(Commands.literal("reload").executes(AIPlayersCommands::voiceReload))
                        .then(Commands.literal("test").executes(AIPlayersCommands::voiceTest))));
    }

    private static int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "name");
        try {
            MindcraftBotInfo created = MindcraftIntegrationService.spawnBot(player, name);
            context.getSource().sendSuccess(() -> Component.literal("已创建真实玩家 bot：" + created.compactSummary()), true);
            return 1;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            AIPlayersMod.LOGGER.error("Spawn bot interrupted for {}", name, ex);
            context.getSource().sendFailure(Component.literal("创建 bot 失败：" + describeException(ex)));
            return 0;
        } catch (Exception ex) {
            AIPlayersMod.LOGGER.error("Spawn bot failed for {}", name, ex);
            context.getSource().sendFailure(Component.literal("创建 bot 失败：" + describeException(ex)));
            return 0;
        }
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        try {
            List<MindcraftBotInfo> bots = MindcraftIntegrationService.listLiveBots(context.getSource().getServer());
            if (bots.isEmpty()) {
                context.getSource().sendSuccess(() -> Component.literal("当前没有在线或已注册的 bot。"), false);
                return 1;
            }
            for (MindcraftBotInfo bot : bots) {
                context.getSource().sendSuccess(() -> Component.literal(bot.compactSummary()), false);
            }
            return bots.size();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            AIPlayersMod.LOGGER.error("List bots interrupted", ex);
            context.getSource().sendFailure(Component.literal("无法连接本地面板：" + describeException(ex)));
            return 0;
        } catch (IOException ex) {
            int shown = 0;
            for (MindcraftSessionSavedData.MindcraftBotSession session : MindcraftIntegrationService.listStoredSessions(context.getSource().getServer())) {
                shown++;
                context.getSource().sendSuccess(() -> Component.literal(session.name() + " | " + session.status() + " | 主人=" + session.ownerName()), false);
            }
            if (shown == 0) {
                context.getSource().sendFailure(Component.literal("无法连接本地面板：" + describeException(ex)));
                return 0;
            }
            context.getSource().sendSuccess(() -> Component.literal("本地面板不可达，以下为本地缓存会话："), false);
            return shown;
        } catch (Exception ex) {
            AIPlayersMod.LOGGER.error("List bots failed", ex);
            context.getSource().sendFailure(Component.literal("列出 bot 失败：" + describeException(ex)));
            return 0;
        }
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        try {
            MindcraftBotInfo info = MindcraftIntegrationService.getBotStatus(context.getSource().getServer(), name);
            if (info == null) {
                context.getSource().sendFailure(Component.literal("未找到 bot：" + name));
                return 0;
            }
            context.getSource().sendSuccess(() -> Component.literal(info.compactSummary()), false);
            context.getSource().sendSuccess(() -> Component.literal(info.gameplaySummary()), false);
            context.getSource().sendSuccess(() -> Component.literal("动作：" + info.actionSummary()), false);
            context.getSource().sendSuccess(() -> Component.literal("最近回复：" + info.lastMessage()), false);
            return 1;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            AIPlayersMod.LOGGER.error("Status bot interrupted for {}", name, ex);
            context.getSource().sendFailure(Component.literal("查询状态失败：" + describeException(ex)));
            return 0;
        } catch (Exception ex) {
            AIPlayersMod.LOGGER.error("Status bot failed for {}", name, ex);
            context.getSource().sendFailure(Component.literal("查询状态失败：" + describeException(ex)));
            return 0;
        }
    }

    private static int send(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(context);
        String name = StringArgumentType.getString(context, "name");
        String message = StringArgumentType.getString(context, "message");
        try {
            MindcraftIntegrationService.sendMessage(player, name, message);
            context.getSource().sendSuccess(() -> Component.literal("已发送到 " + name + "：" + message), false);
            return 1;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            AIPlayersMod.LOGGER.error("Send bot message interrupted for {}", name, ex);
            context.getSource().sendFailure(Component.literal("发送失败：" + describeException(ex)));
            return 0;
        } catch (Exception ex) {
            AIPlayersMod.LOGGER.error("Send bot message failed for {}", name, ex);
            context.getSource().sendFailure(Component.literal("发送失败：" + describeException(ex)));
            return 0;
        }
    }

    private static int stop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(context);
        String name = StringArgumentType.getString(context, "name");
        try {
            MindcraftIntegrationService.stopBot(player, name);
            context.getSource().sendSuccess(() -> Component.literal("已停止 bot：" + name), true);
            return 1;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            AIPlayersMod.LOGGER.error("Stop bot interrupted for {}", name, ex);
            context.getSource().sendFailure(Component.literal("停止失败：" + describeException(ex)));
            return 0;
        } catch (Exception ex) {
            AIPlayersMod.LOGGER.error("Stop bot failed for {}", name, ex);
            context.getSource().sendFailure(Component.literal("停止失败：" + describeException(ex)));
            return 0;
        }
    }

    private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(context);
        String name = StringArgumentType.getString(context, "name");
        try {
            MindcraftIntegrationService.removeBot(player, name);
            context.getSource().sendSuccess(() -> Component.literal("已删除 bot：" + name), true);
            return 1;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            AIPlayersMod.LOGGER.error("Remove bot interrupted for {}", name, ex);
            context.getSource().sendFailure(Component.literal("删除失败：" + describeException(ex)));
            return 0;
        } catch (Exception ex) {
            AIPlayersMod.LOGGER.error("Remove bot failed for {}", name, ex);
            context.getSource().sendFailure(Component.literal("删除失败：" + describeException(ex)));
            return 0;
        }
    }

    private static int panel(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("本地控制面板：" + MindcraftIntegrationService.panelUrl()), false);
        return 1;
    }

    private static int voiceStatus(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(VoiceCommandSupport.statusSummary()), false);
        return 1;
    }

    private static int voiceReload(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(VoiceCommandSupport.reloadAndDescribe()), false);
        return 1;
    }

    private static int voiceTest(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(VoiceCommandSupport.testTts()), false);
        return 1;
    }

    private static ServerPlayer requirePlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            throw PLAYER_ONLY.create();
        }
        return player;
    }

    private static String describeException(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }
}
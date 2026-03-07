package com.mcmod.aiplayers.system;

import com.mcmod.aiplayers.ai.AIServiceManager;
import com.mcmod.aiplayers.entity.AIPlayerAction;
import com.mcmod.aiplayers.entity.AgentConfigManager;
import com.mcmod.aiplayers.entity.AIPlayerEntity;
import com.mcmod.aiplayers.entity.AIPlayerMode;
import com.mcmod.aiplayers.system.BlueprintRegistry;
import com.mcmod.aiplayers.registry.ModEntities;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class AIPlayersCommands {
    private static final SimpleCommandExceptionType NOT_AN_AI_PLAYER = new SimpleCommandExceptionType(Component.literal("目标不是 AI Players 实体。"));
    private static final SimpleCommandExceptionType INVALID_MODE = new SimpleCommandExceptionType(Component.literal("未知模式。可用模式：idle, follow, guard, gather_wood, mine, explore, build_shelter, survive"));
    private static final SimpleCommandExceptionType INVALID_ACTION = new SimpleCommandExceptionType(Component.literal("未知动作。可用动作：jump, crouch, stand, look_up, look_down, look_owner, recover"));

    private AIPlayersCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("aiplayers")
                .then(Commands.literal("spawn")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> spawn(context, StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("mode")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests(AIPlayersCommands::suggestModes)
                                        .executes(AIPlayersCommands::setMode))))
                .then(Commands.literal("action")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("action", StringArgumentType.word())
                                        .suggests(AIPlayersCommands::suggestActions)
                                        .executes(AIPlayersCommands::performAction))))
                .then(Commands.literal("status")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(AIPlayersCommands::status)))
                .then(Commands.literal("inventory")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(AIPlayersCommands::inventory)))
                .then(Commands.literal("memory")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(AIPlayersCommands::memory)))
                .then(Commands.literal("plan")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(AIPlayersCommands::plan)))
                .then(Commands.literal("blueprint")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("blueprint", StringArgumentType.word())
                                        .suggests(AIPlayersCommands::suggestBlueprints)
                                        .executes(AIPlayersCommands::blueprint))))
                .then(Commands.literal("api")
                        .then(Commands.literal("status").executes(AIPlayersCommands::apiStatus))
                        .then(Commands.literal("reload").executes(AIPlayersCommands::apiReload))
                        .then(Commands.literal("enable").executes(AIPlayersCommands::apiEnable))
                        .then(Commands.literal("disable").executes(AIPlayersCommands::apiDisable))));
    }

    private static int spawn(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        AIPlayerEntity companion = ModEntities.AI_PLAYER.get().create(player.level(), EntitySpawnReason.COMMAND);
        if (companion == null) {
            throw new SimpleCommandExceptionType(Component.literal("AI 玩家实体创建失败。")).create();
        }

        companion.snapTo(player.getX() + 1.0D, player.getY(), player.getZ() + 1.0D, player.getYRot(), 0.0F);
        companion.initializeCompanion(player, name);
        player.level().addFreshEntity(companion);

        context.getSource().sendSuccess(() -> Component.literal("已生成 AI 玩家：" + name), true);
        return 1;
    }

    private static int setMode(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        AIPlayerMode mode = AIPlayerMode.fromCommand(StringArgumentType.getString(context, "mode"));
        if (mode == null) {
            throw INVALID_MODE.create();
        }

        List<AIPlayerEntity> companions = resolveCompanions(context, "targets");
        ServerPlayer commander = context.getSource().getEntity() instanceof ServerPlayer player ? player : null;
        int updated = 0;
        for (AIPlayerEntity companion : companions) {
            if (commander != null && !companion.canReceiveOrdersFrom(commander)) {
                continue;
            }
            companion.applyCommandedMode(commander, mode);
            updated++;
        }

        int updatedCount = updated;
        context.getSource().sendSuccess(() -> Component.literal("已切换 " + updatedCount + " 个 AI 玩家到模式：" + mode.displayName()), true);
        return updated;
    }

    private static int performAction(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        AIPlayerAction action = AIPlayerAction.fromCommand(StringArgumentType.getString(context, "action"));
        if (action == null) {
            throw INVALID_ACTION.create();
        }

        List<AIPlayerEntity> companions = resolveCompanions(context, "targets");
        ServerPlayer commander = context.getSource().getEntity() instanceof ServerPlayer player ? player : null;
        int updated = 0;
        for (AIPlayerEntity companion : companions) {
            if (commander != null && !companion.canReceiveOrdersFrom(commander)) {
                continue;
            }
            companion.performAction(action);
            updated++;
        }

        int updatedCount = updated;
        context.getSource().sendSuccess(() -> Component.literal("已让 " + updatedCount + " 个 AI 玩家执行动作：" + action.displayName()), true);
        return updated;
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        AIPlayerEntity companion = resolveCompanion(context, "target");
        context.getSource().sendSuccess(() -> Component.literal(companion.getStatusSummary()), false);
        return 1;
    }

    private static int inventory(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        AIPlayerEntity companion = resolveCompanion(context, "target");
        context.getSource().sendSuccess(() -> Component.literal("背包内容：" + companion.getDetailedInventorySummary()), false);
        return 1;
    }

    private static int memory(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        AIPlayerEntity companion = resolveCompanion(context, "target");
        context.getSource().sendSuccess(() -> Component.literal(companion.getMemorySummary()), false);
        return 1;
    }

    private static int plan(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        AIPlayerEntity companion = resolveCompanion(context, "target");
        context.getSource().sendSuccess(() -> Component.literal("当前规划：" + companion.getPlanSummary()), false);
        return 1;
    }

    private static int blueprint(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        AIPlayerEntity companion = resolveCompanion(context, "target");
        String blueprintId = StringArgumentType.getString(context, "blueprint");
        companion.selectBlueprint(blueprintId);
        context.getSource().sendSuccess(() -> Component.literal("已将 " + companion.getAIName() + " 的蓝图切换为：" + BlueprintRegistry.get(companion.getActiveBlueprintId()).displayName()), false);
        return 1;
    }

    private static int apiStatus(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(AIServiceManager.getStatusSummary()), false);
        return 1;
    }

    private static int apiReload(CommandContext<CommandSourceStack> context) {
        AIServiceManager.reload();
        context.getSource().sendSuccess(() -> Component.literal("已重新加载 AI 接口配置。"), false);
        context.getSource().sendSuccess(() -> Component.literal(AIServiceManager.getStatusSummary()), false);
        return 1;
    }

    private static int apiEnable(CommandContext<CommandSourceStack> context) {
        AIServiceManager.setEnabled(true);
        context.getSource().sendSuccess(() -> Component.literal("已启用 AI 接口。"), false);
        context.getSource().sendSuccess(() -> Component.literal(AIServiceManager.getStatusSummary()), false);
        return 1;
    }

    private static int apiDisable(CommandContext<CommandSourceStack> context) {
        AIServiceManager.setEnabled(false);
        context.getSource().sendSuccess(() -> Component.literal("已关闭 AI 接口。"), false);
        context.getSource().sendSuccess(() -> Component.literal(AIServiceManager.getStatusSummary()), false);
        return 1;
    }

    private static AIPlayerEntity resolveCompanion(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(context, name);
        if (!(entity instanceof AIPlayerEntity companion)) {
            throw NOT_AN_AI_PLAYER.create();
        }
        return companion;
    }

    private static List<AIPlayerEntity> resolveCompanions(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        Collection<? extends Entity> entities = EntityArgument.getEntities(context, name);
        List<AIPlayerEntity> companions = entities.stream()
                .filter(AIPlayerEntity.class::isInstance)
                .map(AIPlayerEntity.class::cast)
                .toList();

        if (companions.isEmpty()) {
            throw NOT_AN_AI_PLAYER.create();
        }
        return companions;
    }

    private static CompletableFuture<Suggestions> suggestModes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(AIPlayerMode.commandNames(), builder);
    }

    private static CompletableFuture<Suggestions> suggestActions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(AIPlayerAction.commandNames(), builder);
    }

    private static CompletableFuture<Suggestions> suggestBlueprints(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(BlueprintRegistry.ids(), builder);
    }
}

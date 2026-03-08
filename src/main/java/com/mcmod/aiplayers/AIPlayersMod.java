package com.mcmod.aiplayers;

import com.mcmod.aiplayers.ai.AIServiceManager;
import com.mcmod.aiplayers.client.AIPlayersClient;
import com.mcmod.aiplayers.entity.AgentConfigManager;
import com.mcmod.aiplayers.knowledge.KnowledgeManager;
import com.mcmod.aiplayers.mindcraft.MindcraftConfigManager;
import com.mcmod.aiplayers.registry.ModEntities;
import com.mcmod.aiplayers.registry.ModMenus;
import com.mcmod.aiplayers.system.AIPlayersChatHandler;
import com.mcmod.aiplayers.system.AIPlayersCommands;
import com.mcmod.aiplayers.system.PathRegressionRunner;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(AIPlayersMod.MODID)
public class AIPlayersMod {
    public static final String MODID = "aiplayers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AIPlayersMod(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        AIServiceManager.initialize();
        AgentConfigManager.initialize();
        MindcraftConfigManager.initialize();
        KnowledgeManager.initialize();
        ModEntities.ENTITY_TYPES.register(modBusGroup);
        ModMenus.MENU_TYPES.register(modBusGroup);
        EntityAttributeCreationEvent.BUS.addListener(ModEntities::registerAttributes);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            AIPlayersClient.register();
            FMLClientSetupEvent.getBus(modBusGroup).addListener(AIPlayersClient::onClientSetup);
        }

        RegisterCommandsEvent.BUS.addListener(AIPlayersCommands::register);
        ServerChatEvent.BUS.addListener(AIPlayersChatHandler::onServerChat);
        PathRegressionRunner.register();
    }
}

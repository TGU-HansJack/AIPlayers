package com.mcmod.aiplayers.client;

import com.mcmod.aiplayers.AIPlayersMod;
import com.mcmod.aiplayers.client.render.AIPlayerRenderer;
import com.mcmod.aiplayers.client.screen.AIPlayersControlScreen;
import com.mcmod.aiplayers.client.voice.AIPlayersVoiceClient;
import com.mcmod.aiplayers.registry.ModEntities;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

public final class AIPlayersClient {
    private static final KeyMapping.Category CONTROL_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(AIPlayersMod.MODID, "controls"));
    private static final KeyMapping OPEN_PANEL_KEY = new KeyMapping("key.aiplayers.open_panel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CONTROL_CATEGORY);
    private static final KeyMapping VOICE_KEY = new KeyMapping("key.aiplayers.voice", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CONTROL_CATEGORY);
    private static String selectedAiName = "ai";

    private AIPlayersClient() {
    }

    public static void register() {
        AIPlayersVoiceClient.initialize();
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(AIPlayersClient::registerRenderers);
        RegisterKeyMappingsEvent.BUS.addListener(AIPlayersClient::registerKeyMappings);
        TickEvent.ClientTickEvent.Post.BUS.addListener(AIPlayersClient::onClientTick);
        ClientChatReceivedEvent.BUS.addListener(AIPlayersClient::onChatReceived);
    }

    public static String getSelectedAiName() {
        return selectedAiName;
    }

    public static void setSelectedAiName(String name) {
        if (name != null && !name.isBlank()) {
            selectedAiName = name;
        }
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.AI_PLAYER.get(), AIPlayerRenderer::new);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PANEL_KEY);
        event.register(VOICE_KEY);
    }

    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        while (OPEN_PANEL_KEY.consumeClick()) {
            minecraft.setScreen(new AIPlayersControlScreen());
        }
        while (VOICE_KEY.consumeClick()) {
            AIPlayersVoiceClient.toggleRecording(selectedAiName);
        }
    }

    private static void onChatReceived(ClientChatReceivedEvent event) {
        if (event.getMessage() != null) {
            AIPlayersVoiceClient.handleSystemMessage(event.getMessage().getString());
        }
    }
}

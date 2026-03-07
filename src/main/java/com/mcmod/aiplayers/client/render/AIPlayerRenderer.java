package com.mcmod.aiplayers.client.render;

import com.mcmod.aiplayers.entity.AIPlayerEntity;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

@SuppressWarnings({"rawtypes", "unchecked"})
public class AIPlayerRenderer extends HumanoidMobRenderer<AIPlayerEntity, AvatarRenderState, PlayerModel> {
    private final PlayerModel wideModel;
    private final PlayerModel slimModel;

    public AIPlayerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wideModel = (PlayerModel)this.model;
        this.slimModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.addLayer(new HumanoidArmorLayer(this, ModelLayers.PLAYER_ARMOR, context.getEquipmentRenderer()));
        this.addLayer(new ItemInHandLayer<>(this));
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return state.skin != null ? state.skin.body().texturePath() : DefaultPlayerSkin.getDefaultTexture();
    }

    @Override
    public void extractRenderState(AIPlayerEntity entity, AvatarRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTick, this.itemModelResolver);
        PlayerSkin skin = this.resolveSkin(entity);
        state.skin = skin;
        state.showHat = true;
        state.isSpectator = false;
        this.model = skin.model() == PlayerModelType.SLIM ? this.slimModel : this.wideModel;
    }

    private PlayerSkin resolveSkin(AIPlayerEntity entity) {
        UUID ownerId = this.parseUuid(entity.getOwnerIdString());
        if (ownerId != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getConnection() != null) {
                PlayerInfo info = minecraft.getConnection().getPlayerInfo(ownerId);
                if (info != null && info.getSkin() != null) {
                    return info.getSkin();
                }
            }
            return DefaultPlayerSkin.get(ownerId);
        }
        return DefaultPlayerSkin.get(entity.getUUID());
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

package com.mcmod.aiplayers.registry;

import com.mcmod.aiplayers.AIPlayersMod;
import com.mcmod.aiplayers.entity.AIPlayerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AIPlayersMod.MODID);

    public static final RegistryObject<EntityType<AIPlayerEntity>> AI_PLAYER = ENTITY_TYPES.register(
            "ai_player",
            () -> EntityType.Builder.of(AIPlayerEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build(ENTITY_TYPES.key("ai_player")));

    private ModEntities() {
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(AI_PLAYER.get(), AIPlayerEntity.createAttributes().build());
    }
}

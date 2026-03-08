package com.mcmod.aiplayers.registry;

import com.mcmod.aiplayers.AIPlayersMod;
import com.mcmod.aiplayers.menu.AIPlayerBackpackMenu;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, AIPlayersMod.MODID);

    public static final RegistryObject<MenuType<AIPlayerBackpackMenu>> AI_PLAYER_BACKPACK = MENU_TYPES.register(
            "ai_player_backpack",
            () -> new MenuType<>(AIPlayerBackpackMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {
    }
}

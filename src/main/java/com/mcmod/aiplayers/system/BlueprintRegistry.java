package com.mcmod.aiplayers.system;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Blocks;

public final class BlueprintRegistry {
    private static final Map<String, BlueprintTemplate> TEMPLATES = new LinkedHashMap<>();

    static {
        register(new BlueprintTemplate("shelter", "???", List.of(
                place(-1, 0, -1), place(0, 0, -1), place(1, 0, -1),
                place(-1, 1, -1), place(0, 1, -1), place(1, 1, -1),
                place(-1, 0, 0), place(-1, 1, 0), place(1, 0, 0), place(1, 1, 0),
                place(-1, 0, 1), place(1, 0, 1),
                place(-1, 2, -1), place(0, 2, -1), place(1, 2, -1),
                place(-1, 2, 0), place(0, 2, 0), place(1, 2, 0),
                place(-1, 2, 1), place(0, 2, 1), place(1, 2, 1)
        )));

        register(new BlueprintTemplate("cabin", "??", List.of(
                place(-2, 0, -2), place(-1, 0, -2), place(0, 0, -2), place(1, 0, -2), place(2, 0, -2),
                place(-2, 1, -2), place(-2, 2, -2), place(2, 1, -2), place(2, 2, -2),
                place(-2, 0, -1), place(2, 0, -1), place(-2, 1, -1), place(2, 1, -1),
                place(-2, 0, 0), place(2, 0, 0), place(-2, 1, 0), place(2, 1, 0),
                place(-2, 0, 1), place(2, 0, 1), place(-2, 1, 1), place(2, 1, 1),
                place(-2, 0, 2), place(-1, 0, 2), place(0, 0, 2), place(1, 0, 2), place(2, 0, 2),
                place(-2, 1, 2), place(-2, 2, 2), place(-1, 2, 2), place(0, 2, 2), place(1, 2, 2), place(2, 2, 2),
                place(-2, 3, -2), place(-1, 3, -2), place(0, 3, -2), place(1, 3, -2), place(2, 3, -2),
                place(-2, 3, -1), place(-1, 3, -1), place(0, 3, -1), place(1, 3, -1), place(2, 3, -1),
                place(-2, 3, 0), place(-1, 3, 0), place(0, 3, 0), place(1, 3, 0), place(2, 3, 0),
                place(-2, 3, 1), place(-1, 3, 1), place(0, 3, 1), place(1, 3, 1), place(2, 3, 1),
                place(-2, 3, 2), place(-1, 3, 2), place(0, 3, 2), place(1, 3, 2), place(2, 3, 2)
        )));

        register(new BlueprintTemplate("watchtower", "??", List.of(
                place(-1, 0, -1), place(1, 0, -1), place(-1, 0, 1), place(1, 0, 1),
                place(-1, 1, -1), place(1, 1, -1), place(-1, 1, 1), place(1, 1, 1),
                place(-1, 2, -1), place(1, 2, -1), place(-1, 2, 1), place(1, 2, 1),
                place(-1, 3, -1), place(1, 3, -1), place(-1, 3, 1), place(1, 3, 1),
                place(-1, 4, -1), place(0, 4, -1), place(1, 4, -1),
                place(-1, 4, 0), place(0, 4, 0), place(1, 4, 0),
                place(-1, 4, 1), place(0, 4, 1), place(1, 4, 1),
                place(-1, 5, -1), place(1, 5, -1), place(-1, 5, 1), place(1, 5, 1)
        )));
    }

    private BlueprintRegistry() {
    }

    public static BlueprintTemplate get(String id) {
        if (id == null || id.isBlank()) {
            return TEMPLATES.get("shelter");
        }
        return TEMPLATES.getOrDefault(id, TEMPLATES.get("shelter"));
    }

    public static List<String> ids() {
        return List.copyOf(TEMPLATES.keySet());
    }

    private static void register(BlueprintTemplate template) {
        TEMPLATES.put(template.id(), template);
    }

    private static BlueprintTemplate.Placement place(int x, int y, int z) {
        return new BlueprintTemplate.Placement(x, y, z, Blocks.OAK_PLANKS);
    }
}

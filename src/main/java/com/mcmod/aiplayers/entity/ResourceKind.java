package com.mcmod.aiplayers.entity;

import java.util.Locale;

public enum ResourceKind {
    TREE("tree", "wood"),
    ORE("ore", "ore"),
    CROP("crop", "crop"),
    ANIMAL("animal", "animal");

    private final String id;
    private final String worldKind;

    ResourceKind(String id, String worldKind) {
        this.id = id;
        this.worldKind = worldKind;
    }

    public String id() {
        return this.id;
    }

    public String worldKind() {
        return this.worldKind;
    }

    public String displayName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static ResourceKind fromText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "tree", "trees", "wood", "log", "logs", "gather_wood", "collect_wood" -> TREE;
            case "ore", "ores", "stone", "mineral", "mine", "mining", "iron", "coal", "diamond", "gold" -> ORE;
            case "crop", "crops", "food", "farm", "harvest", "wheat", "potato", "carrot" -> CROP;
            case "animal", "animals", "hunt", "hunting", "mob", "passive_mob" -> ANIMAL;
            default -> null;
        };
    }
}

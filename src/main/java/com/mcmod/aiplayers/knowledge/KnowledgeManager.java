package com.mcmod.aiplayers.knowledge;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmod.aiplayers.AIPlayersMod;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class KnowledgeManager {
    private static final Gson GSON = new Gson();
    private static final String BLOCKS_RESOURCE = "ai/knowledge/blocks.json";
    private static final String TREES_RESOURCE = "ai/knowledge/trees.json";
    private static final String ORES_RESOURCE = "ai/knowledge/ores.json";
    private static final String TOOLS_RESOURCE = "ai/knowledge/tools.json";
    private static final String RECIPES_RESOURCE = "ai/knowledge/recipes.json";
    private static final String MOBS_RESOURCE = "ai/knowledge/mobs.json";
    private static final Path EXTERNAL_KNOWLEDGE_DIR = Path.of("config", "aiplayers", "knowledge");

    private static final Map<String, BlockKnowledge> BLOCKS = new HashMap<>();
    private static final Map<String, OreKnowledge> ORES = new HashMap<>();
    private static final Map<String, ToolKnowledge> TOOLS = new HashMap<>();
    private static final Map<String, RecipeKnowledge> RECIPES = new HashMap<>();
    private static final Map<String, MobKnowledge> MOBS = new HashMap<>();
    private static final Set<String> TREE_LOGS = new HashSet<>();
    private static final Set<String> TREE_LEAVES = new HashSet<>();

    private static boolean initialized;

    private KnowledgeManager() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        loadAll();
    }

    public static synchronized void reload() {
        loadAll();
    }

    private static void loadAll() {
        ensureExternalKnowledgeFiles();
        BLOCKS.clear();
        ORES.clear();
        TOOLS.clear();
        RECIPES.clear();
        MOBS.clear();
        TREE_LOGS.clear();
        TREE_LEAVES.clear();

        loadBlocks();
        loadTrees();
        loadOres();
        loadTools();
        loadRecipes();
        loadMobs();
        initialized = true;

        AIPlayersMod.LOGGER.info(
                "Knowledge loaded: blocks={}, ores={}, tools={}, recipes={}, mobs={}, treeLogs={}, treeLeaves={}",
                BLOCKS.size(),
                ORES.size(),
                TOOLS.size(),
                RECIPES.size(),
                MOBS.size(),
                TREE_LOGS.size(),
                TREE_LEAVES.size());
    }

    private static void ensureExternalKnowledgeFiles() {
        try {
            Files.createDirectories(EXTERNAL_KNOWLEDGE_DIR);
        } catch (Exception exception) {
            AIPlayersMod.LOGGER.warn("Failed to create external knowledge directory {}", EXTERNAL_KNOWLEDGE_DIR, exception);
            return;
        }
        ensureExternalKnowledgeFile(BLOCKS_RESOURCE);
        ensureExternalKnowledgeFile(TREES_RESOURCE);
        ensureExternalKnowledgeFile(ORES_RESOURCE);
        ensureExternalKnowledgeFile(TOOLS_RESOURCE);
        ensureExternalKnowledgeFile(RECIPES_RESOURCE);
        ensureExternalKnowledgeFile(MOBS_RESOURCE);
    }

    private static void ensureExternalKnowledgeFile(String resourcePath) {
        String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        Path targetPath = EXTERNAL_KNOWLEDGE_DIR.resolve(fileName);
        if (Files.exists(targetPath)) {
            return;
        }
        ClassLoader classLoader = KnowledgeManager.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return;
            }
            Files.copy(stream, targetPath);
            AIPlayersMod.LOGGER.info("Generated external knowledge template: {}", targetPath);
        } catch (Exception exception) {
            AIPlayersMod.LOGGER.warn("Failed to generate external knowledge file {}", targetPath, exception);
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static String getStatusSummary() {
        ensureInitialized();
        return "blocks=" + BLOCKS.size()
                + ", ores=" + ORES.size()
                + ", tools=" + TOOLS.size()
                + ", recipes=" + RECIPES.size()
                + ", mobs=" + MOBS.size()
                + ", treeLogs=" + TREE_LOGS.size()
                + ", treeLeaves=" + TREE_LEAVES.size();
    }

    public static BlockKnowledge getBlockKnowledge(BlockState state) {
        ensureInitialized();
        String id = getBlockId(state);
        if (id.isBlank()) {
            return null;
        }
        return BLOCKS.get(id);
    }

    public static boolean isTreeLog(BlockState state) {
        ensureInitialized();
        String id = getBlockId(state);
        if (id.isBlank()) {
            return false;
        }
        if (TREE_LOGS.contains(id)) {
            return true;
        }
        BlockKnowledge knowledge = BLOCKS.get(id);
        return knowledge != null && "tree_log".equalsIgnoreCase(knowledge.type());
    }

    public static boolean isTreeLeaves(BlockState state) {
        ensureInitialized();
        String id = getBlockId(state);
        if (id.isBlank()) {
            return false;
        }
        if (TREE_LEAVES.contains(id)) {
            return true;
        }
        BlockKnowledge knowledge = BLOCKS.get(id);
        return knowledge != null && "tree_leaves".equalsIgnoreCase(knowledge.type());
    }

    public static boolean isKnownOre(BlockState state) {
        ensureInitialized();
        String id = getBlockId(state);
        if (id.isBlank()) {
            return false;
        }
        if (ORES.containsKey(id)) {
            return true;
        }
        BlockKnowledge knowledge = BLOCKS.get(id);
        return knowledge != null && "ore".equalsIgnoreCase(knowledge.type());
    }

    public static boolean isKnownOre(BlockState state, int y) {
        ensureInitialized();
        String id = getBlockId(state);
        if (id.isBlank()) {
            return false;
        }
        OreKnowledge ore = ORES.get(id);
        if (ore != null) {
            if (ore.minY() != null && y < ore.minY()) {
                return false;
            }
            if (ore.maxY() != null && y > ore.maxY()) {
                return false;
            }
            return true;
        }
        BlockKnowledge block = BLOCKS.get(id);
        return block != null && "ore".equalsIgnoreCase(block.type());
    }

    public static String getRecommendedTool(BlockState state) {
        ensureInitialized();
        String id = getBlockId(state);
        if (id.isBlank()) {
            return "";
        }
        BlockKnowledge block = BLOCKS.get(id);
        if (block != null && block.tool() != null && !block.tool().isBlank()) {
            return block.tool();
        }
        OreKnowledge ore = ORES.get(id);
        if (ore != null && ore.tool() != null && !ore.tool().isBlank()) {
            return ore.tool();
        }
        return "";
    }

    public static Float getConfiguredHardness(BlockState state) {
        ensureInitialized();
        String id = getBlockId(state);
        if (id.isBlank()) {
            return null;
        }
        BlockKnowledge block = BLOCKS.get(id);
        return block == null ? null : block.hardness();
    }

    public static RecipeKnowledge getRecipe(String itemId) {
        ensureInitialized();
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        return RECIPES.get(itemId.toLowerCase(Locale.ROOT));
    }

    public static int getRecipeOutputCount(String itemId) {
        RecipeKnowledge recipe = getRecipe(itemId);
        if (recipe == null || recipe.outputCount() == null || recipe.outputCount() <= 0) {
            return 1;
        }
        return recipe.outputCount();
    }

    public static String getRecipeChain(String itemId, int maxDepth) {
        ensureInitialized();
        if (itemId == null || itemId.isBlank()) {
            return "";
        }
        return describeRecipeChain(itemId.toLowerCase(Locale.ROOT), Math.max(0, maxDepth), new LinkedHashSet<>());
    }

    public static String getCraftingHintSummary() {
        ensureInitialized();
        List<String> hints = new ArrayList<>();
        hints.add(getRecipeChain("minecraft:stone_pickaxe", 2));
        hints.add(getRecipeChain("minecraft:stone_axe", 2));
        hints.add(getRecipeChain("minecraft:bread", 1));
        return String.join(" | ", hints);
    }

    public static MobKnowledge getMobKnowledge(LivingEntity entity) {
        ensureInitialized();
        if (entity == null) {
            return null;
        }
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (key == null) {
            return null;
        }
        return MOBS.get(key.toString().toLowerCase(Locale.ROOT));
    }

    public static String getPreferredWeaponForMob(LivingEntity entity) {
        MobKnowledge knowledge = getMobKnowledge(entity);
        if (knowledge == null || knowledge.preferredWeapon() == null) {
            return "";
        }
        return knowledge.preferredWeapon();
    }

    public static String getMobWeaknessHint(LivingEntity entity) {
        MobKnowledge knowledge = getMobKnowledge(entity);
        if (knowledge == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (knowledge.weaknessTags() != null && !knowledge.weaknessTags().isEmpty()) {
            parts.add("weakness=" + String.join("/", knowledge.weaknessTags()));
        }
        if (knowledge.preferredWeapon() != null && !knowledge.preferredWeapon().isBlank()) {
            parts.add("weapon=" + knowledge.preferredWeapon());
        }
        if (knowledge.notes() != null && !knowledge.notes().isBlank()) {
            parts.add(knowledge.notes());
        }
        return String.join(", ", parts);
    }

    public static String getMobKnowledgeSummary() {
        ensureInitialized();
        List<String> entries = new ArrayList<>();
        addMobSummary(entries, "minecraft:zombie");
        addMobSummary(entries, "minecraft:skeleton");
        addMobSummary(entries, "minecraft:spider");
        addMobSummary(entries, "minecraft:creeper");
        addMobSummary(entries, "minecraft:enderman");
        return String.join(" | ", entries);
    }

    private static void addMobSummary(List<String> entries, String id) {
        MobKnowledge knowledge = MOBS.get(id);
        if (knowledge == null) {
            return;
        }
        String shortId = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
        String weakness = knowledge.weaknessTags() == null ? "" : String.join("/", knowledge.weaknessTags());
        entries.add(shortId + "->" + knowledge.preferredWeapon() + "(" + weakness + ")");
    }

    private static String describeRecipeChain(String itemId, int depth, Set<String> visiting) {
        RecipeKnowledge recipe = RECIPES.get(itemId);
        if (recipe == null || recipe.inputs() == null || recipe.inputs().isEmpty()) {
            return shortItemId(itemId);
        }
        if (!visiting.add(itemId)) {
            return shortItemId(itemId);
        }
        List<String> ingredientParts = new ArrayList<>();
        for (Map.Entry<String, Integer> ingredient : recipe.inputs().entrySet()) {
            ingredientParts.add(shortItemId(ingredient.getKey()) + "x" + ingredient.getValue());
        }
        String base = shortItemId(itemId) + "<=(" + String.join("+", ingredientParts) + ")";
        if (depth <= 0) {
            visiting.remove(itemId);
            return base;
        }

        List<String> subChains = new ArrayList<>();
        for (String ingredient : recipe.inputs().keySet()) {
            if (!RECIPES.containsKey(ingredient.toLowerCase(Locale.ROOT))) {
                continue;
            }
            subChains.add(describeRecipeChain(ingredient.toLowerCase(Locale.ROOT), depth - 1, visiting));
        }
        visiting.remove(itemId);
        if (subChains.isEmpty()) {
            return base;
        }
        return base + " -> [" + String.join("; ", subChains) + "]";
    }

    private static String shortItemId(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        int split = id.indexOf(':');
        return split >= 0 ? id.substring(split + 1) : id;
    }

    private static void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    private static String getBlockId(BlockState state) {
        if (state == null) {
            return "";
        }
        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key == null ? "" : key.toString().toLowerCase(Locale.ROOT);
    }

    private static JsonObject readObject(String resourcePath) {
        Path externalPath = EXTERNAL_KNOWLEDGE_DIR.resolve(resourcePath.substring(resourcePath.lastIndexOf('/') + 1));
        if (Files.isRegularFile(externalPath)) {
            try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(externalPath), StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
            } catch (Exception exception) {
                AIPlayersMod.LOGGER.warn("Failed to load external knowledge resource {}", externalPath, exception);
            }
        }

        ClassLoader classLoader = KnowledgeManager.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                AIPlayersMod.LOGGER.warn("Knowledge resource missing: {}", resourcePath);
                return new JsonObject();
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
            }
        } catch (Exception exception) {
            AIPlayersMod.LOGGER.warn("Failed to load knowledge resource {}", resourcePath, exception);
            return new JsonObject();
        }
    }

    private static void loadBlocks() {
        JsonObject root = readObject(BLOCKS_RESOURCE);
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            BlockKnowledge block = GSON.fromJson(entry.getValue(), BlockKnowledge.class);
            if (block == null) {
                continue;
            }
            BLOCKS.put(entry.getKey().toLowerCase(Locale.ROOT), block);
        }
    }

    private static void loadTrees() {
        JsonObject root = readObject(TREES_RESOURCE);
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            TreeKnowledge tree = GSON.fromJson(entry.getValue(), TreeKnowledge.class);
            if (tree == null) {
                continue;
            }
            if (tree.logs() != null) {
                for (String log : tree.logs()) {
                    if (log != null && !log.isBlank()) {
                        TREE_LOGS.add(log.toLowerCase(Locale.ROOT));
                    }
                }
            }
            if (tree.leaves() != null) {
                for (String leaves : tree.leaves()) {
                    if (leaves != null && !leaves.isBlank()) {
                        TREE_LEAVES.add(leaves.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
    }

    private static void loadOres() {
        JsonObject root = readObject(ORES_RESOURCE);
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            OreKnowledge ore = GSON.fromJson(entry.getValue(), OreKnowledge.class);
            if (ore == null) {
                continue;
            }
            ORES.put(entry.getKey().toLowerCase(Locale.ROOT), ore);
        }
    }

    private static void loadTools() {
        JsonObject root = readObject(TOOLS_RESOURCE);
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            ToolKnowledge tool = GSON.fromJson(entry.getValue(), ToolKnowledge.class);
            if (tool == null) {
                continue;
            }
            TOOLS.put(entry.getKey().toLowerCase(Locale.ROOT), tool);
        }
    }

    private static void loadRecipes() {
        JsonObject root = readObject(RECIPES_RESOURCE);
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            RecipeKnowledge recipe = GSON.fromJson(entry.getValue(), RecipeKnowledge.class);
            if (recipe == null) {
                continue;
            }
            Map<String, Integer> normalizedInputs = new HashMap<>();
            if (recipe.inputs() != null) {
                for (Map.Entry<String, Integer> input : recipe.inputs().entrySet()) {
                    if (input.getKey() == null || input.getKey().isBlank()) {
                        continue;
                    }
                    normalizedInputs.put(input.getKey().toLowerCase(Locale.ROOT), Math.max(1, input.getValue() == null ? 1 : input.getValue()));
                }
            }
            Set<String> normalizedRequires = new HashSet<>();
            if (recipe.requires() != null) {
                for (String requirement : recipe.requires()) {
                    if (requirement != null && !requirement.isBlank()) {
                        normalizedRequires.add(requirement.toLowerCase(Locale.ROOT));
                    }
                }
            }
            RECIPES.put(entry.getKey().toLowerCase(Locale.ROOT), new RecipeKnowledge(
                    recipe.outputCount() == null ? 1 : Math.max(1, recipe.outputCount()),
                    normalizedInputs,
                    recipe.category(),
                    normalizedRequires));
        }
    }

    private static void loadMobs() {
        JsonObject root = readObject(MOBS_RESOURCE);
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            MobKnowledge mob = GSON.fromJson(entry.getValue(), MobKnowledge.class);
            if (mob == null) {
                continue;
            }
            Set<String> weaknesses = new HashSet<>();
            if (mob.weaknessTags() != null) {
                for (String weakness : mob.weaknessTags()) {
                    if (weakness != null && !weakness.isBlank()) {
                        weaknesses.add(weakness.toLowerCase(Locale.ROOT));
                    }
                }
            }
            String preferredWeapon = mob.preferredWeapon() == null ? "" : mob.preferredWeapon().toLowerCase(Locale.ROOT);
            MOBS.put(entry.getKey().toLowerCase(Locale.ROOT), new MobKnowledge(
                    mob.family(),
                    preferredWeapon,
                    weaknesses,
                    mob.danger(),
                    mob.notes()));
        }
    }

    public record BlockKnowledge(String type, Float hardness, String tool, Float breakTime) {
    }

    public record TreeKnowledge(Set<String> logs, Set<String> leaves) {
    }

    public record OreKnowledge(Integer minY, Integer maxY, String tool) {
    }

    public record ToolKnowledge(Integer miningLevel, Float speed) {
    }

    public record RecipeKnowledge(Integer outputCount, Map<String, Integer> inputs, String category, Set<String> requires) {
    }

    public record MobKnowledge(String family, String preferredWeapon, Set<String> weaknessTags, Integer danger, String notes) {
    }
}

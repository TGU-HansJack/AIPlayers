package com.mcmod.aiplayers.entity;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

public final class AnimalTargetHelper {
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();
    private static final String[] KNOWN_TARGETS = {
            "cow", "pig", "sheep", "chicken", "rabbit", "horse", "donkey", "mule", "llama", "goat",
            "bee", "turtle", "fox", "wolf", "cat", "ocelot", "panda", "camel", "frog", "axolotl",
            "squid", "glow_squid", "salmon", "cod", "tropical_fish", "pufferfish", "dolphin",
            "strider", "hoglin", "mooshroom", "sniffer"
    };

    static {
        register("cow", "牛", "cow", "cattle");
        register("pig", "猪", "pig");
        register("sheep", "羊", "绵羊", "sheep");
        register("chicken", "鸡", "chicken");
        register("rabbit", "兔子", "兔", "rabbit");
        register("horse", "马", "horse");
        register("donkey", "驴", "donkey");
        register("mule", "驽马", "mule");
        register("llama", "羊驼", "llama");
        register("goat", "山羊", "goat");
        register("bee", "蜜蜂", "bee");
        register("turtle", "海龟", "turtle");
        register("fox", "狐狸", "fox");
        register("wolf", "狼", "wolf");
        register("cat", "猫", "cat");
        register("ocelot", "豹猫", "ocelot");
        register("panda", "熊猫", "panda");
        register("camel", "骱驼", "camel");
        register("frog", "青蛙", "frog");
        register("axolotl", "美西蟈螵", "axolotl");
        register("squid", "鱿鱼", "squid");
        register("glow_squid", "发光鱿鱼", "glow squid", "glow_squid");
        register("salmon", "鲑鱼", "salmon");
        register("cod", "鳕鱼", "cod");
        register("tropical_fish", "热带鱼", "tropical fish", "tropical_fish");
        register("pufferfish", "河豚", "pufferfish");
        register("dolphin", "海豚", "dolphin");
        register("strider", "炽足兽", "strider");
        register("hoglin", "疟灵", "hoglin");
        register("mooshroom", "哞菇牛", "mooshroom");
        register("sniffer", "嗅探兽", "sniffer");
    }

    private AnimalTargetHelper() {
    }

    public static HuntTarget parseHuntDirective(String content) {
        if (!mentionsAttackIntent(content)) {
            return null;
        }
        String targetId = resolveTargetId(content);
        if (targetId == null) {
            return null;
        }
        return new HuntTarget(targetId, displayName(targetId));
    }

    public static boolean mentionsAttackIntent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String normalized = normalize(content);
        return normalized.contains("攻击")
                || normalized.contains("击杀")
                || normalized.contains("狩猎")
                || normalized.contains("猎杀")
                || normalized.contains("杀")
                || normalized.contains("打")
                || normalized.contains("hunt")
                || normalized.contains("attack")
                || normalized.contains("kill")
                || normalized.contains("slay");
    }

    public static String supportedAnimalsSummary() {
        return "牛 / 猪 / 羊 / 鸡 / 兔子 / 马 / 驴 / 羊驼 / 山羊 / 蜜蜂 / 海龟 / 狐狸 / 狼 / 猫 / 熊猫 / 骱驼 / 青蛙 / 美西蟈螵 / squid / dolphin / strider / hoglin / mooshroom / sniffer";
    }

    public static boolean matchesRequestedAnimal(LivingEntity entity, String targetId) {
        if (entity == null || !entity.isAlive() || targetId == null || targetId.isBlank()) {
            return false;
        }
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null && targetId.equals(key.getPath());
    }

    public static String displayName(String targetId) {
        return switch (targetId) {
            case "cow" -> "牛";
            case "pig" -> "猪";
            case "sheep" -> "羊";
            case "chicken" -> "鸡";
            case "rabbit" -> "兔子";
            case "horse" -> "马";
            case "donkey" -> "驴";
            case "mule" -> "驽马";
            case "llama" -> "羊驼";
            case "goat" -> "山羊";
            case "bee" -> "蜜蜂";
            case "turtle" -> "海龟";
            case "fox" -> "狐狸";
            case "wolf" -> "狼";
            case "cat" -> "猫";
            case "ocelot" -> "豹猫";
            case "panda" -> "熊猫";
            case "camel" -> "骱驼";
            case "frog" -> "青蛙";
            case "axolotl" -> "美西蟈螵";
            case "squid" -> "squid";
            case "glow_squid" -> "glow squid";
            case "salmon" -> "salmon";
            case "cod" -> "cod";
            case "tropical_fish" -> "tropical fish";
            case "pufferfish" -> "pufferfish";
            case "dolphin" -> "dolphin";
            case "strider" -> "strider";
            case "hoglin" -> "hoglin";
            case "mooshroom" -> "mooshroom";
            case "sniffer" -> "sniffer";
            default -> targetId;
        };
    }

    private static void register(String targetId, String... aliases) {
        for (String alias : aliases) {
            if (alias != null && !alias.isBlank()) {
                ALIASES.put(normalize(alias), targetId);
            }
        }
    }

    private static String resolveTargetId(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String normalized = normalize(content);
        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        for (String known : KNOWN_TARGETS) {
            if (normalized.contains(known.replace('_', ' ')) || normalized.contains(known)) {
                return known;
            }
        }
        return null;
    }

    private static String normalize(String content) {
        return content.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replace('、', ' ')
                .replace('，', ' ')
                .replace('。', ' ')
                .replace('！', ' ')
                .replace('？', ' ')
                .replace(':', ' ')
                .replace(';', ' ')
                .replaceAll("\s+", " ")
                .trim();
    }

    public record HuntTarget(String targetId, String label) {
    }
}

package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.knowledge.KnowledgeManager;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.entity.LivingEntity;

public final class AnimalTargetHelper {
    private AnimalTargetHelper() {
    }

    public static HuntTarget parseHuntDirective(String content) {
        if (!mentionsAttackIntent(content)) {
            return null;
        }
        return resolveHuntTarget(content);
    }

    public static HuntTarget resolveHuntTarget(String content) {
        String targetId = KnowledgeManager.resolveHuntTargetId(content);
        if (targetId == null || targetId.isBlank()) {
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
        return KnowledgeManager.getSupportedHuntAnimalsSummary();
    }

    public static List<String> huntCommandSuggestions() {
        return KnowledgeManager.getHuntableMobTargets();
    }

    public static boolean matchesRequestedAnimal(LivingEntity entity, String targetId) {
        return KnowledgeManager.matchesRequestedMob(entity, targetId);
    }

    public static String displayName(String targetId) {
        return KnowledgeManager.getMobDisplayName(targetId);
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
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record HuntTarget(String targetId, String label) {
    }
}

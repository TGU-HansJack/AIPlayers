package com.mcmod.aiplayers.entity;

import java.util.List;
import java.util.Map;

final class ResourceActionCompiler {
    private ResourceActionCompiler() {
    }

    static BehaviorNodeSpec compileGather(ResourceKind kind, int amount, String label) {
        ResourceKind resolvedKind = kind == null ? ResourceKind.TREE : kind;
        int resolvedAmount = Math.max(1, amount);
        return BehaviorNodeSpec.sequence(
                label == null || label.isBlank() ? "Gather" + resolvedKind.name() : label,
                List.of(BehaviorNodeSpec.action(
                        "统一资源采集",
                        "gather_resource",
                        Map.of("kind", resolvedKind.id(), "amount", Integer.toString(resolvedAmount), "target", resolvedKind.worldKind()))));
    }
}

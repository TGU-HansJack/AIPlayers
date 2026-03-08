package com.mcmod.aiplayers.entity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ConstraintSet(List<String> values, Map<String, String> metadata) {
    public ConstraintSet {
        values = values == null ? List.of() : List.copyOf(values);
        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
    }

    public static ConstraintSet empty() {
        return new ConstraintSet(List.of(), Map.of());
    }

    public boolean contains(String value) {
        return value != null && this.values.stream().anyMatch(candidate -> value.equalsIgnoreCase(candidate));
    }
}

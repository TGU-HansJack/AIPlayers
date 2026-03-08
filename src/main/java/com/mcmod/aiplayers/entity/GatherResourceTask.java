package com.mcmod.aiplayers.entity;

public record GatherResourceTask(
        ResourceKind kind,
        int amount,
        String targetHint,
        String targetId,
        boolean pinned,
        String source) {
    public GatherResourceTask {
        kind = kind == null ? ResourceKind.TREE : kind;
        amount = Math.max(1, amount);
        targetHint = targetHint == null ? "" : targetHint;
        targetId = targetId == null ? "" : targetId;
        source = source == null || source.isBlank() ? "brain" : source;
    }

    public String key() {
        return this.kind.name() + "|" + this.amount + "|" + this.targetHint + "|" + this.targetId + "|" + this.pinned;
    }
}

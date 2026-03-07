package com.mcmod.aiplayers.entity;

public enum GoapActionType {
    MOVE_TO_TARGET("移动到目标"),
    ACQUIRE_TOOL("获取工具"),
    CHOP_TREE("砍树"),
    MINE_ORE("采矿"),
    COLLECT_DROP("回收掉落物"),
    HARVEST_CROP("收割作物"),
    CRAFT_PLANKS("合成木板"),
    CRAFT_BREAD("合成面包"),
    CRAFT_TORCH("制作火把"),
    BUILD_SHELTER("建造避难所"),
    DELIVER_ITEM("交付物品"),
    RETREAT_TO_SAFE_GROUND("撤离到安全地面"),
    REST_AT_BED("前往床位休整"),
    OBSERVE_AND_REPORT("观察并汇报");

    private final String displayName;

    GoapActionType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}

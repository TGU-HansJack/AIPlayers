package com.mcmod.aiplayers.entity;

public enum GoapFact {
    OWNER_AVAILABLE("主人在线"),
    OWNER_NEAR("已在主人身边"),
    OWNER_SAFE("主人暂时安全"),
    SAFE("已脱离危险"),
    HEALTHY("状态健康"),
    HAS_TOOLS("拥有基础工具"),
    HAS_WOOD_TARGET("知道木材位置"),
    HAS_ORE_TARGET("知道矿点位置"),
    HAS_CROP_TARGET("知道作物位置"),
    HAS_BED_TARGET("知道床位位置"),
    HAS_BUILDING_MATERIALS("建材充足"),
    HAS_LOGS("拥有原木"),
    HAS_PLANKS("拥有木板"),
    HAS_FOOD_SUPPLY("食物充足"),
    HAS_BACKPACK_SPACE("背包有空位"),
    HAS_ORE_SUPPLY("已有矿物收获"),
    DELIVERY_PENDING("存在交付请求"),
    DELIVERY_COMPLETE("交付已完成"),
    SHELTER_READY("避难所已完成"),
    OBSERVATION_REFRESHED("观察已更新"),
    HAS_TORCH_SUPPLY("照明充足");

    private final String displayName;

    GoapFact(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}

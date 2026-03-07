package com.mcmod.aiplayers.entity;

public enum GoapFact {
    OWNER_AVAILABLE("\u4e3b\u4eba\u5728\u7ebf"),
    OWNER_NEAR("\u5df2\u5728\u4e3b\u4eba\u8eab\u8fb9"),
    OWNER_SAFE("\u4e3b\u4eba\u6682\u65f6\u5b89\u5168"),
    SAFE("\u5df2\u8131\u79bb\u5371\u9669"),
    HEALTHY("\u72b6\u6001\u5065\u5eb7"),
    HAS_TOOLS("\u62e5\u6709\u57fa\u7840\u5de5\u5177"),
    HAS_WOOD_TARGET("\u77e5\u9053\u6728\u6750\u4f4d\u7f6e"),
    HAS_ORE_TARGET("\u77e5\u9053\u77ff\u70b9\u4f4d\u7f6e"),
    HAS_CROP_TARGET("\u77e5\u9053\u4f5c\u7269\u4f4d\u7f6e"),
    HAS_BED_TARGET("\u77e5\u9053\u5e8a\u4f4d\u4f4d\u7f6e"),
    HAS_BUILDING_MATERIALS("\u5efa\u6750\u5145\u8db3"),
    HAS_LOGS("\u62e5\u6709\u539f\u6728"),
    HAS_PLANKS("\u62e5\u6709\u6728\u677f"),
    HAS_FOOD_SUPPLY("\u98df\u7269\u5145\u8db3"),
    HAS_BACKPACK_SPACE("\u80cc\u5305\u6709\u7a7a\u4f4d"),
    HAS_ORE_SUPPLY("\u5df2\u6709\u77ff\u7269\u6536\u83b7"),
    DELIVERY_PENDING("\u5b58\u5728\u4ea4\u4ed8\u8bf7\u6c42"),
    DELIVERY_COMPLETE("\u4ea4\u4ed8\u5df2\u5b8c\u6210"),
    SHELTER_READY("\u907f\u96be\u6240\u5df2\u5b8c\u6210"),
    OBSERVATION_REFRESHED("\u89c2\u5bdf\u5df2\u66f4\u65b0"),
    HAS_TORCH_SUPPLY("\u7167\u660e\u5145\u8db3");

    private final String displayName;

    GoapFact(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}

package com.mcmod.aiplayers.entity;

public final class GoalSelector {
    private GoalSelector() {
    }

    public static AgentGoal select(AIPlayerEntity entity, WorldStateSnapshot state, AgentMemory memory) {
        if (state.pendingDelivery()) {
            return AgentGoal.of(GoalType.DELIVER_ITEM, "local", "存在待交付请求，优先响应主人直接命令");
        }
        if (state.inHazard() || state.onFire()) {
            return AgentGoal.of(GoalType.RECOVER_SELF, "local", "检测到液体、着火或危险环境，生存优先");
        }
        if (state.ownerUnderThreat()) {
            return AgentGoal.of(GoalType.GUARD_OWNER, "local", "主人附近存在威胁，需要优先护卫");
        }
        if (state.hostileNearby() && state.lowHealth()) {
            return AgentGoal.of(GoalType.RECOVER_SELF, "local", "低血量且附近有敌对威胁，先自保");
        }
        if (state.lowFood() && state.cropKnown()) {
            return AgentGoal.of(GoalType.COLLECT_FOOD, "local", "食物不足，附近已有成熟作物");
        }
        if (state.night() && state.buildingUnits() >= 12) {
            return AgentGoal.of(GoalType.BUILD_SHELTER, "local", "夜晚且建材充足，优先搭建避难所");
        }
        if ((state.lowTools() || state.buildingUnits() < 12) && state.woodKnown()) {
            return AgentGoal.of(GoalType.COLLECT_WOOD, "local", "工具或建材不足，优先补木头");
        }
        if (state.oreKnown() && state.freeBackpackSlots() > 2) {
            return AgentGoal.of(GoalType.COLLECT_ORE, "local", "有可达矿点且背包仍有空间");
        }
        if (state.ownerAvailable() && state.ownerDistance() > 7.0D) {
            return AgentGoal.of(GoalType.FOLLOW_OWNER, "local", "与主人距离偏远，先回到队形");
        }
        if (!state.ownerAvailable()) {
            return AgentGoal.of(GoalType.SURVIVE, "local", "无主人在线，保持自主生存");
        }
        return AgentGoal.of(GoalType.SURVIVE, "local", "默认保持自主生存并兼顾附近环境");
    }
}

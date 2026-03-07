package com.mcmod.aiplayers.system;

import com.mcmod.aiplayers.entity.AIPlayerEntity;
import com.mcmod.aiplayers.entity.AIPlayerMode;
import java.util.ArrayList;
import java.util.List;

public final class AIAgentPipeline {
    private AIAgentPipeline() {
    }

    public static AIAgentPlan evaluate(AIPlayerEntity entity) {
        AIAgentWorldState state = entity.buildAgentWorldState();
        AIAgentMemorySnapshot memory = new AIAgentMemorySnapshot(
                entity.getMemorySummary(),
                entity.getLongTermMemorySummary(),
                AILongTermMemoryStore.getRecentEvents(entity, 4));
        return plan(state, memory);
    }

    private static AIAgentPlan plan(AIAgentWorldState state, AIAgentMemorySnapshot memory) {
        List<String> steps = new ArrayList<>();
        AIPlayerMode recommendedMode = state.mode();
        String goal;
        String reasoning;

        if (state.pendingDelivery()) {
            goal = "把背包物资交付给主人";
            steps.add("靠近玩家");
            steps.add("检查背包数量");
            steps.add("转移物资或丢到脚边");
            reasoning = "当前存在未完成交付请求，应优先完成玩家直接指令";
            return new AIAgentPlan(goal, recommendedMode, steps, reasoning, "local-pipeline");
        }

        if (state.inHazard()) {
            goal = "先脱离危险环境";
            steps.add("寻找干燥落脚点");
            steps.add("上浮或跳跃脱困");
            reasoning = "环境感知到液体或高风险状态，生存优先级最高";
            return new AIAgentPlan(goal, recommendedMode, steps, reasoning, "local-pipeline");
        }

        if (state.hostileNearby() && state.lowHealth()) {
            goal = "低血量自保并回到安全位置";
            recommendedMode = state.ownerAvailable() ? AIPlayerMode.GUARD : AIPlayerMode.SURVIVE;
            steps.add("拉开距离");
            steps.add("贴近主人或安全区域");
            steps.add("恢复后再评估");
            reasoning = "敌对生物靠近且生命值偏低，需要先保证存活";
            return new AIAgentPlan(goal, recommendedMode, steps, reasoning, "local-pipeline");
        }

        if (state.hostileNearby()) {
            goal = "处理附近敌对威胁";
            recommendedMode = state.ownerAvailable() ? AIPlayerMode.GUARD : AIPlayerMode.SURVIVE;
            steps.add("锁定敌人");
            steps.add("靠近并攻击");
            steps.add("战后恢复巡逻");
            reasoning = "危险识别命中敌对实体，应优先清理战斗目标";
            return new AIAgentPlan(goal, recommendedMode, steps, reasoning, "local-pipeline");
        }

        switch (state.mode()) {
            case GATHER_WOOD -> {
                goal = state.hasWoodTarget() ? "采集木材并继续向上处理树干" : "搜索最近可采木材";
                steps.add(state.hasWoodTarget() ? "前往树木" : "短距离探索");
                steps.add("识别遮挡树叶");
                steps.add("连锁采集原木");
                reasoning = "当前模式是砍树，应围绕木材目标持续推进";
                return new AIAgentPlan(goal, AIPlayerMode.GATHER_WOOD, steps, reasoning, "local-pipeline");
            }
            case MINE -> {
                goal = state.hasOreTarget() ? "推进到矿点并处理相邻矿脉" : "搜索最近矿点";
                steps.add(state.hasOreTarget() ? "靠近矿点" : "探索地形");
                steps.add("必要时清理浅层遮挡方块");
                steps.add("继续处理相邻矿脉");
                reasoning = "当前模式是挖矿，应围绕矿点继续规划推进";
                return new AIAgentPlan(goal, AIPlayerMode.MINE, steps, reasoning, "local-pipeline");
            }
            case BUILD_SHELTER -> {
                goal = state.buildingUnits() < 12 ? "补足建材后继续建造" : "前往锚点继续放置蓝图方块";
                steps.add(state.buildingUnits() < 12 ? "切换到砍树补材料" : "回到蓝图位置");
                steps.add("按蓝图放置方块");
                reasoning = "当前模式是建造，优先保证建材和锚点连续施工";
                return new AIAgentPlan(goal, state.buildingUnits() < 12 ? AIPlayerMode.GATHER_WOOD : AIPlayerMode.BUILD_SHELTER, steps, reasoning, "local-pipeline");
            }
            case SURVIVE -> {
                if (state.night() && state.buildingUnits() >= 12) {
                    goal = "夜间优先建造避难所";
                    recommendedMode = AIPlayerMode.BUILD_SHELTER;
                    steps.add("回到建造锚点");
                    steps.add("继续封墙和封顶");
                    reasoning = "夜晚且材料充足，适合优先构建安全掩体";
                } else if (state.buildingUnits() < 12 && state.hasWoodTarget()) {
                    goal = "先收集木材补足长期生存物资";
                    recommendedMode = AIPlayerMode.GATHER_WOOD;
                    steps.add("前往树木");
                    steps.add("采集并转化木板");
                    reasoning = "建材不足会阻塞后续建造与工具补给";
                } else if (state.hasOreTarget() && state.freeBackpackSlots() > 2) {
                    goal = "补充矿物与工具资源";
                    recommendedMode = AIPlayerMode.MINE;
                    steps.add("前往矿点");
                    steps.add("清理遮挡方块");
                    steps.add("采完后继续巡查");
                    reasoning = "矿点存在且背包还有空间，可以扩充资源储备";
                } else {
                    goal = state.ownerAvailable() ? "围绕主人巡查与待命" : "探索并寻找新资源";
                    recommendedMode = state.ownerAvailable() ? AIPlayerMode.GUARD : AIPlayerMode.EXPLORE;
                    steps.add(state.ownerAvailable() ? "靠近主人" : "探索附近区域");
                    steps.add("持续环境感知");
                    reasoning = "当前没有更高优先级任务，进入稳态巡查循环";
                }
                return new AIAgentPlan(goal, recommendedMode, steps, reasoning, "local-pipeline");
            }
            case IDLE, FOLLOW, GUARD, EXPLORE -> {
                if (state.ownerAvailable() && state.ownerDistance() > 8.0D) {
                    goal = "快速补位到主人附近";
                    recommendedMode = state.mode() == AIPlayerMode.GUARD ? AIPlayerMode.GUARD : AIPlayerMode.FOLLOW;
                    steps.add("加速跟随");
                    steps.add("必要时快速传送");
                    reasoning = "与主人距离偏大，先恢复队形再继续其他任务";
                } else if (state.hasOreTarget() && state.mode() == AIPlayerMode.EXPLORE) {
                    goal = "探索中发现矿点，转入挖矿";
                    recommendedMode = AIPlayerMode.MINE;
                    steps.add("靠近矿点");
                    steps.add("开始采掘");
                    reasoning = "探索阶段发现资源，应转化为实际采集收益";
                } else if (state.hasWoodTarget() && state.mode() == AIPlayerMode.EXPLORE) {
                    goal = "探索中发现树木，转入采木";
                    recommendedMode = AIPlayerMode.GATHER_WOOD;
                    steps.add("前往树木");
                    steps.add("处理树叶遮挡");
                    reasoning = "探索阶段发现木材，可快速补充基础建材";
                } else {
                    goal = state.mode() == AIPlayerMode.EXPLORE ? "继续探索并更新世界状态" : "保持当前位置并持续观察";
                    steps.add(state.mode() == AIPlayerMode.EXPLORE ? "随机探索可到达位置" : "看向主人或周围环境");
                    steps.add("更新短期记忆");
                    reasoning = "当前无更高优先级事件，维持基础巡逻与观察";
                }
                return new AIAgentPlan(goal, recommendedMode, steps, reasoning + "；记忆=" + memory.longTermSummary(), "local-pipeline");
            }
        }

        return new AIAgentPlan(
                "保持当前任务",
                state.mode(),
                List.of("继续执行当前模式逻辑"),
                "未命中更高优先级规则，沿用当前策略",
                "local-pipeline");
    }
}

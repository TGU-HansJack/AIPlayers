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
            return localPlan(goal, recommendedMode, steps, reasoning, state, memory);
        }

        if (state.inHazard()) {
            goal = "先脱离危险环境";
            steps.add("寻找干燥落脚点");
            steps.add("上浮或跳跃脱困");
            reasoning = state.onFire() ? "着火或掉入危险液体时必须立刻自救" : "环境感知到液体或高风险状态，生存优先级最高";
            return localPlan(goal, recommendedMode, steps, reasoning, state, memory);
        }

        if (state.ownerUnderThreat()) {
            goal = "优先保护主人并压制威胁";
            recommendedMode = AIPlayerMode.GUARD;
            steps.add("快速靠近主人");
            steps.add("锁定主人附近敌人");
            steps.add("清场后恢复队形");
            reasoning = "感知到主人附近存在战斗威胁，护卫优先级高于一般采集";
            return localPlan(goal, recommendedMode, steps, reasoning, state, memory);
        }

        if (state.hostileNearby() && state.lowHealth()) {
            goal = "低血量自保并回到安全位置";
            recommendedMode = state.ownerAvailable() ? AIPlayerMode.GUARD : AIPlayerMode.SURVIVE;
            steps.add("拉开距离");
            steps.add("贴近主人或安全区域");
            steps.add("恢复后再评估");
            reasoning = "敌对生物靠近且生命值偏低，需要先保证存活";
            return localPlan(goal, recommendedMode, steps, reasoning, state, memory);
        }

        if (state.hostileNearby()) {
            goal = "处理附近敌对威胁";
            recommendedMode = state.ownerAvailable() ? AIPlayerMode.GUARD : AIPlayerMode.SURVIVE;
            steps.add("锁定敌人");
            steps.add("靠近并攻击");
            steps.add("战后恢复巡逻");
            reasoning = "危险识别命中敌对实体，应优先清理战斗目标";
            return localPlan(goal, recommendedMode, steps, reasoning, state, memory);
        }

        if (state.navigationStuck() && state.taskFailureStreak() >= 2) {
            goal = state.ownerAvailable() ? "当前路径连续失败，先回到主人附近重置路线" : "当前路径连续失败，先探索新的可达路线";
            recommendedMode = state.ownerAvailable() ? AIPlayerMode.FOLLOW : AIPlayerMode.EXPLORE;
            steps.add(state.ownerAvailable() ? "回到主人附近" : "放弃旧路径目标");
            steps.add("重新观察环境");
            steps.add(state.ownerAvailable() ? "等待下一次重规划" : "寻找新的资源入口");
            reasoning = "导航持续卡住且已有失败累计，需要先跳出当前局部最优";
            return localPlan(goal, recommendedMode, steps, reasoning, state, memory);
        }

        if (state.progressStalled() && state.taskFailureStreak() >= 2) {
            goal = state.ownerAvailable() ? "当前任务推进停滞，先回到主人附近同步上下文" : "当前任务推进停滞，先探索新区域寻找替代目标";
            recommendedMode = state.ownerAvailable() ? AIPlayerMode.FOLLOW : AIPlayerMode.EXPLORE;
            steps.add(state.ownerAvailable() ? "贴近主人" : "离开当前受阻区域");
            steps.add("刷新环境感知");
            steps.add("等待本地或任务 AI 重规划");
            reasoning = "最近任务连续失败且长时间没有实质进展，继续重复同一动作收益很低";
            return localPlan(goal, recommendedMode, steps, reasoning, state, memory);
        }

        if (state.nearDroppedItems() && state.freeBackpackSlots() > 0 && !state.progressStalled()) {
            goal = "回收附近可利用掉落物";
            steps.add("靠近掉落物");
            steps.add("拾取并整理进背包");
            reasoning = "附近存在现成资源，回收成本低且能提升生存效率";
            return localPlan(goal, recommendedMode, steps, reasoning, state, memory);
        }

        switch (state.mode()) {
            case GATHER_WOOD -> {
                if (state.progressStalled()) {
                    goal = "当前采木停滞，放弃旧树并搜索新的可达木材";
                    steps.add("清空旧树目标");
                    steps.add("搜索新的可达树木");
                    steps.add("重新开始采集");
                    reasoning = "当前采木任务没有实质进展，应主动放弃受阻目标避免空转";
                    return localPlan(goal, AIPlayerMode.EXPLORE, steps, reasoning, state, memory);
                }

                goal = state.hasWoodTarget() ? "采集木材并继续向上处理树干" : "搜索最近可采木材";
                steps.add(state.hasWoodTarget() ? "前往树木" : "短距离探索");
                steps.add("识别遮挡树叶");
                steps.add("连锁采集原木");
                reasoning = "当前模式是砍树，应围绕木材目标持续推进";
                return localPlan(goal, AIPlayerMode.GATHER_WOOD, steps, reasoning, state, memory);
            }
            case MINE -> {
                if (state.progressStalled()) {
                    goal = "当前挖矿停滞，放弃旧矿点并搜索新的可达矿脉";
                    steps.add("清空旧矿点目标");
                    steps.add("搜索新的可达矿点");
                    steps.add("重新开始采掘");
                    reasoning = "矿点受阻或不可达时，不应持续在原地重复尝试";
                    return localPlan(goal, AIPlayerMode.EXPLORE, steps, reasoning, state, memory);
                }

                goal = state.hasOreTarget() ? "推进到矿点并处理相邻矿脉" : "搜索最近矿点";
                steps.add(state.hasOreTarget() ? "靠近矿点" : "探索地形");
                steps.add("必要时清理浅层遮挡方块");
                steps.add("继续处理相邻矿脉");
                reasoning = "当前模式是挖矿，应围绕矿点继续规划推进";
                return localPlan(goal, AIPlayerMode.MINE, steps, reasoning, state, memory);
            }
            case BUILD_SHELTER -> {
                if (state.buildingUnits() < 12) {
                    goal = "补足建材后继续建造";
                    recommendedMode = AIPlayerMode.GATHER_WOOD;
                    steps.add("切换到砍树补材料");
                    steps.add("转化成木板");
                    steps.add("回到蓝图位置");
                    reasoning = "当前模式是建造，但建材不足会直接阻塞施工";
                    return localPlan(goal, recommendedMode, steps, reasoning, state, memory);
                }

                if (state.progressStalled()) {
                    goal = "建造施工停滞，先重找可达施工位";
                    steps.add("回到锚点附近");
                    steps.add("寻找新的施工入口");
                    steps.add("继续放置蓝图方块");
                    reasoning = "施工位置连续受阻时，应先恢复可达性再继续建造";
                    return localPlan(goal, AIPlayerMode.BUILD_SHELTER, steps, reasoning, state, memory);
                }

                goal = "前往锚点继续放置蓝图方块";
                steps.add("回到蓝图位置");
                steps.add("按蓝图放置方块");
                reasoning = "当前模式是建造，优先保证锚点附近连续施工";
                return localPlan(goal, AIPlayerMode.BUILD_SHELTER, steps, reasoning, state, memory);
            }
            case SURVIVE -> {
                if (state.lowFoodSupply() && state.foodSourceKnown()) {
                    goal = "优先补充食物储备";
                    recommendedMode = AIPlayerMode.SURVIVE;
                    steps.add("前往成熟作物区域");
                    steps.add("收割并补种");
                    steps.add("必要时制作面包");
                    reasoning = "食物储备偏低时，应先保证长期生存能力";
                } else if (state.night() && state.bedKnown()) {
                    goal = "夜间回到已知安全休整点";
                    recommendedMode = AIPlayerMode.SURVIVE;
                    steps.add("前往床位或休整点");
                    steps.add("保持警戒并恢复状态");
                    reasoning = "夜晚存在更高环境风险，已知安全点优先级高于盲目探索";
                } else if (state.night() && state.buildingUnits() >= 12) {
                    goal = "夜间优先建造避难所";
                    recommendedMode = AIPlayerMode.BUILD_SHELTER;
                    steps.add("回到建造锚点");
                    steps.add("继续封墙和封顶");
                    reasoning = "夜晚且材料充足，适合优先构建安全掩体";
                } else if (state.lowTools() && (state.hasWoodTarget() || state.hasOreTarget())) {
                    goal = "补足基础工具链";
                    recommendedMode = state.hasWoodTarget() ? AIPlayerMode.GATHER_WOOD : AIPlayerMode.MINE;
                    steps.add(state.hasWoodTarget() ? "采集木材" : "收集石料或矿物");
                    steps.add("回到工作站附近整理材料");
                    steps.add("补做基础工具");
                    reasoning = "缺少工具会阻塞采集、建造和战斗，应优先补链";
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
                } else if (state.progressStalled()) {
                    goal = state.ownerAvailable() ? "生存循环停滞，先回到主人附近获取上下文" : "生存循环停滞，先探索附近刷新环境认知";
                    recommendedMode = state.ownerAvailable() ? AIPlayerMode.FOLLOW : AIPlayerMode.EXPLORE;
                    steps.add(state.ownerAvailable() ? "回到主人附近" : "探索新的可达位置");
                    steps.add("刷新周边资源与威胁感知");
                    reasoning = "当前生存流程缺少进展，需要先更新环境上下文";
                } else {
                    goal = state.ownerAvailable() ? "围绕主人巡查与待命" : "探索并寻找新资源";
                    recommendedMode = state.ownerAvailable() ? AIPlayerMode.GUARD : AIPlayerMode.EXPLORE;
                    steps.add(state.ownerAvailable() ? "靠近主人" : "探索附近区域");
                    steps.add("持续环境感知");
                    reasoning = "当前没有更高优先级任务，进入稳态巡查循环";
                }
                return localPlan(goal, recommendedMode, steps, reasoning, state, memory);
            }
            case IDLE, FOLLOW, GUARD, EXPLORE -> {
                if (state.mode() == AIPlayerMode.IDLE && !state.ownerAvailable()) {
                    goal = "无主人指令时转入自主生存";
                    recommendedMode = AIPlayerMode.SURVIVE;
                    steps.add("开始环境巡查");
                    steps.add("收集生存所需基础资源");
                    reasoning = "长时间待命且无主人约束时，应进入自主活动而非原地停滞";
                } else if (state.ownerAvailable() && state.ownerDistance() > 8.0D) {
                    goal = "快速补位到主人附近";
                    recommendedMode = state.mode() == AIPlayerMode.GUARD ? AIPlayerMode.GUARD : AIPlayerMode.FOLLOW;
                    steps.add("加速跟随");
                    steps.add("必要时快速传送");
                    reasoning = "与主人距离偏大，先恢复队形再继续其他任务";
                } else if (state.progressStalled()) {
                    goal = state.ownerAvailable() ? "当前巡逻停滞，回到主人附近同步上下文" : "当前探索停滞，切换到新区域继续探索";
                    recommendedMode = state.ownerAvailable() ? AIPlayerMode.FOLLOW : AIPlayerMode.EXPLORE;
                    steps.add(state.ownerAvailable() ? "靠近主人" : "探索新的可达位置");
                    steps.add("刷新观察与记忆");
                    reasoning = "当前模式长时间没有推进，应该主动改变上下文而非持续空转";
                } else if (state.nearDroppedItems() && state.freeBackpackSlots() > 0) {
                    goal = "顺手回收附近掉落物";
                    recommendedMode = state.mode();
                    steps.add("靠近掉落物");
                    steps.add("拾取后回归原任务");
                    reasoning = "掉落物属于低成本收益，可与当前巡逻或探索兼容";
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
                return localPlan(goal, recommendedMode, steps, reasoning, state, memory);
            }
        }

        return localPlan(
                "保持当前任务",
                state.mode(),
                List.of("继续执行当前模式逻辑"),
                "未命中更高优先级规则，沿用当前策略",
                state,
                memory);
    }

    private static AIAgentPlan localPlan(
            String goal,
            AIPlayerMode recommendedMode,
            List<String> steps,
            String reasoning,
            AIAgentWorldState state,
            AIAgentMemorySnapshot memory) {
        return new AIAgentPlan(goal, recommendedMode, steps, enrichReasoning(reasoning, state, memory), "local-pipeline");
    }

    private static String enrichReasoning(String reasoning, AIAgentWorldState state, AIAgentMemorySnapshot memory) {
        StringBuilder builder = new StringBuilder(reasoning);
        if (state.activeTask() != null && !state.activeTask().isBlank()) {
            builder.append("；当前任务=").append(state.activeTask());
        }
        if (state.lastTaskFeedback() != null && !state.lastTaskFeedback().isBlank()) {
            builder.append("；最近反馈=").append(state.lastTaskFeedback());
        }
        if (state.progressStalled()) {
            builder.append("；进展=停滞");
        }
        if (state.taskFailureStreak() > 0) {
            builder.append("；失败连击=").append(state.taskFailureStreak());
        }
        if (state.taskSuccessStreak() > 0) {
            builder.append("；成功连击=").append(state.taskSuccessStreak());
        }
        if (memory.longTermSummary() != null && !memory.longTermSummary().isBlank()) {
            builder.append("；记忆=").append(memory.longTermSummary());
        }
        return builder.toString();
    }
}

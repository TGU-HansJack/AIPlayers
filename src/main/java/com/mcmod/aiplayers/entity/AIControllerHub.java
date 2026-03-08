package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.knowledge.KnowledgeManager;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public final class AIControllerHub {
    private final AIPlayerEntity entity;

    AIControllerHub(AIPlayerEntity entity) {
        this.entity = entity;
    }

    public String capabilitySummary() {
        return "vision(scanBlocks/scanEntities/scanHostiles/scanResources/scanStructures), "
                + "movement(goTo/follow/wander/jump/look/input/avoidObstacle/stop), "
                + "interaction(openDoor/openChest/sleepInBed/pressButton/useItem), "
                + "mining(mineBlock/mineOre/chopTree/clearArea), "
                + "combat(attack/raiseShield/lowerShield/retreat/kiteEnemy), "
                + "inventory(equipBestTool/selectSlot/pickupItem/dropItem/storeInChest), "
                + "building(placeBlock/buildStructure/buildBridge/buildStairs), "
                + "task(setGoal/chooseTask/updateTask/cancelTask)";
    }

    public ControllerExecutionResult execute(ServerPlayer speaker, String controller, String action, Map<String, String> args) {
        String controllerKey = normalize(controller);
        String actionKey = normalize(action);
        if (controllerKey.isBlank()) {
            controllerKey = inferControllerByAction(actionKey);
        }
        Map<String, String> safeArgs = args == null ? Map.of() : args;
        return switch (controllerKey) {
            case "vision", "perception" -> this.execVision(actionKey);
            case "movement", "move", "input" -> this.execMovement(speaker, actionKey, safeArgs);
            case "interaction", "interact", "use" -> this.execInteraction(actionKey, safeArgs);
            case "mining", "mine" -> this.execMining(speaker, actionKey, safeArgs);
            case "combat", "fight" -> this.execCombat(speaker, actionKey, safeArgs);
            case "inventory", "inv", "bag" -> this.execInventory(actionKey, safeArgs);
            case "building", "build" -> this.execBuilding(speaker, actionKey, safeArgs);
            case "task", "planner" -> this.execTask(speaker, actionKey, safeArgs);
            default -> ControllerExecutionResult.fail("未知控制器：" + controller + "，可用：" + this.capabilitySummary());
        };
    }

    private static String inferControllerByAction(String actionKey) {
        if (actionKey == null || actionKey.isBlank()) {
            return "";
        }
        return switch (actionKey) {
            case "scan", "all", "scanblocks", "blocks", "scanentities", "entities", "scanhostiles", "hostiles", "scanresources", "resources", "scanstructures", "structures" -> "vision";
            case "goto", "go_to", "move_to", "follow", "follow_owner", "wander", "explore", "jump", "look", "lookat", "look_at", "input", "set_input", "wasd", "avoidobstacle", "avoid_obstacle", "recover", "stop", "halt" -> "movement";
            case "opendoor", "open_door", "open", "openchest", "open_chest", "sleepinbed", "sleep_in_bed", "sleep", "pressbutton", "press_button", "useitem", "use_item", "use" -> "interaction";
            case "mineore", "mine_ore", "choptree", "chop_tree", "mineblock", "mine_block", "cleararea", "clear_area" -> "mining";
            case "attack", "raiseshield", "raise_shield", "lowershield", "lower_shield", "retreat", "kiteenemy", "kite_enemy" -> "combat";
            case "equipbesttool", "equip_best_tool", "selectslot", "select_slot", "pickupitem", "pickup_item", "dropitem", "drop_item", "storeinchest", "store_in_chest" -> "inventory";
            case "placeblock", "place_block", "buildstructure", "build_structure", "buildbridge", "build_bridge", "buildstairs", "build_stairs" -> "building";
            case "setgoal", "set_goal", "choosetask", "choose_task", "updatetask", "update_task", "canceltask", "cancel_task" -> "task";
            default -> "";
        };
    }

    private ControllerExecutionResult execVision(String action) {
        this.entity.runtimeRefreshPerception();
        WorldStateSnapshot snapshot = this.entity.captureWorldStateSnapshot();
        return switch (action) {
            case "", "scan", "all" -> ControllerExecutionResult.ok("视觉扫描完成：" + snapshot.observation());
            case "scanblocks", "blocks" -> ControllerExecutionResult.ok("方块：木头=" + formatPos(snapshot.woodPos()) + "，矿石=" + formatPos(snapshot.orePos()));
            case "scanentities", "entities" -> ControllerExecutionResult.ok("实体：敌对=" + yesNo(snapshot.hostileNearby()) + "，掉落物=" + yesNo(snapshot.dropNearby()));
            case "scanhostiles", "hostiles" -> ControllerExecutionResult.ok("危险：敌对=" + yesNo(snapshot.hostileNearby()) + "，主人受威胁=" + yesNo(snapshot.ownerUnderThreat()));
            case "scanresources", "resources" -> ControllerExecutionResult.ok("资源：木头=" + formatPos(snapshot.woodPos()) + "，矿石=" + formatPos(snapshot.orePos()) + "，作物=" + formatPos(snapshot.cropPos()));
            case "scanstructures", "structures" -> ControllerExecutionResult.ok("结构：床位=" + formatPos(snapshot.bedPos()) + "，设施=" + yesNo(snapshot.utilitiesKnown()));
            default -> ControllerExecutionResult.fail("VisionController 不支持动作：" + action);
        };
    }

    private ControllerExecutionResult execMovement(ServerPlayer speaker, String action, Map<String, String> args) {
        return switch (action) {
            case "goto", "go_to", "move_to" -> {
                BlockPos target = parseBlockPos(args, null);
                if (target == null) {
                    yield ControllerExecutionResult.fail("goTo 需要 x/y/z 或 pos=x,y,z");
                }
                double speed = Mth.clamp(parseDouble(args, "speed", 1.05D), 0.45D, 1.35D);
                BlockPos resolved = this.entity.runtimeResolveMovementTarget(target);
                if (resolved == null) {
                    yield ControllerExecutionResult.fail("目标不可达：" + formatPos(target));
                }
                boolean started = this.entity.runtimeNavigateToPosition(resolved, speed);
                yield started || this.entity.runtimeIsWithin(resolved, 2.56D)
                        ? ControllerExecutionResult.ok("已开始移动到 " + formatPos(resolved))
                        : ControllerExecutionResult.fail("导航启动失败：" + formatPos(resolved));
            }
            case "follow", "follow_owner" -> {
                this.entity.applyCommandedMode(speaker, AIPlayerMode.FOLLOW);
                yield ControllerExecutionResult.ok("已切换跟随模式。");
            }
            case "wander", "explore" -> {
                this.entity.applyCommandedMode(speaker, AIPlayerMode.EXPLORE);
                yield ControllerExecutionResult.ok("已切换探索模式。");
            }
            case "jump" -> ControllerExecutionResult.ok(this.entity.performAction(AIPlayerAction.JUMP));
            case "look", "lookat", "look_at" -> {
                BlockPos target = parseBlockPos(args, null);
                if (target == null) {
                    yield ControllerExecutionResult.fail("lookAt 需要 x/y/z 或 pos=x,y,z");
                }
                this.entity.runtimeLookAt(Vec3.atCenterOf(target), Math.max(10, parseInt(args, "ticks", 20)));
                yield ControllerExecutionResult.ok("已看向 " + formatPos(target));
            }
            case "input", "set_input", "wasd" -> {
                this.entity.runtimeSetWasdControl(
                        Mth.clamp(parseFloat(args, "forward", 0.0F), -1.0F, 1.0F),
                        Mth.clamp(parseFloat(args, "strafe", 0.0F), -1.0F, 1.0F),
                        Mth.clamp(parseFloat(args, "speed", 0.18F), 0.0F, 0.42F),
                        parseFloat(args, "yaw", this.entity.getYRot()),
                        parseBoolean(args, "sprint", false),
                        parseBoolean(args, "jump", false));
                yield ControllerExecutionResult.ok("已应用 WASD 输入控制。");
            }
            case "avoidobstacle", "avoid_obstacle", "recover" -> this.entity.runtimeAttemptImmediateRecovery()
                    ? ControllerExecutionResult.ok("已触发避障/脱困逻辑。")
                    : ControllerExecutionResult.fail("当前没有可执行的避障动作。");
            case "stop", "halt" -> {
                this.entity.runtimeClearWasdOverride();
                this.entity.getNavigation().stop();
                this.entity.setZza(0.0F);
                this.entity.setXxa(0.0F);
                this.entity.setSpeed(0.0F);
                yield ControllerExecutionResult.ok("已停止移动控制。");
            }
            default -> ControllerExecutionResult.fail("MovementController 不支持动作：" + action);
        };
    }

    private ControllerExecutionResult execInteraction(String action, Map<String, String> args) {
        return switch (action) {
            case "opendoor", "open_door", "open" -> {
                BlockPos target = parseBlockPos(args, findNearestToggleable(this.entity.blockPosition(), 4));
                if (target == null) {
                    yield ControllerExecutionResult.fail("附近未找到可开关方块。");
                }
                yield this.entity.runtimeBreakPathNavigationBlock(target)
                        ? ControllerExecutionResult.ok("已处理通路方块：" + formatPos(target))
                        : ControllerExecutionResult.fail("无法操作方块：" + formatPos(target));
            }
            case "openchest", "open_chest" -> {
                BlockPos chestPos = this.entity.resolveRuntimeTarget("chest", null);
                if (chestPos == null) {
                    yield ControllerExecutionResult.fail("未找到箱子位置。");
                }
                boolean started = this.entity.runtimeNavigateToPosition(chestPos, 1.0D);
                if (!started && !this.entity.runtimeIsWithin(chestPos, 3.0D)) {
                    yield ControllerExecutionResult.fail("无法靠近箱子：" + formatPos(chestPos));
                }
                this.entity.runtimeAdjustViewTo(chestPos);
                yield ControllerExecutionResult.ok("已前往箱子并尝试交互。");
            }
            case "sleepinbed", "sleep_in_bed", "sleep" -> {
                BlockPos bedPos = this.entity.resolveRuntimeTarget("bed", null);
                if (bedPos == null) {
                    yield ControllerExecutionResult.fail("未找到床位。");
                }
                boolean started = this.entity.runtimeNavigateToPosition(bedPos, 1.0D);
                if (!started && !this.entity.runtimeIsWithin(bedPos, 3.0D)) {
                    yield ControllerExecutionResult.fail("无法靠近床位：" + formatPos(bedPos));
                }
                this.entity.runtimeAdjustViewTo(bedPos);
                yield ControllerExecutionResult.ok("已前往床位并尝试休息。");
            }
            case "pressbutton", "press_button" -> {
                BlockPos pos = parseBlockPos(args, null);
                if (pos == null) {
                    yield ControllerExecutionResult.fail("pressButton 需要 x/y/z 或 pos=x,y,z");
                }
                this.entity.runtimeAdjustViewTo(pos);
                this.entity.swing(InteractionHand.MAIN_HAND);
                yield ControllerExecutionResult.ok("已尝试触发按钮/拉杆：" + formatPos(pos));
            }
            case "useitem", "use_item", "use" -> {
                String hand = normalize(args.getOrDefault("hand", "main"));
                InteractionHand interactionHand = "off".equals(hand) || "offhand".equals(hand) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                this.entity.swing(interactionHand);
                this.entity.startUsingItem(interactionHand);
                yield ControllerExecutionResult.ok("已触发 useItem，hand=" + interactionHand.name());
            }
            default -> ControllerExecutionResult.fail("InteractionController 不支持动作：" + action);
        };
    }

    private ControllerExecutionResult execMining(ServerPlayer speaker, String action, Map<String, String> args) {
        return switch (action) {
            case "mineore", "mine_ore" -> {
                this.entity.applyCommandedMode(speaker, AIPlayerMode.MINE);
                yield ControllerExecutionResult.ok("已切换挖矿模式。");
            }
            case "choptree", "chop_tree" -> {
                this.entity.applyCommandedMode(speaker, AIPlayerMode.GATHER_WOOD);
                yield ControllerExecutionResult.ok("已切换砍树模式。");
            }
            case "mineblock", "mine_block" -> this.mineBlock(args);
            case "cleararea", "clear_area" -> this.clearArea(args);
            default -> ControllerExecutionResult.fail("MiningController 不支持动作：" + action);
        };
    }

    private ControllerExecutionResult execCombat(ServerPlayer speaker, String action, Map<String, String> args) {
        return switch (action) {
            case "attack" -> {
                String animal = firstNonBlank(args.get("animal"), args.get("target"), args.get("mob"));
                if (animal != null && !animal.isBlank()) {
                    AnimalTargetHelper.HuntTarget hunt = AnimalTargetHelper.resolveHuntTarget(animal);
                    if (hunt != null) {
                        yield ControllerExecutionResult.ok(this.entity.startAnimalHunt(speaker, hunt.targetId(), hunt.label()));
                    }
                }
                this.entity.applyCommandedMode(speaker, AIPlayerMode.SURVIVE);
                this.entity.runtimeTickCombatSense();
                yield ControllerExecutionResult.ok("已执行战斗感知并进入生存战斗模式。");
            }
            case "raiseshield", "raise_shield" -> this.entity.runtimeRaiseShieldGuard()
                    ? ControllerExecutionResult.ok("已举盾。")
                    : ControllerExecutionResult.fail("举盾失败：副手无盾牌。");
            case "lowershield", "lower_shield" -> {
                this.entity.runtimeLowerShieldGuard();
                yield ControllerExecutionResult.ok("已放下盾牌。");
            }
            case "retreat" -> {
                this.entity.applyCommandedMode(speaker, AIPlayerMode.FOLLOW);
                yield ControllerExecutionResult.ok("已撤退为跟随模式。");
            }
            case "kiteenemy", "kite_enemy" -> {
                this.entity.applyCommandedMode(speaker, AIPlayerMode.GUARD);
                this.entity.runtimeRaiseShieldGuard();
                yield ControllerExecutionResult.ok("已执行 kite 策略（护卫+举盾）。");
            }
            default -> ControllerExecutionResult.fail("CombatController 不支持动作：" + action);
        };
    }

    private ControllerExecutionResult execInventory(String action, Map<String, String> args) {
        return switch (action) {
            case "equipbesttool", "equip_best_tool" -> this.equipBestTool(args);
            case "selectslot", "select_slot" -> this.selectSlot(args);
            case "pickupitem", "pickup_item" -> this.entity.runtimeCollectNearbyDrops()
                    ? ControllerExecutionResult.ok("已执行拾取动作。")
                    : ControllerExecutionResult.fail("附近没有可拾取掉落物。");
            case "dropitem", "drop_item" -> this.dropItem(args);
            case "storeinchest", "store_in_chest" -> {
                BlockPos chestPos = this.entity.resolveRuntimeTarget("chest", null);
                if (chestPos == null) {
                    yield ControllerExecutionResult.fail("未找到箱子位置。");
                }
                boolean started = this.entity.runtimeNavigateToPosition(chestPos, 1.0D);
                yield started || this.entity.runtimeIsWithin(chestPos, 3.0D)
                        ? ControllerExecutionResult.ok("已前往箱子，准备存取物品。")
                        : ControllerExecutionResult.fail("无法靠近箱子：" + formatPos(chestPos));
            }
            default -> ControllerExecutionResult.fail("InventoryController 不支持动作：" + action);
        };
    }

    private ControllerExecutionResult execBuilding(ServerPlayer speaker, String action, Map<String, String> args) {
        return switch (action) {
            case "buildstructure", "build_structure" -> {
                String blueprint = firstNonBlank(args.get("blueprint"), args.get("template"), args.get("id"));
                if (blueprint != null && !blueprint.isBlank()) {
                    this.entity.selectBlueprint(blueprint);
                }
                this.entity.applyCommandedMode(speaker, AIPlayerMode.BUILD_SHELTER);
                yield ControllerExecutionResult.ok("已切换建造模式，蓝图=" + this.entity.getActiveBlueprintId());
            }
            case "placeblock", "place_block" -> {
                BlockPos pos = parseBlockPos(args, null);
                if (pos == null) {
                    yield ControllerExecutionResult.fail("placeBlock 需要 x/y/z 或 pos=x,y,z");
                }
                if (this.entity.runtimePlacePathSupport(pos) || this.entity.runtimePlaceShelterBlock(pos)) {
                    yield ControllerExecutionResult.ok("已放置方块：" + formatPos(pos));
                }
                yield ControllerExecutionResult.fail("无法在该位置放置方块：" + formatPos(pos));
            }
            case "buildbridge", "build_bridge" -> this.buildBridge();
            case "buildstairs", "build_stairs" -> this.buildStairs();
            default -> ControllerExecutionResult.fail("BuildingController 不支持动作：" + action);
        };
    }

    private ControllerExecutionResult execTask(ServerPlayer speaker, String action, Map<String, String> args) {
        if ("canceltask".equals(action) || "cancel_task".equals(action) || "stop".equals(action)) {
            this.entity.runtimeStopGoal();
            return ControllerExecutionResult.ok("已取消任务并切换待命。");
        }
        if (!"setgoal".equals(action) && !"set_goal".equals(action) && !"choosetask".equals(action) && !"choose_task".equals(action) && !"updatetask".equals(action) && !"update_task".equals(action)) {
            return ControllerExecutionResult.fail("TaskController 不支持动作：" + action);
        }
        String requested = firstNonBlank(args.get("goalType"), args.get("goal"), args.get("mode"), args.get("task"));
        if (requested == null || requested.isBlank()) {
            return ControllerExecutionResult.fail("setGoal 需要 goalType/goal/mode/task 参数。");
        }
        GoalType goalType = GoalType.fromLegacyText(requested);
        if (goalType == null) {
            AIPlayerMode mode = AIPlayerMode.fromCommand(requested);
            if (mode != null) {
                goalType = GoalType.fromMode(mode);
            }
        }
        if (goalType == null) {
            return ControllerExecutionResult.fail("无法识别目标任务：" + requested);
        }
        this.entity.runtimeApplyGoalDirective(speaker, AgentGoal.of(goalType, args, 60, "controller", "控制器任务指令", GoalType.SURVIVE), true);
        return ControllerExecutionResult.ok("已切换任务目标：" + goalType.displayName());
    }

    private ControllerExecutionResult mineBlock(Map<String, String> args) {
        BlockPos pos = parseBlockPos(args, null);
        if (pos == null) {
            return ControllerExecutionResult.fail("mineBlock 需要 x/y/z 或 pos=x,y,z");
        }
        BlockState state = this.entity.level().getBlockState(pos);
        boolean wood = KnowledgeManager.isTreeLog(state) || state.is(BlockTags.LOGS);
        if (wood && this.entity.runtimeCanHarvestFromHere(pos)) {
            this.entity.runtimePrepareHarvestTool(true);
            boolean done = this.entity.runtimeHarvestTarget(pos, true);
            if (done || this.entity.runtimeIsMiningLocked()) {
                return ControllerExecutionResult.ok(done ? "木头采集完成：" + formatPos(pos) : "已开始采集木头：" + formatPos(pos));
            }
        }
        if (isLikelyOre(state) && this.entity.runtimeCanHarvestFromHere(pos)) {
            this.entity.runtimePrepareHarvestTool(false);
            boolean done = this.entity.runtimeHarvestTarget(pos, false);
            if (done || this.entity.runtimeIsMiningLocked()) {
                return ControllerExecutionResult.ok(done ? "矿石采掘完成：" + formatPos(pos) : "已开始采掘矿石：" + formatPos(pos));
            }
        }
        if (this.entity.runtimeCanBreakPathBlock(pos)) {
            boolean done = this.entity.runtimeBreakPathNavigationBlock(pos);
            if (done || this.entity.runtimeIsMiningLocked()) {
                return ControllerExecutionResult.ok(done ? "障碍清理完成：" + formatPos(pos) : "已开始清理障碍：" + formatPos(pos));
            }
        }
        return ControllerExecutionResult.fail("当前无法挖掘目标方块：" + formatPos(pos));
    }

    private ControllerExecutionResult clearArea(Map<String, String> args) {
        BlockPos center = parseBlockPos(args, this.entity.blockPosition());
        int radius = Mth.clamp(parseInt(args, "radius", 1), 1, 4);
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos candidate = center.offset(x, y, z);
                    if (!this.entity.runtimeCanBreakPathBlock(candidate)) {
                        continue;
                    }
                    boolean done = this.entity.runtimeBreakPathNavigationBlock(candidate);
                    if (done || this.entity.runtimeIsMiningLocked()) {
                        return ControllerExecutionResult.ok("已开始清理区域方块：" + formatPos(candidate));
                    }
                }
            }
        }
        return ControllerExecutionResult.fail("clearArea 未发现可清理方块。");
    }

    private ControllerExecutionResult equipBestTool(Map<String, String> args) {
        String forType = normalize(firstNonBlank(args.get("for"), args.get("target"), args.get("kind")));
        if (forType.contains("wood") || forType.contains("log") || forType.contains("tree") || forType.contains("axe") || forType.contains("木")) {
            return this.entity.runtimePrepareHarvestTool(true)
                    ? ControllerExecutionResult.ok("已装备砍树工具。")
                    : ControllerExecutionResult.fail("未找到可用斧头。");
        }
        if (forType.contains("ore") || forType.contains("mine") || forType.contains("pick") || forType.contains("矿")) {
            return this.entity.runtimePrepareHarvestTool(false)
                    ? ControllerExecutionResult.ok("已装备采矿工具。")
                    : ControllerExecutionResult.fail("未找到可用镐子。");
        }
        return this.entity.runtimePrepareHarvestTool(true) || this.entity.runtimePrepareHarvestTool(false)
                ? ControllerExecutionResult.ok("已自动装备可用工具。")
                : ControllerExecutionResult.fail("未找到适配工具。");
    }

    private ControllerExecutionResult selectSlot(Map<String, String> args) {
        int slot = parseInt(args, "slot", -1);
        if (slot < 0 || slot >= this.entity.getBackpackSize()) {
            return ControllerExecutionResult.fail("slot 超出范围，需在 0-" + (this.entity.getBackpackSize() - 1));
        }
        ItemStack stack = this.entity.getBackpackStack(slot);
        if (stack.isEmpty()) {
            return ControllerExecutionResult.fail("指定槽位为空：" + slot);
        }
        this.entity.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
        return ControllerExecutionResult.ok("已切换主手到槽位 " + slot + "（" + stack.getHoverName().getString() + "）");
    }

    private ControllerExecutionResult dropItem(Map<String, String> args) {
        int count = Math.max(1, parseInt(args, "count", 1));
        int slot = parseInt(args, "slot", -1);
        ItemStack removed;
        if (slot >= 0) {
            removed = this.entity.removeBackpackStack(slot, count);
        } else {
            ItemStack held = this.entity.getMainHandItem();
            if (held.isEmpty()) {
                return ControllerExecutionResult.fail("主手为空，且未指定 slot。");
            }
            removed = held.split(Math.min(count, held.getCount()));
            if (held.isEmpty()) {
                this.entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
        }
        if (removed == null || removed.isEmpty()) {
            return ControllerExecutionResult.fail("没有可丢弃的物品。");
        }
        if (this.entity.level() instanceof ServerLevel serverLevel) {
            this.entity.spawnAtLocation(serverLevel, removed);
        }
        return ControllerExecutionResult.ok("已丢弃 " + removed.getHoverName().getString() + " x" + removed.getCount());
    }

    private ControllerExecutionResult buildBridge() {
        Direction direction = Direction.fromYRot(this.entity.getYRot());
        BlockPos base = this.entity.blockPosition();
        for (int i = 1; i <= 3; i++) {
            BlockPos support = base.relative(direction, i).below();
            if (this.entity.runtimeCanPlacePathSupport(support) && this.entity.runtimePlacePathSupport(support)) {
                return ControllerExecutionResult.ok("已搭建桥面支撑：" + formatPos(support));
            }
        }
        return ControllerExecutionResult.fail("当前无法搭建桥面。");
    }

    private ControllerExecutionResult buildStairs() {
        Direction direction = Direction.fromYRot(this.entity.getYRot());
        BlockPos base = this.entity.blockPosition();
        for (int i = 0; i < 2; i++) {
            BlockPos support = base.relative(direction, i + 1).above(i).below();
            if (this.entity.runtimeCanPlacePathSupport(support) && this.entity.runtimePlacePathSupport(support)) {
                return ControllerExecutionResult.ok("已搭建阶梯支撑：" + formatPos(support));
            }
        }
        return ControllerExecutionResult.fail("当前无法搭建阶梯。");
    }

    private BlockPos findNearestToggleable(BlockPos center, int radius) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = this.entity.level().getBlockState(pos);
                    if (state.isAir() || !state.hasProperty(BlockStateProperties.OPEN) || Boolean.TRUE.equals(state.getValue(BlockStateProperties.OPEN))) {
                        continue;
                    }
                    double distance = this.entity.distanceToSqr(Vec3.atCenterOf(pos));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    public record ControllerExecutionResult(boolean success, String message) {
        static ControllerExecutionResult ok(String message) {
            return new ControllerExecutionResult(true, message == null || message.isBlank() ? "操作完成。" : message);
        }

        static ControllerExecutionResult fail(String message) {
            return new ControllerExecutionResult(false, message == null || message.isBlank() ? "操作失败。" : message);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static BlockPos parseBlockPos(Map<String, String> args, BlockPos fallback) {
        if (args == null || args.isEmpty()) {
            return fallback;
        }
        String packed = firstNonBlank(args.get("pos"), args.get("position"), args.get("target"), args.get("xyz"));
        if (packed != null) {
            String[] parts = packed.replace(" ", "").split(",");
            if (parts.length == 3) {
                Integer px = parseInteger(parts[0]);
                Integer py = parseInteger(parts[1]);
                Integer pz = parseInteger(parts[2]);
                if (px != null && py != null && pz != null) {
                    return new BlockPos(px, py, pz);
                }
            }
        }
        Integer x = parseInteger(args.get("x"));
        Integer y = parseInteger(args.get("y"));
        Integer z = parseInteger(args.get("z"));
        return x != null && y != null && z != null ? new BlockPos(x, y, z) : fallback;
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int parseInt(Map<String, String> args, String key, int fallback) {
        Integer value = args == null ? null : parseInteger(args.get(key));
        return value == null ? fallback : value;
    }

    private static double parseDouble(Map<String, String> args, String key, double fallback) {
        if (args == null) {
            return fallback;
        }
        String raw = args.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloat(Map<String, String> args, String key, float fallback) {
        if (args == null) {
            return fallback;
        }
        String raw = args.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(Map<String, String> args, String key, boolean fallback) {
        if (args == null) {
            return fallback;
        }
        String raw = args.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "1", "true", "yes", "on", "是" -> true;
            case "0", "false", "no", "off", "否" -> false;
            default -> fallback;
        };
    }

    private static boolean isLikelyOre(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) {
            return false;
        }
        String path = id.getPath();
        return path.contains("ore") || path.contains("debris");
    }

    private static String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    private static String formatPos(BlockPos pos) {
        return pos == null ? "未知" : (pos.getX() + "," + pos.getY() + "," + pos.getZ());
    }
}

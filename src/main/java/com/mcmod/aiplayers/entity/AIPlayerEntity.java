package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.ai.AIServiceManager;
import com.mcmod.aiplayers.ai.AIServiceResponse;
import com.mcmod.aiplayers.system.AILongTermMemoryStore;
import com.mcmod.aiplayers.system.AITaskCoordinator;
import com.mcmod.aiplayers.system.BlueprintRegistry;
import com.mcmod.aiplayers.system.BlueprintTemplate;
import com.mcmod.aiplayers.system.ChatIntent;
import com.mcmod.aiplayers.system.ChatIntentParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.BlockTags;

public class AIPlayerEntity extends Zombie {
    private static final EntityDataAccessor<String> DATA_AI_NAME = SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_OWNER_ID = SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_MODE = SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_BLUEPRINT = SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);
    private static final String DEFAULT_AI_NAME = "Companion";
    private static final String DEFAULT_OBSERVATION = "All clear.";
    private static final String DEFAULT_BLUEPRINT_ID = "shelter";
    private static final int BACKPACK_SIZE = 27;
    private static final int SCAN_RADIUS = 12;
    private static final int SCAN_VERTICAL_DOWN = 6;
    private static final int SCAN_VERTICAL_UP = 10;
    private static final int SCAN_INTERVAL = 20;
    private static final int MEMORY_LIMIT = 12;
    private static final int NAVIGATION_STUCK_THRESHOLD = 60;
    private static final double HARVEST_REACH = 4.75D;

    private final NonNullList<ItemStack> backpack = NonNullList.withSize(BACKPACK_SIZE, ItemStack.EMPTY);
    private final List<String> memory = new ArrayList<>();

    private AIPlayerMode mode = AIPlayerMode.IDLE;
    private UUID ownerId;
    private String aiName = DEFAULT_AI_NAME;
    private String lastObservation = "周围一切正常。";
    private BlockPos rememberedLog;
    private BlockPos rememberedOre;
    private LivingEntity observedHostile;
    private BlockPos shelterAnchor;
    private BlockPos lastExploreRecord;
    private int crouchTicks;
    private int lookTicks;
    private Vec3 forcedLookTarget;
    private int jumpCooldown;
    private boolean pendingAiResponse;
    private boolean persistentStateLoaded;
    private boolean persistentStateDirty;
    private BlockPos activeNavigationTarget;
    private Vec3 lastNavigationSample = Vec3.ZERO;
    private int stuckNavigationTicks;
    private String activeBlueprintId = DEFAULT_BLUEPRINT_ID;

    public AIPlayerEntity(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
        this.xpReward = 0;
        this.refreshDisplayName();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_AI_NAME, DEFAULT_AI_NAME);
        builder.define(DATA_OWNER_ID, "");
        builder.define(DATA_MODE, AIPlayerMode.IDLE.commandName());
        builder.define(DATA_BLUEPRINT, DEFAULT_BLUEPRINT_ID);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, true));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (this.level().isClientSide()) {
            return;
        }

        this.ensurePersistentStateLoaded();

        if (this.tickCount % SCAN_INTERVAL == 0) {
            this.scanSurroundings();
        }

        this.tickActionState();
        this.tickNavigationState();
        this.runModeLogic();

        if (this.persistentStateDirty) {
            this.syncPersistentState();
        }
    }

    public void initializeCompanion(ServerPlayer owner, String name) {
        this.assignOwner(owner);
        this.aiName = name;
        this.setMode(AIPlayerMode.FOLLOW);
        this.setHealth(this.getMaxHealth());
        this.populateStarterKit();
        this.remember("绑定", "已绑定玩家 " + owner.getName().getString());
        this.refreshDisplayName();
        this.markPersistentDirty();
    }

    public String getAIName() {
        String synced = this.entityData.get(DATA_AI_NAME);
        return synced == null || synced.isBlank() ? this.aiName : synced;
    }

    public AIPlayerMode getMode() {
        return this.safeMode();
    }

    public void assignOwner(ServerPlayer player) {
        this.ownerId = player.getUUID();
        this.syncClientState();
        this.markPersistentDirty();
    }

    public boolean canReceiveOrdersFrom(ServerPlayer player) {
        return this.ownerId == null || this.ownerId.equals(player.getUUID());
    }

    public void setMode(AIPlayerMode newMode) {
        AIPlayerMode normalizedMode = newMode == null ? AIPlayerMode.IDLE : newMode;
        this.mode = normalizedMode;
        this.setTarget(null);
        this.getNavigation().stop();
        this.resetNavigationState();
        this.applyModeEquipment();
        if (normalizedMode != AIPlayerMode.BUILD_SHELTER) {
            this.shelterAnchor = null;
        }
        this.remember("??", "??? " + normalizedMode.displayName());
        this.refreshDisplayName();
        this.markPersistentDirty();
    }

    public String executeConversation(ServerPlayer speaker, String content) {
        if (!this.canReceiveOrdersFrom(speaker)) {
            return "我现在只接受绑定玩家的命令。";
        }

        this.remember("对话", speaker.getName().getString() + "：" + content);
        ChatIntent intent = ChatIntentParser.parse(content);
        if (intent != ChatIntent.UNKNOWN) {
            return this.executeIntent(speaker, intent);
        }

        if (AIServiceManager.canUseExternalService()) {
            if (this.pendingAiResponse) {
                return "我还在思考上一条消息，请稍等。";
            }

            this.pendingAiResponse = true;
            this.markPersistentDirty();
            AIServiceManager.tryRespondAsync(this, speaker, content).whenComplete((apiResponse, throwable) -> {
                var server = speaker.level().getServer();
                if (server == null) {
                    this.pendingAiResponse = false;
                    return;
                }

                server.execute(() -> {
                    this.pendingAiResponse = false;
                    this.markPersistentDirty();

                    String reply;
                    if (throwable != null || apiResponse == null) {
                        reply = this.executeIntent(speaker, ChatIntent.UNKNOWN);
                    } else {
                        String directiveResult = this.applyApiDirective(speaker, apiResponse);
                        reply = apiResponse.reply() == null || apiResponse.reply().isBlank() ? directiveResult : apiResponse.reply();
                        this.remember("AI", reply + "（" + apiResponse.source() + "）");
                    }

                    if (!speaker.isRemoved()) {
                        speaker.sendSystemMessage(Component.literal("[" + this.getAIName() + "] " + reply));
                    }
                });
            });
            return "收到，我先思考一下。";
        }

        return this.executeIntent(speaker, ChatIntent.UNKNOWN);
    }
    public String executeIntent(ServerPlayer speaker, ChatIntent intent) {
        if (!this.canReceiveOrdersFrom(speaker)) {
            return "我现在只接受绑定玩家的命令。";
        }

        switch (intent) {
            case GREET -> {
                return "你好，我已就绪。" + this.getObservationSummary();
            }
            case HELP -> {
                return "你可以让我跟随、护卫、砍树、挖矿、探索、建造、生存，也可以让我跳跃、下蹲、抬头、查看记忆或询问当前计划。";
            }
            case STATUS -> {
                return this.getStatusSummary();
            }
            case MEMORY -> {
                return "最近记忆：" + this.getMemorySummary();
            }
            case PLAN -> {
                return "当前规划：" + this.getPlanSummary();
            }
            case FOLLOW -> {
                this.assignOwner(speaker);
                this.setMode(AIPlayerMode.FOLLOW);
                return "收到，我开始跟随你。";
            }
            case GUARD -> {
                this.assignOwner(speaker);
                this.setMode(AIPlayerMode.GUARD);
                return "收到，我会优先保护你。";
            }
            case GATHER_WOOD -> {
                this.assignOwner(speaker);
                this.setMode(AIPlayerMode.GATHER_WOOD);
                return "开始砍树，我会处理上方原木和附近木头。";
            }
            case MINE -> {
                this.assignOwner(speaker);
                this.setMode(AIPlayerMode.MINE);
                return "开始挖矿，我会寻找附近裸露矿石。";
            }
            case EXPLORE -> {
                this.assignOwner(speaker);
                this.setMode(AIPlayerMode.EXPLORE);
                return "开始探索，我会在附近巡查并记录位置。";
            }
            case BUILD -> {
                this.assignOwner(speaker);
                this.setMode(AIPlayerMode.BUILD_SHELTER);
                return "开始建造简易避难所，我会先补足建材。";
            }
            case SURVIVE -> {
                this.assignOwner(speaker);
                this.setMode(AIPlayerMode.SURVIVE);
                return "进入自主生存模式，我会战斗、采集、恢复并在夜晚尝试建造避难所。";
            }
            case JUMP -> {
                return this.performAction(AIPlayerAction.JUMP);
            }
            case CROUCH -> {
                return this.performAction(AIPlayerAction.CROUCH);
            }
            case STAND -> {
                return this.performAction(AIPlayerAction.STAND);
            }
            case LOOK_UP -> {
                return this.performAction(AIPlayerAction.LOOK_UP);
            }
            case LOOK_DOWN -> {
                return this.performAction(AIPlayerAction.LOOK_DOWN);
            }
            case LOOK_OWNER -> {
                return this.performAction(AIPlayerAction.LOOK_OWNER);
            }
            case STOP -> {
                this.setMode(AIPlayerMode.IDLE);
                return "已停止当前任务，进入待命。";
            }
            case UNKNOWN -> {
                return "我听到了，但没完全理解。你可以说：跟随、护卫、砍树、挖矿、探索、建造、生存、跳跃、下蹲、抬头、记忆、状态、计划、停止。";
            }
        }

        return "我已收到命令。";
    }

    public String performAction(AIPlayerAction action) {
        switch (action) {
            case JUMP -> {
                if (this.jumpCooldown <= 0 && this.onGround()) {
                    this.jumpFromGround();
                    this.jumpCooldown = 10;
                    this.remember("动作", "执行跳跃");
                    return "收到，我跳一下。";
                }
                return "我现在不适合起跳。";
            }
            case CROUCH -> {
                this.crouchTicks = Math.max(this.crouchTicks, 60);
                this.setShiftKeyDown(true);
                this.remember("动作", "执行下蹲");
                return "收到，我先蹲下。";
            }
            case STAND -> {
                this.crouchTicks = 0;
                this.setShiftKeyDown(false);
                this.remember("动作", "结束下蹲");
                return "收到，我站起来了。";
            }
            case LOOK_UP -> {
                this.lookAtPitch(-50.0F, 30);
                this.remember("动作", "执行抬头");
                return "收到，我抬头查看。";
            }
            case LOOK_DOWN -> {
                this.lookAtPitch(45.0F, 30);
                this.remember("动作", "执行低头");
                return "收到，我低头观察。";
            }
            case LOOK_OWNER -> {
                ServerPlayer owner = this.getOwnerPlayer();
                if (owner != null) {
                    this.setForcedLookTarget(owner.getEyePosition(), 30);
                    this.remember("动作", "看向主人");
                    return "收到，我看向你。";
                }
                return "我暂时找不到主人位置。";
            }
        }

        return "动作已接收。";
    }
    public String getStatusSummary() {
        return "???" + this.safeModeDisplayName()
                + "????" + Math.round(this.getHealth()) + "/" + Math.round(this.getMaxHealth())
                + "????" + this.getUsedBackpackSlots() + "/" + BACKPACK_SIZE
                + "????" + this.getInventoryPreview()
                + "????" + this.getMemorySummary()
                + "????" + this.getPlanSummary()
                + "????" + this.getObservationSummary()
                + (this.pendingAiResponse ? "?AI????" : "");
    }


    public String getObservationSummary() {
        return this.lastObservation;
    }

    public String getMemorySummary() {
        if (this.memory.isEmpty()) {
            return "暂无";
        }
        int start = Math.max(0, this.memory.size() - 4);
        return String.join(" | ", this.memory.subList(start, this.memory.size()));
    }

    public String getPlanSummary() {
        String blueprintName = BlueprintRegistry.get(this.activeBlueprintId).displayName();
        AIPlayerMode currentMode = this.safeMode();
        int teamSize = AITaskCoordinator.getTeamSize(this, currentMode, 24.0D);
        return switch (currentMode) {
            case IDLE -> this.pendingAiResponse ? "等待当前对话回复 -> 保持警戒" : "原地待命 -> 观察附近玩家和威胁";
            case FOLLOW -> {
                ServerPlayer owner = this.getOwnerPlayer();
                yield owner == null
                        ? "等待重新绑定主人 -> 原地观察"
                        : (this.distanceToSqr(owner) > 9.0D
                                ? "靠近主人@" + this.formatPos(owner.blockPosition()) + " -> 保持 3 格跟随 -> 观察威胁"
                                : "保持主人附近阵型 -> 看向主人 -> 侦察周围");
            }
            case GUARD -> {
                ServerPlayer owner = this.getOwnerPlayer();
                yield owner == null
                        ? "等待重新绑定主人 -> 维持警戒"
                        : "护卫主人@" + this.formatPos(owner.blockPosition()) + " -> 优先处理敌对生物 -> 补位跟随";
            }
            case GATHER_WOOD -> "前往" + this.describePlanTarget(this.rememberedLog, "附近树木") + " -> 抬头定位原木 -> 必要时跳跃补位 -> 采集并收纳";
            case MINE -> "前往" + this.describePlanTarget(this.rememberedOre, "附近矿点") + " -> 选择可开采位置 -> 必要时下蹲稳位 -> 开采并收纳";
            case EXPLORE -> "随机探索新区域 -> 记录关键位置 -> 遇敌优先脱战或反击";
            case BUILD_SHELTER -> this.countAvailableBuildingUnits() < 12
                    ? "建材不足 -> 先去砍树 -> 转化木板 -> 返回锚点搭建避难所"
                    : "前往锚点" + this.describePlanTarget(this.shelterAnchor, "当前位置") + " -> 封墙封顶 -> 完成避难所";
            case SURVIVE -> "优先战斗自保 -> 低血吃面包 -> 白天采集探索 -> 夜晚建造避难所";
        };
    }

    private String applyApiDirective(ServerPlayer speaker, AIServiceResponse response) {
        String result = "我已经处理你的请求。";

        if (response.mode() != null && !response.mode().isBlank() && !"unchanged".equalsIgnoreCase(response.mode())) {
            AIPlayerMode apiMode = AIPlayerMode.fromCommand(response.mode());
            if (apiMode != null) {
                this.assignOwner(speaker);
                this.setMode(apiMode);
                result = "我已切换到" + apiMode.displayName() + "模式。";
            }
        }

        if (response.action() != null && !response.action().isBlank() && !"none".equalsIgnoreCase(response.action())) {
            AIPlayerAction action = AIPlayerAction.fromCommand(response.action());
            if (action != null) {
                result = this.performAction(action);
            }
        }

        return result;
    }

    private void runModeLogic() {
        this.autoMaintainSurvival();

        if (this.getTarget() != null && !this.getTarget().isAlive()) {
            this.setTarget(null);
        }

        switch (this.safeMode()) {
            case IDLE -> this.performIdle();
            case FOLLOW -> this.performFollow(false);
            case GUARD -> this.performFollow(true);
            case GATHER_WOOD -> this.performHarvestTask(true);
            case MINE -> this.performHarvestTask(false);
            case EXPLORE -> this.performExplore();
            case BUILD_SHELTER -> this.performBuildShelter();
            case SURVIVE -> this.performSurvive();
        }
    }

    private void autoMaintainSurvival() {
        if (this.getHealth() <= 12.0F && this.consumeBackpackItem(Items.BREAD, 1)) {
            this.heal(4.0F);
            this.crouchTicks = Math.max(this.crouchTicks, 15);
            this.lastObservation = "已消耗面包进行恢复。";
            this.remember("生存", "消耗面包恢复生命");
        }
    }

    private void tickActionState() {
        if (this.jumpCooldown > 0) {
            this.jumpCooldown--;
        } else {
            this.setJumping(false);
        }
        if (this.crouchTicks > 0) {
            this.crouchTicks--;
        }
        if (this.lookTicks > 0) {
            if (this.forcedLookTarget != null) {
                this.getLookControl().setLookAt(this.forcedLookTarget);
            }
            this.lookTicks--;
            if (this.lookTicks <= 0) {
                this.forcedLookTarget = null;
            }
        }

        boolean shouldCrouch = this.crouchTicks > 0 || this.shouldAutoCrouch();
        this.setShiftKeyDown(shouldCrouch);
    }

    private void tickNavigationState() {
        if (this.getNavigation().isDone()) {
            this.resetNavigationState();
            return;
        }
        if (this.activeNavigationTarget == null || this.tickCount % SCAN_INTERVAL != 0) {
            return;
        }

        Vec3 currentPos = this.position();
        boolean farFromGoal = this.distanceToSqr(Vec3.atCenterOf(this.activeNavigationTarget)) > 4.0D;
        if (farFromGoal && currentPos.distanceToSqr(this.lastNavigationSample) < 0.09D) {
            this.stuckNavigationTicks += SCAN_INTERVAL;
            if (this.stuckNavigationTicks >= NAVIGATION_STUCK_THRESHOLD) {
                this.lastObservation = "路径受阻，正在重新规划。";
                this.getNavigation().stop();
                this.resetNavigationState();
            }
        } else {
            this.stuckNavigationTicks = 0;
        }

        this.lastNavigationSample = currentPos;
    }

    private void resetNavigationState() {
        this.activeNavigationTarget = null;
        this.stuckNavigationTicks = 0;
        this.lastNavigationSample = this.position();
    }

    private boolean navigateToPosition(BlockPos pos, double speed) {
        if (pos == null) {
            return false;
        }
        if (this.activeNavigationTarget != null && this.activeNavigationTarget.distSqr(pos) <= 1.0D && !this.getNavigation().isDone()) {
            return true;
        }

        if (this.tryStartNavigation(pos, speed)) {
            return true;
        }

        BlockPos fallback = this.findWalkablePositionNear(pos, 2, 3);
        return fallback != null && !fallback.equals(pos) && this.tryStartNavigation(fallback, speed);
    }

    private boolean tryStartNavigation(BlockPos pos, double speed) {
        boolean started = this.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, speed);
        if (started) {
            this.activeNavigationTarget = pos;
            this.stuckNavigationTicks = 0;
            this.lastNavigationSample = this.position();
        }
        return started;
    }

    private boolean shouldAutoCrouch() {
        AIPlayerMode currentMode = this.safeMode();
        if (currentMode != AIPlayerMode.MINE && currentMode != AIPlayerMode.BUILD_SHELTER) {
            return false;
        }
        if (!this.getNavigation().isDone()) {
            return false;
        }

        Vec3 look = this.getLookAngle();
        int checkX = (int)Math.floor(this.getX() + look.x * 0.8D);
        int checkY = (int)Math.floor(this.getY()) - 1;
        int checkZ = (int)Math.floor(this.getZ() + look.z * 0.8D);
        return this.level().isEmptyBlock(new BlockPos(checkX, checkY, checkZ));
    }

    private void performIdle() {
        if (this.getTarget() == null) {
            this.getNavigation().stop();
            this.resetNavigationState();
        }

        ServerPlayer owner = this.getOwnerPlayer();
        if (owner != null && this.distanceToSqr(owner) < 16.0D) {
            this.setForcedLookTarget(owner.getEyePosition(), 10);
        }
    }

    private void performFollow(boolean guardMode) {
        ServerPlayer owner = this.getOwnerPlayer();
        if (owner == null) {
            this.setMode(AIPlayerMode.IDLE);
            return;
        }

        if (this.observedHostile != null) {
            double ownerThreatRange = guardMode ? 14.0D : 10.0D;
            if (this.distanceToSqr(this.observedHostile) <= 64.0D || owner.distanceToSqr(this.observedHostile) <= ownerThreatRange * ownerThreatRange) {
                this.setTarget(this.observedHostile);
                this.setForcedLookTarget(this.observedHostile.getEyePosition(), 10);
                return;
            }
        }

        if (this.getTarget() != null && this.getTarget().isAlive()) {
            this.setForcedLookTarget(this.getTarget().getEyePosition(), 10);
            return;
        }

        double followDistance = guardMode ? 5.0D : 3.0D;
        if (this.distanceToSqr(owner) > followDistance * followDistance) {
            this.getNavigation().moveTo(owner, 1.15D);
        } else {
            this.getNavigation().stop();
            this.resetNavigationState();
            this.setForcedLookTarget(owner.getEyePosition(), 10);
        }
    }

    private void performHarvestTask(boolean woodTask) {
        if (this.observedHostile != null && this.distanceToSqr(this.observedHostile) <= 49.0D) {
            this.setTarget(this.observedHostile);
            this.setForcedLookTarget(this.observedHostile.getEyePosition(), 10);
            return;
        }

        if (this.getTarget() != null && this.getTarget().isAlive()) {
            this.setForcedLookTarget(this.getTarget().getEyePosition(), 10);
            return;
        }

        BlockPos target = woodTask ? this.rememberedLog : this.rememberedOre;
        if (!this.isValidHarvestTarget(target, woodTask)) {
            target = this.findNearestHarvestBlock(woodTask);
            if (woodTask) {
                this.rememberedLog = target;
            } else {
                this.rememberedOre = target;
            }
        }

        if (target == null) {
            this.lastObservation = woodTask ? "附近没有可采伐木头，先去搜索。" : "附近没有可挖掘矿石，先去搜索。";
            this.getNavigation().stop();
            this.resetNavigationState();
            this.performExplore();
            return;
        }

        Vec3 targetCenter = Vec3.atCenterOf(target);
        this.setForcedLookTarget(targetCenter, 10);

        if (this.shouldJumpToward(target)) {
            this.performAction(AIPlayerAction.JUMP);
        }

        if (this.canHarvestFromHere(target)) {
            if (this.harvestBlock(target, woodTask)) {
                if (woodTask) {
                    this.rememberedLog = null;
                } else {
                    this.rememberedOre = null;
                }
                this.scanSurroundings();
            }
            return;
        }

        BlockPos approach = this.findApproachPosition(target);
        BlockPos navigationGoal = approach != null ? approach : this.findWalkablePositionNear(target, 2, 3);
        if (navigationGoal == null) {
            navigationGoal = target;
        }

        if (!this.navigateToPosition(navigationGoal, 1.05D)) {
            this.lastObservation = "目标路径受阻，正在重新规划@" + this.formatPos(target);
            if (woodTask) {
                this.rememberedLog = null;
            } else {
                this.rememberedOre = null;
            }
            this.getNavigation().stop();
            this.resetNavigationState();
        }
    }

    private void performExplore() {
        if (this.observedHostile != null && this.distanceToSqr(this.observedHostile) <= 49.0D) {
            this.setTarget(this.observedHostile);
            return;
        }

        if (this.getTarget() != null && this.getTarget().isAlive()) {
            return;
        }

        if (this.tickCount % 40 == 0 && (this.getNavigation().isDone() || this.activeNavigationTarget == null)) {
            BlockPos destination = this.findExplorationDestination();
            if (destination != null && this.navigateToPosition(destination, 1.0D)) {
                if (this.lastExploreRecord == null || this.lastExploreRecord.distSqr(destination) > 64.0D) {
                    this.lastExploreRecord = destination;
                    this.remember("探索", "记录位置@" + this.formatPos(destination));
                }
            } else {
                this.lastObservation = "暂时找不到可到达的探索路径。";
            }
        }
    }

    private void performBuildShelter() {
        if (this.observedHostile != null && this.distanceToSqr(this.observedHostile) <= 36.0D) {
            this.setTarget(this.observedHostile);
            return;
        }

        if (this.countAvailableBuildingUnits() < 12) {
            this.lastObservation = "建材不足，先去砍树。";
            this.performHarvestTask(true);
            return;
        }

        if (this.shelterAnchor == null) {
            this.shelterAnchor = this.blockPosition();
            this.remember("建造", "选定避难所中心@" + this.formatPos(this.shelterAnchor));
        }

        BlockPos nextPlacement = this.findNextShelterPlacement();
        if (nextPlacement == null) {
            this.getNavigation().stop();
            this.resetNavigationState();
            this.lastObservation = "简易避难所已完成@" + this.formatPos(this.shelterAnchor);
            return;
        }

        this.setForcedLookTarget(Vec3.atCenterOf(nextPlacement), 10);

        if (this.distanceToSqr(Vec3.atCenterOf(nextPlacement)) > 9.0D) {
            BlockPos approach = this.findApproachPosition(nextPlacement);
            BlockPos navigationGoal = approach != null ? approach : this.findWalkablePositionNear(nextPlacement, 2, 3);
            if (navigationGoal != null && this.navigateToPosition(navigationGoal, 1.0D)) {
                return;
            }

            this.lastObservation = "建造点路径受阻，正在重新规划@" + this.formatPos(nextPlacement);
            this.getNavigation().stop();
            this.resetNavigationState();
            return;
        }

        this.placeShelterBlock(nextPlacement);
    }

    private void performSurvive() {
        if (this.observedHostile != null && this.distanceToSqr(this.observedHostile) <= 64.0D) {
            this.setTarget(this.observedHostile);
            return;
        }

        if (this.manageNearbyFarm()) {
            return;
        }

        if ((this.level().getDayTime() % 24000L) >= 12500L && this.countAvailableBuildingUnits() >= BlueprintRegistry.get(this.activeBlueprintId).requiredUnits()) {
            this.performBuildShelter();
            return;
        }

        if (this.countAvailableBuildingUnits() < BlueprintRegistry.get(this.activeBlueprintId).requiredUnits() && this.rememberedLog != null) {
            this.performHarvestTask(true);
            return;
        }

        if (this.rememberedOre != null && this.getUsedBackpackSlots() < BACKPACK_SIZE - 2) {
            this.performHarvestTask(false);
            return;
        }

        ServerPlayer owner = this.getOwnerPlayer();
        if (owner != null) {
            this.performFollow(true);
        } else {
            this.performExplore();
        }
    }

    private boolean tryCraftBread() {
        while (this.countBackpackItem(Items.BREAD) < 4 && this.countBackpackItem(Items.WHEAT) >= 3) {
            this.consumeBackpackItem(Items.WHEAT, 3);
            this.storeInBackpack(new ItemStack(Items.BREAD));
            this.lastObservation = "已自动合成面包。";
            this.remember("合成", "制作面包");
            return true;
        }
        return false;
    }

    private boolean tryCraftStoneTool(Item toolItem, String toolName) {
        if (this.countBackpackItem(toolItem) > 0) {
            return false;
        }
        if (this.countBackpackItem(Items.COBBLESTONE) < 3) {
            return false;
        }
        if (!this.ensurePlanksCount(2)) {
            return false;
        }
        if (this.countBackpackItem(Items.STICK) < 2) {
            this.consumeBackpackItem(Items.OAK_PLANKS, 2);
            this.storeInBackpack(new ItemStack(Items.STICK, 4));
            this.remember("合成", "制作面包");
        }
        if (this.countBackpackItem(Items.STICK) < 2) {
            return false;
        }
        this.consumeBackpackItem(Items.COBBLESTONE, 3);
        this.consumeBackpackItem(Items.STICK, 2);
        this.storeInBackpack(new ItemStack(toolItem));
        this.remember("合成", "制作" + toolName);
        return true;
    }

    private boolean ensurePlanksCount(int minimum) {
        while (this.countBackpackItem(Items.OAK_PLANKS) < minimum) {
            if (!this.ensurePlanksAvailable()) {
                return false;
            }
        }
        return true;
    }

    private boolean manageNearbyFarm() {
        BlockPos matureCrop = this.findNearestMatureCrop();
        if (matureCrop != null) {
            if (this.distanceToSqr(Vec3.atCenterOf(matureCrop)) > 9.0D) {
                BlockPos approach = this.findApproachPosition(matureCrop);
                return approach != null && this.navigateToPosition(approach, 1.0D);
            }
            return this.harvestCrop(matureCrop);
        }

        BlockPos farmland = this.findPlantableFarmland();
        if (farmland != null && this.hasAnyCropSeed()) {
            if (this.distanceToSqr(Vec3.atCenterOf(farmland)) > 9.0D) {
                BlockPos approach = this.findApproachPosition(farmland);
                return approach != null && this.navigateToPosition(approach, 1.0D);
            }
            return this.plantCrop(farmland);
        }

        return false;
    }

    private BlockPos findNearestMatureCrop() {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos center = this.blockPosition();
        for (int x = -8; x <= 8; x++) {
            for (int y = -2; y <= 3; y++) {
                for (int z = -8; z <= 8; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = this.level().getBlockState(pos);
                    if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
                        continue;
                    }
                    double distance = this.distanceToSqr(Vec3.atCenterOf(pos));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private BlockPos findPlantableFarmland() {
        if (!this.hasAnyCropSeed()) {
            return null;
        }
        BlockPos center = this.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -8; x <= 8; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -8; z <= 8; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState floor = this.level().getBlockState(pos);
                    if (!floor.is(Blocks.FARMLAND)) {
                        continue;
                    }
                    if (!this.level().getBlockState(pos.above()).isAir()) {
                        continue;
                    }
                    double distance = this.distanceToSqr(Vec3.atCenterOf(pos.above()));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.above();
                    }
                }
            }
        }
        return best;
    }

    private boolean harvestCrop(BlockPos pos) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!serverLevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return false;
        }

        BlockState state = serverLevel.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
            return false;
        }

        this.facePosition(Vec3.atCenterOf(pos));
        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, serverLevel.getBlockEntity(pos), this, this.getMainHandItem().copy());
        serverLevel.levelEvent(2001, pos, Block.getId(state));
        serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        for (ItemStack stack : drops) {
            ItemStack remainder = this.storeInBackpack(stack.copy());
            if (!remainder.isEmpty()) {
                this.spawnAtLocation(serverLevel, remainder);
            }
        }

        Item seed = this.getSeedForCrop(state);
        if (seed != Items.AIR && this.consumeBackpackItem(seed, 1)) {
            serverLevel.setBlock(pos, crop.getStateForAge(0), 3);
        }
        this.lastObservation = "已收割作物@" + this.formatPos(pos);
        this.remember("农场", this.lastObservation);
        return true;
    }

    private boolean plantCrop(BlockPos pos) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!serverLevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return false;
        }
        if (!serverLevel.getBlockState(pos).isAir()) {
            return false;
        }

        if (this.consumeBackpackItem(Items.WHEAT_SEEDS, 1)) {
            serverLevel.setBlock(pos, Blocks.WHEAT.defaultBlockState(), 3);
        } else if (this.consumeBackpackItem(Items.CARROT, 1)) {
            serverLevel.setBlock(pos, Blocks.CARROTS.defaultBlockState(), 3);
        } else if (this.consumeBackpackItem(Items.POTATO, 1)) {
            serverLevel.setBlock(pos, Blocks.POTATOES.defaultBlockState(), 3);
        } else if (this.consumeBackpackItem(Items.BEETROOT_SEEDS, 1)) {
            serverLevel.setBlock(pos, Blocks.BEETROOTS.defaultBlockState(), 3);
        } else {
            return false;
        }

        this.lastObservation = "已补种农作物@" + this.formatPos(pos);
        this.remember("农场", this.lastObservation);
        return true;
    }

    private boolean hasAnyCropSeed() {
        return this.countBackpackItem(Items.WHEAT_SEEDS) > 0
                || this.countBackpackItem(Items.CARROT) > 0
                || this.countBackpackItem(Items.POTATO) > 0
                || this.countBackpackItem(Items.BEETROOT_SEEDS) > 0;
    }

    private Item getSeedForCrop(BlockState state) {
        if (state.is(Blocks.WHEAT)) {
            return Items.WHEAT_SEEDS;
        }
        if (state.is(Blocks.CARROTS)) {
            return Items.CARROT;
        }
        if (state.is(Blocks.POTATOES)) {
            return Items.POTATO;
        }
        if (state.is(Blocks.BEETROOTS)) {
            return Items.BEETROOT_SEEDS;
        }
        return Items.AIR;
    }

    private void scanSurroundings() {
        AABB scanBox = this.getBoundingBox().inflate(SCAN_RADIUS);

        LivingEntity previousHostile = this.observedHostile;
        this.observedHostile = this.level().getEntitiesOfClass(LivingEntity.class, scanBox, entity -> entity != this
                        && entity.isAlive()
                        && entity instanceof Enemy
                        && !(entity instanceof AIPlayerEntity))
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);

        this.rememberedLog = this.isValidHarvestTarget(this.rememberedLog, true) ? this.rememberedLog : this.findNearestHarvestBlock(true);
        this.rememberedOre = this.isValidHarvestTarget(this.rememberedOre, false) ? this.rememberedOre : this.findNearestHarvestBlock(false);

        int nearbyPlayers = this.level().getEntitiesOfClass(Player.class, scanBox, player -> player.isAlive() && !player.isSpectator()).size();
        List<String> notices = new ArrayList<>();

        notices.add("附近玩家 " + nearbyPlayers + " 名");
        if (this.observedHostile != null) {
            notices.add("敌对生物：" + this.observedHostile.getName().getString());
            if (previousHostile == null || !previousHostile.equals(this.observedHostile)) {
                this.remember("威胁", "发现敌对生物 " + this.observedHostile.getName().getString());
            }
        }
        if (this.rememberedLog != null) {
            notices.add("木头@" + this.formatPos(this.rememberedLog));
        }
        if (this.rememberedOre != null) {
            notices.add("矿石@" + this.formatPos(this.rememberedOre));
        }

        this.lastObservation = String.join("，", notices);
        this.markPersistentDirty();
    }

    private boolean isValidHarvestTarget(BlockPos pos, boolean woodTask) {
        if (pos == null) {
            return false;
        }

        BlockState state = this.level().getBlockState(pos);
        return woodTask ? state.is(BlockTags.LOGS) : this.isInterestingOre(state) && this.isExposed(pos);
    }

    private BlockPos findNearestHarvestBlock(boolean woodTask) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos origin = this.blockPosition();

        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int y = -SCAN_VERTICAL_DOWN; y <= SCAN_VERTICAL_UP; y++) {
                for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    BlockState state = this.level().getBlockState(cursor);
                    boolean matches = woodTask ? state.is(BlockTags.LOGS) : this.isInterestingOre(state) && this.isExposed(cursor);
                    if (!matches) {
                        continue;
                    }

                    double distance = this.distanceToSqr(Vec3.atCenterOf(cursor));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = cursor.immutable();
                    }
                }
            }
        }

        return bestPos;
    }

    private boolean canHarvestFromHere(BlockPos target) {
        Vec3 eyes = this.getEyePosition();
        Vec3 center = Vec3.atCenterOf(target);
        double dx = center.x - eyes.x;
        double dy = center.y - eyes.y;
        double dz = center.z - eyes.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return distance <= HARVEST_REACH && horizontal <= 2.75D;
    }

    private boolean shouldJumpToward(BlockPos target) {
        Vec3 center = Vec3.atCenterOf(target);
        double horizontal = Math.sqrt(Math.pow(center.x - this.getX(), 2) + Math.pow(center.z - this.getZ(), 2));
        return this.jumpCooldown <= 0
                && this.onGround()
                && center.y > this.getEyeY() + 0.8D
                && horizontal <= 1.8D;
    }

    private BlockPos findApproachPosition(BlockPos target) {
        List<BlockPos> candidates = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            candidates.add(target.relative(direction));
            candidates.add(target.relative(direction).below());
            candidates.add(target.relative(direction).below().below());
        }
        candidates.add(target.below());

        return candidates.stream()
                .filter(this::canStandAt)
                .min(Comparator.comparingDouble(pos -> this.distanceToSqr(Vec3.atCenterOf(pos))))
                .orElse(null);
    }

    private boolean canStandAt(BlockPos pos) {
        BlockState feet = this.level().getBlockState(pos);
        BlockState head = this.level().getBlockState(pos.above());
        BlockState floor = this.level().getBlockState(pos.below());
        return feet.isAir() && head.isAir() && !floor.isAir();
    }

    private BlockPos findWalkablePositionNear(BlockPos center, int horizontalRadius, int verticalRadius) {
        BlockPos bestPos = null;
        double bestScore = Double.MAX_VALUE;

        for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
            for (int y = -verticalRadius; y <= verticalRadius; y++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    BlockPos candidate = center.offset(x, y, z);
                    if (!this.canStandAt(candidate)) {
                        continue;
                    }

                    double targetDistance = candidate.distSqr(center);
                    double selfDistance = this.distanceToSqr(Vec3.atCenterOf(candidate));
                    double score = targetDistance * 2.0D + selfDistance;
                    if (score < bestScore) {
                        bestScore = score;
                        bestPos = candidate;
                    }
                }
            }
        }

        return bestPos;
    }

    private BlockPos findExplorationDestination() {
        for (int attempt = 0; attempt < 12; attempt++) {
            int offsetX = this.random.nextIntBetweenInclusive(-12, 12);
            int offsetZ = this.random.nextIntBetweenInclusive(-12, 12);
            if (Math.abs(offsetX) + Math.abs(offsetZ) < 4) {
                continue;
            }

            BlockPos roughTarget = this.blockPosition().offset(offsetX, 0, offsetZ);
            BlockPos destination = this.findWalkablePositionNear(roughTarget, 3, 4);
            if (destination != null) {
                return destination;
            }
        }

        return this.findWalkablePositionNear(this.blockPosition().offset(6, 0, 0), 6, 4);
    }

    private boolean harvestBlock(BlockPos pos, boolean woodTask) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!serverLevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
            this.lastObservation = "服务器关闭了生物破坏方块，无法执行采集任务。";
            return false;
        }

        BlockState state = serverLevel.getBlockState(pos);
        if (!this.isValidHarvestTarget(pos, woodTask)) {
            return false;
        }

        this.facePosition(Vec3.atCenterOf(pos));
        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        ItemStack tool = this.getMainHandItem().copy();
        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, blockEntity, this, tool);

        serverLevel.levelEvent(2001, pos, Block.getId(state));
        serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);

        for (ItemStack stack : drops) {
            ItemStack remainder = this.storeInBackpack(stack.copy());
            if (!remainder.isEmpty()) {
                this.spawnAtLocation(serverLevel, remainder);
            }
        }

        this.lastObservation = (woodTask ? "已采集木头@" : "已采集矿石@") + this.formatPos(pos);
        this.remember("资源", this.lastObservation);
        return true;
    }

    private boolean placeShelterBlock(BlockPos pos) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!serverLevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
            this.lastObservation = "服务器关闭了生物放置方块，无法执行建造任务。";
            return false;
        }
        if (!serverLevel.getBlockState(pos).isAir()) {
            return false;
        }
        if (!this.ensurePlanksAvailable()) {
            this.lastObservation = "缺少木板，无法继续建造。";
            return false;
        }

        serverLevel.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        this.consumeBackpackItem(Items.OAK_PLANKS, 1);
        this.lastObservation = "已放置木板@" + this.formatPos(pos);
        this.remember("建造", this.lastObservation);
        return true;
    }

    private BlockPos findNextShelterPlacement() {
        if (this.shelterAnchor == null) {
            return null;
        }

        BlueprintTemplate template = BlueprintRegistry.get(this.activeBlueprintId);
        int teamSize = AITaskCoordinator.getTeamSize(this, AIPlayerMode.BUILD_SHELTER, 24.0D);
        int slot = AITaskCoordinator.getTeamSlot(this, AIPlayerMode.BUILD_SHELTER, 24.0D);

        for (int index = 0; index < template.placements().size(); index++) {
            if (teamSize > 1 && index % teamSize != slot) {
                continue;
            }
            BlockPos pos = template.placements().get(index).resolve(this.shelterAnchor);
            if (this.level().getBlockState(pos).isAir()) {
                return pos;
            }
        }

        for (BlueprintTemplate.Placement placement : template.placements()) {
            BlockPos pos = placement.resolve(this.shelterAnchor);
            if (this.level().getBlockState(pos).isAir()) {
                return pos;
            }
        }

        return null;
    }

    private boolean ensurePlanksAvailable() {
        if (this.countBackpackItem(Items.OAK_PLANKS) > 0) {
            return true;
        }

        for (int index = 0; index < this.backpack.size(); index++) {
            ItemStack stack = this.backpack.get(index);
            if (stack.isEmpty() || !this.isLogItem(stack)) {
                continue;
            }

            stack.shrink(1);
            if (stack.isEmpty()) {
                this.backpack.set(index, ItemStack.EMPTY);
            }
            this.storeInBackpack(new ItemStack(Items.OAK_PLANKS, 4));
            return true;
        }

        return false;
    }

    private boolean isLogItem(ItemStack stack) {
        Item item = stack.getItem();
        if (!(item instanceof BlockItem blockItem)) {
            return false;
        }
        return blockItem.getBlock().defaultBlockState().is(BlockTags.LOGS);
    }

    private int countAvailableBuildingUnits() {
        int total = this.countBackpackItem(Items.OAK_PLANKS);
        for (ItemStack stack : this.backpack) {
            if (this.isLogItem(stack)) {
                total += stack.getCount() * 4;
            }
        }
        return total;
    }

    private int countBackpackItem(Item item) {
        int total = 0;
        for (ItemStack stack : this.backpack) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private boolean consumeBackpackItem(Item item, int count) {
        int remaining = count;
        for (int index = 0; index < this.backpack.size(); index++) {
            ItemStack stack = this.backpack.get(index);
            if (stack.isEmpty() || stack.getItem() != item) {
                continue;
            }

            int removable = Math.min(stack.getCount(), remaining);
            stack.shrink(removable);
            this.markPersistentDirty();
            remaining -= removable;
            if (stack.isEmpty()) {
                this.backpack.set(index, ItemStack.EMPTY);
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isInterestingOre(BlockState state) {
        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null && (key.getPath().endsWith("_ore") || key.getPath().contains("ancient_debris"));
    }

    private boolean isExposed(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (this.level().isEmptyBlock(pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    private ServerPlayer getOwnerPlayer() {
        if (this.ownerId == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Player player = serverLevel.getPlayerByUUID(this.ownerId);
        return player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    private void setForcedLookTarget(Vec3 target, int ticks) {
        this.forcedLookTarget = target;
        this.lookTicks = Math.max(this.lookTicks, ticks);
    }

    private void lookAtPitch(float pitch, int ticks) {
        float clampedPitch = Math.max(-85.0F, Math.min(85.0F, pitch));
        this.setXRot(clampedPitch);
        this.lookTicks = Math.max(this.lookTicks, ticks);
        this.forcedLookTarget = null;
    }

    private void facePosition(Vec3 target) {
        Vec3 eyes = this.getEyePosition();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, horizontal)));
        pitch = Math.max(-85.0F, Math.min(85.0F, pitch));

        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
        this.setXRot(pitch);
    }

    private void ensurePersistentStateLoaded() {
        if (this.persistentStateLoaded) {
            return;
        }

        CompoundTag tag = this.getPersistentData();
        this.persistentStateLoaded = true;
        if (!tag.getBoolean("AIPlayersSaved").orElse(false)) {
            return;
        }

        String storedName = tag.getString("AIName").orElse("");
        if (!storedName.isBlank()) {
            this.aiName = storedName;
        }

        String storedMode = tag.getString("Mode").orElse("");
        AIPlayerMode restoredMode = storedMode.isBlank() ? AIPlayerMode.IDLE : AIPlayerMode.fromCommand(storedMode);
        this.mode = restoredMode == null ? AIPlayerMode.IDLE : restoredMode;


        String storedOwner = tag.getString("OwnerId").orElse("");
        if (!storedOwner.isBlank()) {
            try {
                this.ownerId = UUID.fromString(storedOwner);
            } catch (IllegalArgumentException ignored) {
                this.ownerId = null;
            }
        } else {
            this.ownerId = null;
        }

        String storedObservation = tag.getString("LastObservation").orElse("");
        if (!storedObservation.isBlank()) {
            this.lastObservation = storedObservation;
        }

        this.rememberedLog = this.readBlockPos(tag, "RememberedLog");
        this.rememberedOre = this.readBlockPos(tag, "RememberedOre");
        this.shelterAnchor = this.readBlockPos(tag, "ShelterAnchor");
        this.lastExploreRecord = this.readBlockPos(tag, "LastExploreRecord");

        this.deserializeMemory(tag.getString("MemoryData").orElse(""));
        this.deserializeBackpack(tag.getString("BackpackData").orElse(""));
        this.activeBlueprintId = tag.getString("BlueprintId").orElse(DEFAULT_BLUEPRINT_ID);
        this.applyModeEquipment();
        this.refreshDisplayName();
        this.syncClientState();
        this.persistentStateDirty = false;
    }

    private void syncPersistentState() {
        CompoundTag tag = this.getPersistentData();
        tag.putBoolean("AIPlayersSaved", true);
        tag.putString("AIName", this.aiName);
        tag.putString("Mode", this.safeModeCommandName());
        tag.putString("OwnerId", this.ownerId == null ? "" : this.ownerId.toString());
        tag.putString("LastObservation", this.lastObservation == null ? "" : this.lastObservation);
        tag.putString("MemoryData", this.serializeMemory());
        tag.putString("BackpackData", this.serializeBackpack());
        tag.putString("BlueprintId", this.activeBlueprintId);
        this.writeBlockPos(tag, "RememberedLog", this.rememberedLog);
        this.writeBlockPos(tag, "RememberedOre", this.rememberedOre);
        this.writeBlockPos(tag, "ShelterAnchor", this.shelterAnchor);
        this.writeBlockPos(tag, "LastExploreRecord", this.lastExploreRecord);
        this.persistentStateDirty = false;
    }

    private void markPersistentDirty() {
        if (!this.level().isClientSide()) {
            this.persistentStateDirty = true;
        }
    }

    private void writeBlockPos(CompoundTag tag, String key, BlockPos pos) {
        if (pos == null) {
            tag.putIntArray(key, new int[0]);
            return;
        }
        tag.putIntArray(key, new int[] {pos.getX(), pos.getY(), pos.getZ()});
    }

    private BlockPos readBlockPos(CompoundTag tag, String key) {
        int[] data = tag.getIntArray(key).orElse(new int[0]);
        return data.length == 3 ? new BlockPos(data[0], data[1], data[2]) : null;
    }

    private String serializeMemory() {
        return String.join("\n", this.memory);
    }

    private void deserializeMemory(String encoded) {
        this.memory.clear();
        if (encoded == null || encoded.isBlank()) {
            return;
        }

        String[] lines = encoded.split("\n");
        int start = Math.max(0, lines.length - MEMORY_LIMIT);
        for (int index = start; index < lines.length; index++) {
            if (!lines[index].isBlank()) {
                this.memory.add(lines[index]);
            }
        }
    }

    private String serializeBackpack() {
        List<String> entries = new ArrayList<>();
        for (int index = 0; index < this.backpack.size(); index++) {
            ItemStack stack = this.backpack.get(index);
            if (stack.isEmpty()) {
                continue;
            }

            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId != null) {
                entries.add(index + "|" + itemId + "|" + stack.getCount());
            }
        }
        return String.join(";", entries);
    }

    private void deserializeBackpack(String encoded) {
        for (int slot = 0; slot < this.backpack.size(); slot++) {
            this.backpack.set(slot, ItemStack.EMPTY);
        }

        if (encoded == null || encoded.isBlank()) {
            return;
        }

        for (String entry : encoded.split(";")) {
            if (entry.isBlank()) {
                continue;
            }

            String[] parts = entry.split("\\|");
            if (parts.length != 3) {
                continue;
            }

            try {
                int slot = Integer.parseInt(parts[0]);
                if (slot < 0 || slot >= this.backpack.size()) {
                    continue;
                }

                Identifier itemId = Identifier.tryParse(parts[1]);
                if (itemId == null) {
                    continue;
                }

                Item item = BuiltInRegistries.ITEM.get(itemId).map(reference -> reference.value()).orElse(Items.AIR);
                if (item == Items.AIR) {
                    continue;
                }

                int count = Math.max(1, Integer.parseInt(parts[2]));
                this.backpack.set(slot, new ItemStack(item, count));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private String describePlanTarget(BlockPos pos, String fallback) {
        return pos == null ? fallback : fallback + "@" + this.formatPos(pos);
    }

    private void populateStarterKit() {
        for (int slot = 0; slot < this.backpack.size(); slot++) {
            this.backpack.set(slot, ItemStack.EMPTY);
        }
        this.storeInBackpack(new ItemStack(Items.STONE_AXE));
        this.storeInBackpack(new ItemStack(Items.STONE_PICKAXE));
        this.storeInBackpack(new ItemStack(Items.IRON_SWORD));
        this.storeInBackpack(new ItemStack(Items.BREAD, 8));
        this.applyModeEquipment();
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        this.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
    }

    private void applyModeEquipment() {
        ItemStack tool = switch (this.safeMode()) {
            case GATHER_WOOD -> new ItemStack(Items.STONE_AXE);
            case MINE -> new ItemStack(Items.STONE_PICKAXE);
            case BUILD_SHELTER -> new ItemStack(Items.STONE_AXE);
            default -> new ItemStack(Items.IRON_SWORD);
        };

        this.setItemSlot(EquipmentSlot.MAINHAND, tool);
        this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        this.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
        this.setDropChance(EquipmentSlot.HEAD, 0.0F);
        this.setDropChance(EquipmentSlot.CHEST, 0.0F);
        this.setDropChance(EquipmentSlot.LEGS, 0.0F);
        this.setDropChance(EquipmentSlot.FEET, 0.0F);
    }

    private ItemStack storeInBackpack(ItemStack stack) {
        ItemStack remaining = stack;

        for (int index = 0; index < this.backpack.size(); index++) {
            ItemStack existing = this.backpack.get(index);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)) {
                continue;
            }

            int transferable = Math.min(existing.getMaxStackSize() - existing.getCount(), remaining.getCount());
            if (transferable <= 0) {
                continue;
            }

            existing.grow(transferable);
            this.markPersistentDirty();
            remaining.shrink(transferable);
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }

        for (int index = 0; index < this.backpack.size(); index++) {
            if (!this.backpack.get(index).isEmpty()) {
                continue;
            }

            this.backpack.set(index, remaining.copy());
            this.markPersistentDirty();
            return ItemStack.EMPTY;
        }

        return remaining;
    }

    private void remember(String type, String detail) {
        String entry = type + "：" + detail;
        this.memory.add(entry);
        if (this.memory.size() > MEMORY_LIMIT) {
            this.memory.remove(0);
        }
        this.markPersistentDirty();
    }

    private int getUsedBackpackSlots() {
        int used = 0;
        for (ItemStack stack : this.backpack) {
            if (!stack.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    private String getInventoryPreview() {
        if (this.getUsedBackpackSlots() == 0) {
            return "空";
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : this.backpack) {
            if (stack.isEmpty()) {
                continue;
            }
            counts.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
        }

        List<String> preview = new ArrayList<>();
        int shown = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            preview.add(entry.getKey() + "x" + entry.getValue());
            shown++;
            if (shown >= 4) {
                break;
            }
        }

        if (counts.size() > shown) {
            preview.add("...");
        }

        return String.join("、", preview);
    }

    private String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private AIPlayerMode safeMode() {
        if (this.mode == null) {
            this.mode = AIPlayerMode.IDLE;
        }
        return this.mode;
    }

    private String safeModeCommandName() {
        return this.safeMode().commandName();
    }

    private String safeModeDisplayName() {
        return this.safeMode().displayName();
    }

    private void refreshDisplayName() {
        this.entityData.set(DATA_AI_NAME, this.aiName);
        this.entityData.set(DATA_MODE, this.safeModeCommandName());
        this.entityData.set(DATA_BLUEPRINT, this.activeBlueprintId);
        this.setCustomName(Component.literal(this.aiName + " [" + this.safeModeDisplayName() + "]"));
        this.setCustomNameVisible(true);
    }

    private void syncClientState() {
        this.entityData.set(DATA_AI_NAME, this.aiName);
        this.entityData.set(DATA_OWNER_ID, this.ownerId == null ? "" : this.ownerId.toString());
        this.entityData.set(DATA_MODE, this.safeModeCommandName());
        this.entityData.set(DATA_BLUEPRINT, this.activeBlueprintId);
    }

    public String getOwnerIdString() {
        String synced = this.entityData.get(DATA_OWNER_ID);
        if (synced != null && !synced.isBlank()) {
            return synced;
        }
        return this.ownerId == null ? "" : this.ownerId.toString();
    }

    public BlockPos getSharedBuildAnchor() {
        return this.shelterAnchor;
    }

    public String getActiveBlueprintId() {
        String synced = this.entityData.get(DATA_BLUEPRINT);
        return synced == null || synced.isBlank() ? this.activeBlueprintId : synced;
    }

    public String getClientModeName() {
        String synced = this.entityData.get(DATA_MODE);
        return synced == null || synced.isBlank() ? this.safeModeCommandName() : synced;
    }

    public void selectBlueprint(String blueprintId) {
        this.activeBlueprintId = BlueprintRegistry.get(blueprintId).id();
        this.entityData.set(DATA_BLUEPRINT, this.activeBlueprintId);
        this.shelterAnchor = null;
        if (!this.level().isClientSide()) {
            AILongTermMemoryStore.updateNote(this, "blueprint", this.activeBlueprintId);
        }
        this.markPersistentDirty();
    }

    public String getLongTermMemorySummary() {
        return this.level().isClientSide() ? "客户端未加载" : AILongTermMemoryStore.getSummary(this);
    }

    private void setGoalNote(String goal) {
        if (!this.level().isClientSide()) {
            AILongTermMemoryStore.updateNote(this, "goal", goal);
        }
    }

    private String detectBlueprintPreference(String content) {
        if (content == null) {
            return this.activeBlueprintId;
        }
        String normalized = content.toLowerCase();
        if (normalized.contains("塔") || normalized.contains("tower")) {
            return "watchtower";
        }
        if (normalized.contains("屋") || normalized.contains("房") || normalized.contains("house") || normalized.contains("cabin")) {
            return "cabin";
        }
        return "shelter";
    }

    private String contentSafe(ServerPlayer speaker, ChatIntent intent) {
        return intent.name() + (speaker == null ? "" : ("@" + speaker.getName().getString()));
    }
}


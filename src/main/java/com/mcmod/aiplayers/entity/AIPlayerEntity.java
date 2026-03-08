package com.mcmod.aiplayers.entity;

import com.mcmod.aiplayers.AIPlayersMod;
import com.mcmod.aiplayers.ai.AIServiceManager;
import com.mcmod.aiplayers.ai.AIServiceResponse;
import com.mcmod.aiplayers.ai.AITaskPlanResponse;
import com.mcmod.aiplayers.knowledge.KnowledgeManager;
import com.mcmod.aiplayers.menu.AIPlayerBackpackMenu;
import com.mcmod.aiplayers.registry.ModEntities;
import com.mcmod.aiplayers.system.AILongTermMemoryStore;
import com.mcmod.aiplayers.system.AIAgentPipeline;
import com.mcmod.aiplayers.system.AIAgentPlan;
import com.mcmod.aiplayers.system.AIAgentWorldState;
import com.mcmod.aiplayers.system.AITaskCoordinator;
import com.mcmod.aiplayers.system.BlueprintRegistry;
import com.mcmod.aiplayers.system.BlueprintTemplate;
import com.mcmod.aiplayers.system.ChatIntent;
import com.mcmod.aiplayers.system.ChatIntentParser;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;

public class AIPlayerEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> DATA_AI_NAME = SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_OWNER_ID = SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_MODE = SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_BLUEPRINT = SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_STATUS = SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_GOAL = SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_ACTION = SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_PATH = SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_MEMORY = SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_LLM = SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_PLAN = SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_OBSERVATION = SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_INVENTORY = SynchedEntityData.defineId(AIPlayerEntity.class, EntityDataSerializers.STRING);
    private static final String DEFAULT_AI_NAME = "Companion";
    private static final String DEFAULT_OBSERVATION = "All clear.";
    private static final String DEFAULT_BLUEPRINT_ID = "shelter";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");
    private static final int BACKPACK_SIZE = 36;
    private static final int SCAN_RADIUS = 22;
    private static final int SCAN_VERTICAL_DOWN = 12;
    private static final int SCAN_VERTICAL_UP = 18;
    private static final int SCAN_INTERVAL = 8;
    private static final int RESOURCE_SCAN_INTERVAL = 30;
    private static final int UTILITY_SCAN_INTERVAL = 40;
    private static final int CROP_SCAN_INTERVAL = 30;
    private static final int TELEMETRY_SYNC_INTERVAL = 10;
    private static final int MEMORY_LIMIT = 12;
    private static final int NAVIGATION_STUCK_THRESHOLD = 60;
    private static final double HARVEST_REACH = 4.5D;
    private static final double RECOVERY_REACH = 3.5D;
    private static final int HARVEST_TASK_STALE_TICKS = 200;
    private static final int HARVEST_TASK_LOCK_TICKS = 150;
    private static final double FAST_FOLLOW_SPEED = 1.35D;
    private static final double NORMAL_FOLLOW_SPEED = 1.15D;
    private static final double OWNER_TELEPORT_FOLLOW_DISTANCE_SQR = 30.0D * 30.0D;
    private static final double FAST_FOLLOW_DISTANCE_SQR = 12.0D * 12.0D;
    private static final double TELEPORT_FOLLOW_DISTANCE_SQR = 32.0D * 32.0D;
    private static final int ALERT_COOLDOWN_TICKS = 120;
    private static final float WASD_MAX_YAW_STEP = 36.0F;
    private static final double LOCAL_BLOCKER_CHECK_DISTANCE = HARVEST_REACH + 1.75D;
    private static final double HUNT_SCAN_RANGE = 28.0D;
    private static final int AWARENESS_BROADCAST_INTERVAL = 20 * 12;
    private static final int DEBUG_STATUS_BROADCAST_INTERVAL = 20 * 3;

    private final NonNullList<ItemStack> backpack = NonNullList.withSize(BACKPACK_SIZE, ItemStack.EMPTY);
    private final List<String> memory = new ArrayList<>();
    private final AgentRuntime agentRuntime;

    private AIPlayerMode mode = AIPlayerMode.IDLE;
    private UUID ownerId;
    private String aiName = DEFAULT_AI_NAME;
    private String lastObservation = "周围一切正常。";
    private BlockPos rememberedLog;
    private BlockPos rememberedOre;
    private BlockPos rememberedBed;
    private BlockPos rememberedChest;
    private BlockPos rememberedCraftingTable;
    private BlockPos rememberedFurnace;
    private BlockPos rememberedCrop;
    private LivingEntity observedHostile;
    private ItemEntity observedDrop;
    private BlockPos shelterAnchor;
    private BlockPos lastExploreRecord;
    private int crouchTicks;
    private int lookTicks;
    private Vec3 forcedLookTarget;
    private int jumpCooldown;
    private int shieldGuardTicks;
    private boolean pendingAiResponse;
    private boolean persistentStateLoaded;
    private boolean persistentStateDirty;
    private BlockPos activeNavigationTarget;
    private Vec3 lastNavigationSample = Vec3.ZERO;
    private int stuckNavigationTicks;
    private double activeNavigationSpeed = NORMAL_FOLLOW_SPEED;
    private String activeBlueprintId = DEFAULT_BLUEPRINT_ID;
    private UUID pendingDeliveryReceiverId;
    private String pendingDeliveryRequest;
    private String latestAgentGoal = "保持警戒";
    private String latestAgentPlanSummary = "观察环境 -> 等待下一步";
    private String latestAgentReasoning = "本地规则";
    private String latestCognitiveSummary = "环境稳定，继续观察";
    private String lastAgentLearnKey = "";
    private int lastOwnerAlertTick;
    private String lastOwnerAlertMessage = "";
    private int autonomousIdleTicks;
    private boolean pendingTaskAiPlan;
    private int nextTaskAiPlanTick;
    private int taskAiPlanValidUntilTick;
    private String taskAiGoal = "";
    private AIPlayerMode taskAiMode;
    private List<String> taskAiSubtasks = new ArrayList<>();
    private String taskAiFallback = "";
    private String activeTaskName = "待命";
    private String lastTaskFeedback = "暂无任务反馈";
    private int taskFailureStreak;
    private int taskSuccessStreak;
    private int lastMeaningfulProgressTick;
    private int lastTaskFeedbackTick;
    private boolean lastTaskFeedbackWasFailure;
    private boolean playerModePinned;
    private AIPlayerMode playerPinnedMode;
    private String lastTaskAiServiceStatus = "待机";
    private int lastResourceScanTick;
    private int lastUtilityScanTick;
    private int lastCropScanTick;
    private int lastDebugStatusTick;
    private int lastTelemetrySyncTick = -TELEMETRY_SYNC_INTERVAL;
    private float pendingWasdForward;
    private float pendingWasdStrafe;
    private float pendingWasdSpeed;
    private float pendingWasdTargetYaw;
    private boolean pendingWasdSprint;
    private boolean pendingWasdJump;
    private boolean pendingWasdJumpQueued;
    private boolean wasdOverrideActive;
    private BlockPos miningTarget;
    private MiningMode miningMode = MiningMode.NONE;
    private int miningProgressTicks;
    private int miningRequiredTicks;
    private int miningLastTick;
    private int miningLastStage = -1;
    private int miningOutOfReachTicks;
    private String miningObservationPrefix = "";
    private HarvestTaskState harvestTaskState = HarvestTaskState.IDLE;
    private boolean harvestTaskWoodMode;
    private BlockPos harvestTaskTarget;
    private BlockPos harvestTaskObstacle;
    private BlockPos harvestTaskMoveTarget;
    private int harvestTaskStateTick;
    private int harvestTaskLastProgressTick;
    private String huntTargetId = "";
    private String huntTargetLabel = "";
    private BlockPos huntLastKnownPos;
    private String lastEnvironmentSignature = "";
    private int nextAwarenessBroadcastTick;
    private boolean pendingAwarenessBroadcast;

    public AIPlayerEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
        this.moveControl = new AIPlayerMoveControl(this);
        this.agentRuntime = new AgentRuntime(this);
        this.getNavigation().setCanFloat(true);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.setPathfindingMalus(PathType.WATER_BORDER, 1.0F);
        this.setPathfindingMalus(PathType.WALKABLE_DOOR, 0.0F);
        this.setPathfindingMalus(PathType.DOOR_OPEN, 0.0F);
        this.setPathfindingMalus(PathType.LEAVES, 0.0F);
        this.xpReward = 0;
        this.refreshDisplayName();
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AIPlayerGroundNavigation(this, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_AI_NAME, DEFAULT_AI_NAME);
        builder.define(DATA_OWNER_ID, "");
        builder.define(DATA_MODE, AIPlayerMode.IDLE.commandName());
        builder.define(DATA_BLUEPRINT, DEFAULT_BLUEPRINT_ID);
        builder.define(DATA_STATUS, "模式：待命 | 生命：20/20 | 背包：0/36");
        builder.define(DATA_GOAL, "待命");
        builder.define(DATA_ACTION, "暂无动作");
        builder.define(DATA_PATH, "路径未启动");
        builder.define(DATA_MEMORY, "暂无记忆");
        builder.define(DATA_LLM, "本地规划");
        builder.define(DATA_PLAN, "暂无计划");
        builder.define(DATA_OBSERVATION, "周围一切正常。");
        builder.define(DATA_INVENTORY, "空");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.BLOCK_BREAK_SPEED)
                .add(Attributes.SUBMERGED_MINING_SPEED)
                .add(Attributes.MINING_EFFICIENCY)
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
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return;
        }
        SoundType soundType = state.getSoundType();
        this.playSound(soundType.getStepSound(), soundType.getVolume() * 0.15F, soundType.getPitch());
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !this.canOpenBackpack(player)) {
            return super.mobInteract(player, hand);
        }
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, openedPlayer) -> AIPlayerBackpackMenu.server(containerId, inventory, this),
                    this.getBackpackMenuTitle()));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void die(DamageSource damageSource) {
        if (this.level().isClientSide()) {
            super.die(damageSource);
            return;
        }

        ServerPlayer owner = this.getOwnerPlayer();
        CompoundTag snapshot = this.createRespawnSnapshot();
        BlockPos deathPos = this.blockPosition();
        BlockPos respawnPos = owner != null
                ? this.findWalkablePositionNear(owner.blockPosition(), 3, 4)
                : this.findWalkablePositionNear(deathPos, 3, 4);
        if (respawnPos == null) {
            respawnPos = owner != null ? owner.blockPosition().above() : deathPos.above();
        }

        super.die(damageSource);
        this.respawnCompanion(snapshot, owner, respawnPos);
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (this.level().isClientSide()) {
            return;
        }

        this.ensurePersistentStateLoaded();
        this.tickActionState();
        this.tickMiningState();
        this.agentRuntime.tickMovement();

        if (AITickScheduler.shouldRunWorldScan(this.tickCount)) {
            this.agentRuntime.tickWorldScan();
        }
        if (AITickScheduler.shouldRunCombat(this.tickCount)) {
            this.agentRuntime.tickCombat();
        }
        if (AITickScheduler.shouldRunPlanner(this.tickCount)) {
            this.agentRuntime.tickPlanner();
        }
        if (AITickScheduler.shouldRunExecutor(this.tickCount)) {
            this.agentRuntime.tickExecutor();
        }

        this.tickNavigationState();
        this.tickEnvironmentAwareness();
        this.tickAdaptiveScanning();
        this.tickDebugStatusBroadcast();

        if (AITickScheduler.shouldSyncTelemetry(this.tickCount)) {
            this.syncClientTelemetry();
        }

        if (this.persistentStateDirty && AITickScheduler.shouldFlushPersistentState(this.tickCount)) {
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

    public boolean canOpenBackpack(Player player) {
        if (player == null || !player.isAlive() || this.isRemoved()) {
            return false;
        }
        if (player.distanceToSqr(this) > 64.0D) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            return this.canReceiveOrdersFrom(serverPlayer);
        }
        return this.ownerId == null || this.ownerId.equals(player.getUUID());
    }

    public int getBackpackSize() {
        return this.backpack.size();
    }

    public ItemStack getBackpackStack(int slot) {
        return slot >= 0 && slot < this.backpack.size() ? this.backpack.get(slot) : ItemStack.EMPTY;
    }

    public void setBackpackStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.backpack.size()) {
            return;
        }
        this.backpack.set(slot, stack == null ? ItemStack.EMPTY : stack);
        this.markPersistentDirty();
    }

    public ItemStack removeBackpackStack(int slot, int amount) {
        if (slot < 0 || slot >= this.backpack.size() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = this.backpack.get(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed;
        if (existing.getCount() <= amount) {
            removed = existing;
            this.backpack.set(slot, ItemStack.EMPTY);
        } else {
            removed = existing.split(amount);
            if (existing.isEmpty()) {
                this.backpack.set(slot, ItemStack.EMPTY);
            }
        }
        this.markPersistentDirty();
        return removed;
    }

    public ItemStack removeBackpackStackNoUpdate(int slot) {
        if (slot < 0 || slot >= this.backpack.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = this.backpack.get(slot);
        this.backpack.set(slot, ItemStack.EMPTY);
        this.markPersistentDirty();
        return existing;
    }

    public Component getBackpackMenuTitle() {
        return Component.translatable("container.crafting");
    }

    public String startAnimalHunt(ServerPlayer speaker, String targetId, String label) {
        if (speaker != null) {
            this.assignOwner(speaker);
        }
        this.huntTargetId = targetId == null ? "" : targetId;
        this.huntTargetLabel = label == null || label.isBlank() ? this.huntTargetId : label;
        this.huntLastKnownPos = null;
        this.playerModePinned = true;
        this.playerPinnedMode = AIPlayerMode.SURVIVE;
        this.clearTaskAiPlanState();
        this.agentRuntime.applyDirectedGoal(speaker, AgentGoal.of(GoalType.SURVIVE, "player", "\u73a9\u5bb6\u8981\u6c42\u72e9\u730e" + this.huntTargetLabel), true);
        this.latestAgentGoal = "\u72e9\u730e" + this.huntTargetLabel;
        this.setMode(AIPlayerMode.SURVIVE);
        this.remember("\u72e9\u730e", "\u5f00\u59cb\u653b\u51fb\u76ee\u6807\uff1a" + this.huntTargetLabel);
        this.markPersistentDirty();
        return "\u6536\u5230\uff0c\u6211\u4f1a\u4e3b\u52a8\u641c\u7d22\u5e76\u653b\u51fb\u9644\u8fd1\u7684" + this.huntTargetLabel + "\u3002";
    }

    public void onBackpackChanged() {
        this.markPersistentDirty();
    }

    public void applyCommandedMode(ServerPlayer speaker, AIPlayerMode newMode) {
        if (speaker != null) {
            this.applyPlayerDirectedMode(speaker, newMode);
            return;
        }
        this.agentRuntime.applyDirectedGoal(null, AgentGoal.of(GoalType.fromMode(newMode), "command", "slash command"), true);
        this.setMode(newMode);
    }

    public void setMode(AIPlayerMode newMode) {
        AIPlayerMode normalizedMode = newMode == null ? AIPlayerMode.IDLE : newMode;
        this.mode = normalizedMode;
        this.setTarget(null);
        this.getNavigation().stop();
        this.resetNavigationState();
        this.resetMiningState();
        this.resetHarvestTaskState();
        this.applyModeEquipment();
        if (normalizedMode != AIPlayerMode.BUILD_SHELTER) {
            this.shelterAnchor = null;
        }
        this.remember("模式", "切换为 " + normalizedMode.displayName());
        this.refreshDisplayName();
        this.markPersistentDirty();
    }

    private void applyPlayerDirectedMode(ServerPlayer speaker, AIPlayerMode newMode) {
        this.assignOwner(speaker);
        this.playerModePinned = true;
        this.playerPinnedMode = newMode;
        this.clearTaskAiPlanState();
        this.clearHuntDirective();
        this.agentRuntime.applyDirectedGoal(speaker, AgentGoal.of(GoalType.fromMode(newMode), "player", "player command"), true);
        this.setMode(newMode);
    }

    private void clearPlayerModePin() {
        this.playerModePinned = false;
        this.playerPinnedMode = null;
    }

    private void clearTaskAiPlanState() {
        this.pendingTaskAiPlan = false;
        this.taskAiPlanValidUntilTick = 0;
        this.taskAiGoal = "";
        this.taskAiMode = null;
        this.taskAiSubtasks.clear();
        this.taskAiFallback = "";
    }

    public String executeConversation(ServerPlayer speaker, String content) {
        return ConversationRouter.handle(this, speaker, content);
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
                return "你可以让我跟随、护卫、砍树、挖矿、探索、建造、生存，也可以让我跳跃、下蹲、抬头、查看背包、把木头给你、脱困、查看记忆或询问当前计划。";
            }
            case STATUS -> {
                return this.getStatusSummary();
            }
            case INVENTORY -> {
                return "当前背包：" + this.getDetailedInventorySummary();
            }
            case GIVE_ITEM -> {
                return "你可以直接说：把木头给我、给我 16 个原木、把圆石给我。";
            }
            case MEMORY -> {
                return "最近记忆：" + this.getMemorySummary();
            }
            case PLAN -> {
                return "当前规划：" + this.getPlanSummary();
            }
            case FOLLOW -> {
                this.applyPlayerDirectedMode(speaker, AIPlayerMode.FOLLOW);
                return "收到，我开始跟随你。";
            }
            case GUARD -> {
                this.applyPlayerDirectedMode(speaker, AIPlayerMode.GUARD);
                return "收到，我会优先保护你。";
            }
            case GATHER_WOOD -> {
                this.applyPlayerDirectedMode(speaker, AIPlayerMode.GATHER_WOOD);
                return "开始砍树，我会处理上方原木和附近木头。";
            }
            case MINE -> {
                this.applyPlayerDirectedMode(speaker, AIPlayerMode.MINE);
                return "开始挖矿，我会寻找附近矿石，并尝试处理浅层遮挡方块。";
            }
            case EXPLORE -> {
                this.applyPlayerDirectedMode(speaker, AIPlayerMode.EXPLORE);
                return "开始探索，我会在附近巡查并记录位置。";
            }
            case BUILD -> {
                this.applyPlayerDirectedMode(speaker, AIPlayerMode.BUILD_SHELTER);
                return "开始建造简易避难所，我会先补足建材。";
            }
            case SURVIVE -> {
                this.applyPlayerDirectedMode(speaker, AIPlayerMode.SURVIVE);
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
            case RECOVER -> {
                return this.performAction(AIPlayerAction.RECOVER);
            }
            case STOP -> {
                this.clearPlayerModePin();
                this.clearTaskAiPlanState();
                this.clearHuntDirective();
                this.setMode(AIPlayerMode.IDLE);
                return "已停止当前任务，进入待命。";
            }
            case UNKNOWN -> {
                return "我听到了，但没完全理解。你可以说：跟随、护卫、砍树、挖矿、探索、建造、生存、跳跃、下蹲、抬头、背包、把木头给我、脱困、记忆、状态、计划、停止。";
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
            case RECOVER -> {
                if (this.attemptImmediateRecovery()) {
                    this.remember("动作", "执行脱困");
                    return "收到，我正在尝试脱困。";
                }
                return "我暂时没有发现需要处理的卡点。";
            }
        }

        return "动作已接收。";
    }
    public String getStatusSummary() {
        return "模式=" + this.safeModeDisplayName()
                + "；生命=" + Math.round(this.getHealth()) + "/" + Math.round(this.getMaxHealth())
                + "；背包=" + this.getUsedBackpackSlots() + "/" + BACKPACK_SIZE
                + "；物资=" + this.getInventoryPreview()
                + "；目标=" + this.latestAgentGoal
                + "；规划=" + this.getPlanSummary()
                + "；认知=" + this.getCognitiveSummary()
                + "；观察=" + this.getObservationSummary()
                + "；LLM=" + AIServiceManager.getLastStatusText()
                + "；知识库=" + (KnowledgeManager.isInitialized() ? "已加载" : "未加载")
                + "；最近反馈=" + this.lastTaskFeedback;
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
        return this.latestAgentPlanSummary + " | 规划器=" + this.agentRuntime.plannerStatus();
    }

    public String getCognitiveSummary() {
        return this.latestCognitiveSummary == null || this.latestCognitiveSummary.isBlank() ? "环境稳定，继续观察" : this.latestCognitiveSummary;
    }

    public String getTaskFeedbackSummary() {
        return "当前任务=" + this.activeTaskName
                + "；最近反馈=" + this.lastTaskFeedback
                + "；成功连击=" + this.taskSuccessStreak
                + "；失败连击=" + this.taskFailureStreak
                + (this.hasActiveTaskAiPlan() ? "；任务AI目标=" + this.taskAiGoal : "")
                + (this.taskAiFallback != null && !this.taskAiFallback.isBlank() ? "；回退=" + this.taskAiFallback : "")
                + (this.pendingTaskAiPlan ? "；任务AI=重规划中" : "");
    }

    private String applyApiDirective(ServerPlayer speaker, AIServiceResponse response) {
        String result = "我已经处理你的请求。";

        if (response.mode() != null && !response.mode().isBlank() && !"unchanged".equalsIgnoreCase(response.mode())) {
            AIPlayerMode apiMode = AIPlayerMode.fromCommand(response.mode());
            if (apiMode != null) {
                this.applyPlayerDirectedMode(speaker, apiMode);
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

    private String generateLocalConversationReply(ServerPlayer speaker, String content, boolean apiFailed) {
        String normalized = this.normalizeConversationText(content);
        if (containsAnyToken(normalized, "谢谢", "辛苦了", "干得不错", "做得好", "thanks", "thank you", "good job")) {
            return "不客气。我当前在" + this.safeModeDisplayName() + "，目标是“" + this.latestAgentGoal + "”。";
        }
        if (containsAnyToken(normalized, "你在做什么", "你现在在做什么", "你在干嘛", "在忙什么", "what are you doing")) {
            return "我现在在" + this.safeModeDisplayName() + "，目标是“" + this.latestAgentGoal + "”，最近反馈是：“" + this.lastTaskFeedback + "”。";
        }
        if (containsAnyToken(normalized, "你看到了什么", "附近有什么", "周围有什么", "你发现了什么", "what do you see")) {
            return "我目前观察到：" + this.getObservationSummary();
        }
        if (containsAnyToken(normalized, "你需要什么", "还缺什么", "缺什么", "需要帮忙吗", "what do you need")) {
            return this.buildNeedSummary();
        }
        if (containsAnyToken(normalized, "接下来怎么办", "下一步怎么办", "你建议什么", "下一步做什么", "what next", "what should we do")) {
            return "我的建议是：" + this.getPlanSummary();
        }
        if (containsAnyToken(normalized, "自由生存", "自己玩", "自己活动", "自己决定", "autonomous", "be autonomous")) {
            this.assignOwner(speaker);
            this.setMode(AIPlayerMode.SURVIVE);
            return "好，我会切到自主生存模式，并根据环境、记忆和失败反馈持续调整。当前目标是“" + this.latestAgentGoal + "”。";
        }
        if (containsAnyToken(normalized, "聊聊天", "说句话", "和我说话", "聊一下", "talk to me", "say something")) {
            return "我在。当前认知是：" + this.getCognitiveSummary() + "；最近任务反馈是：" + this.lastTaskFeedback;
        }

        String prefix = apiFailed ? "外部对话 AI 当前不可用，我先基于本地认知回答。" : "我先基于当前观察回答。";
        return prefix
                + " 当前目标是“" + this.latestAgentGoal + "”，最近反馈是“" + this.lastTaskFeedback + "”。"
                + " 你也可以直接说：跟随、护卫、砍树、挖矿、探索、建造、生存、跳跃、下蹲、抬头、背包、把木头给我、脱困、记忆、状态、计划、停止。";
    }

    private String buildNeedSummary() {
        List<String> needs = new ArrayList<>();
        if (this.hasLowFoodSupply()) {
            needs.add("食物偏少");
        }
        if (this.hasLowTools()) {
            needs.add("基础工具不足");
        }
        if (this.safeMode() == AIPlayerMode.BUILD_SHELTER && this.countAvailableBuildingUnits() < 12) {
            needs.add("建材不足");
        }
        if (this.getHealth() <= 8.0F) {
            needs.add("血量较低");
        }
        if (needs.isEmpty()) {
            return "我目前物资和状态还算稳定，重点是继续推进“" + this.latestAgentGoal + "”。";
        }
        return "我现在最缺的是：" + String.join("、", needs) + "。";
    }

    public String getEnvironmentSummary() {
        List<String> parts = new ArrayList<>();
        parts.add((this.level().getDayTime() % 24000L) >= 12500L ? "\u591c\u665a" : "\u767d\u5929");
        if (this.level().isThundering()) {
            parts.add("\u96f7\u96e8");
        } else if (this.level().isRaining()) {
            parts.add("\u4e0b\u96e8");
        }
        if (this.level().canSeeSky(this.blockPosition())) {
            parts.add("\u9732\u5929");
        } else if (this.runtimeHasLowCeiling()) {
            parts.add("\u96f7\u96e8");
        } else {
            parts.add("\u4e0b\u96e8");
        }
        if (this.isInWater() || this.isUnderWater()) {
            parts.add("\u5728\u6c34\u4e2d");
        }
        if (this.isInLava()) {
            parts.add("\u5728\u5ca9\u6d46\u4e2d");
        }
        if (this.isOnFire()) {
            parts.add("\u7740\u706b");
        }
        if (this.getHealth() <= 8.0F) {
            parts.add("\u8840\u91cf\u504f\u4f4e");
        }
        if (this.hasLowFoodSupply()) {
            parts.add("\u98df\u7269\u504f\u5c11");
        }
        if (this.hasLowTools()) {
            parts.add("\u5de5\u5177\u504f\u5c11");
        }
        if (this.observedHostile != null && this.observedHostile.isAlive()) {
            parts.add("\u9644\u8fd1\u654c\u5bf9=" + this.observedHostile.getName().getString());
        }
        if (this.observedDrop != null && this.observedDrop.isAlive()) {
            parts.add("\u9644\u8fd1\u6389\u843d\u7269=" + this.observedDrop.getItem().getHoverName().getString());
        }
        ServerPlayer owner = this.getOwnerPlayer();
        if (owner != null && !owner.isRemoved()) {
            parts.add("\u4e3b\u4eba\u8ddd\u79bb=" + Math.max(0, Mth.floor(this.distanceTo(owner))));
        }
        if (this.huntTargetLabel != null && !this.huntTargetLabel.isBlank()) {
            parts.add("\u72e9\u730e\u76ee\u6807=" + this.huntTargetLabel);
        }
        return String.join("\uff1b", parts);
    }

    private void clearHuntDirective() {
        this.huntTargetId = "";
        this.huntTargetLabel = "";
        this.huntLastKnownPos = null;
        this.markPersistentDirty();
    }

    private boolean hasActiveHuntDirective() {
        return this.huntTargetId != null && !this.huntTargetId.isBlank();
    }

    private void equipHuntCombatLoadout() {
        if (!this.hasActiveHuntDirective()) {
            return;
        }
        String requirement = KnowledgeManager.getPreferredWeaponForMobId(this.huntTargetId);
        ItemStack preferred = this.findBestToolForRequirement(requirement);
        if (preferred.isEmpty()) {
            preferred = this.findBestToolForRequirement("sword");
        }
        if (preferred.isEmpty()) {
            preferred = this.findBestCombatItem();
        }
        if (!preferred.isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, preferred.copy());
        }
        this.ensureShieldInOffhand();
    }

    private boolean performHuntDirective() {
        if (!this.hasActiveHuntDirective()) {
            return false;
        }
        this.equipHuntCombatLoadout();
        if (this.observedHostile != null && this.observedHostile.isAlive() && this.distanceToSqr(this.observedHostile) <= 49.0D) {
            return false;
        }
        LivingEntity target = this.findNearestHuntTarget();
        if (target != null) {
            this.huntLastKnownPos = target.blockPosition().immutable();
            this.setTarget(target);
            this.setForcedLookTarget(target.getEyePosition(), 10);
            this.lastObservation = "\u9501\u5b9a\u72e9\u730e\u76ee\u6807 " + target.getName().getString() + "@" + this.formatPos(target.blockPosition());
            if (this.distanceToSqr(target) > 9.0D) {
                BlockPos moveTarget = this.runtimeResolveMovementTarget(target.blockPosition());
                if (moveTarget != null) {
                    this.navigateToPosition(moveTarget, 1.12D);
                }
            }
            return true;
        }
        if (this.huntLastKnownPos != null && this.distanceToSqr(Vec3.atCenterOf(this.huntLastKnownPos)) > 6.25D) {
            BlockPos moveTarget = this.runtimeResolveMovementTarget(this.huntLastKnownPos);
            if (moveTarget != null) {
                this.lastObservation = "\u6b63\u5728\u8ffd\u8e2a" + this.huntTargetLabel + "\u6700\u8fd1\u51fa\u73b0\u7684\u4f4d\u7f6e@" + this.formatPos(this.huntLastKnownPos);
                this.navigateToPosition(moveTarget, 1.05D);
                return true;
            }
        }
        ServerPlayer owner = this.getOwnerPlayer();
        if (owner != null && this.distanceToSqr(owner) > 12.0D * 12.0D) {
            this.performFollow(false);
            return true;
        }
        this.lastObservation = "\u6b63\u5728\u641c\u7d22\u9644\u8fd1\u7684" + this.huntTargetLabel + "\u3002";
        if (this.tickCount % 40 == 0) {
            BlockPos destination = this.findExplorationDestination();
            if (destination != null) {
                this.navigateToPosition(destination, 1.0D);
            }
        }
        return true;
    }

    private LivingEntity findNearestHuntTarget() {
        AABB searchBox = this.getBoundingBox().inflate(HUNT_SCAN_RANGE);
        ServerPlayer owner = this.getOwnerPlayer();
        if (owner != null && !owner.isRemoved()) {
            searchBox = searchBox.minmax(owner.getBoundingBox().inflate(HUNT_SCAN_RANGE));
        }
        return this.level().getEntitiesOfClass(LivingEntity.class, searchBox, entity -> entity != this
                        && entity.isAlive()
                        && AnimalTargetHelper.matchesRequestedAnimal(entity, this.huntTargetId))
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    private String buildEnvironmentSignature() {
        String hostileKey = this.observedHostile != null && this.observedHostile.isAlive()
                ? this.observedHostile.getType().getDescription().getString()
                : "";
        boolean ownerFar = false;
        ServerPlayer owner = this.getOwnerPlayer();
        if (owner != null && !owner.isRemoved()) {
            ownerFar = this.distanceToSqr(owner) > OWNER_TELEPORT_FOLLOW_DISTANCE_SQR;
        }
        return ((this.level().getDayTime() % 24000L) >= 12500L ? "night" : "day")
                + '|' + (this.level().isRaining() ? '1' : '0')
                + '|' + (this.level().isThundering() ? '1' : '0')
                + '|' + (this.level().canSeeSky(this.blockPosition()) ? '1' : '0')
                + '|' + ((this.isInWater() || this.isUnderWater()) ? '1' : '0')
                + '|' + (this.isInLava() ? '1' : '0')
                + '|' + (this.isOnFire() ? '1' : '0')
                + '|' + (this.getHealth() <= 8.0F ? '1' : '0')
                + '|' + (this.hasLowFoodSupply() ? '1' : '0')
                + '|' + (this.hasLowTools() ? '1' : '0')
                + '|' + (ownerFar ? '1' : '0')
                + '|' + hostileKey;
    }

    private void tickEnvironmentAwareness() {
        if (this.tickCount % 20 != 0) {
            return;
        }
        ServerPlayer owner = this.getOwnerPlayer();
        if (owner == null || owner.isRemoved() || owner.isSpectator()) {
            return;
        }
        String signature = this.buildEnvironmentSignature();
        if (signature.equals(this.lastEnvironmentSignature)) {
            return;
        }
        this.lastEnvironmentSignature = signature;
        if (this.tickCount < this.nextAwarenessBroadcastTick) {
            return;
        }
        String fallbackMessage = this.buildEnvironmentBroadcastFallback();
        if (fallbackMessage.isBlank()) {
            return;
        }
        this.nextAwarenessBroadcastTick = this.tickCount + AWARENESS_BROADCAST_INTERVAL;
        if (!AIServiceManager.canUseConversationService()) {
            this.sendOwnerAlert(fallbackMessage, false);
            return;
        }
        this.requestAwarenessBroadcast(owner, fallbackMessage);
    }

    private void tickAdaptiveScanning() {
        if (this.tickCount - this.lastResourceScanTick >= RESOURCE_SCAN_INTERVAL) {
            this.lastResourceScanTick = this.tickCount;
            this.scanSurroundings();
        }
        if (this.tickCount - this.lastUtilityScanTick >= UTILITY_SCAN_INTERVAL) {
            this.lastUtilityScanTick = this.tickCount;
            this.refreshKnownUtilityPoints();
        }
        if (this.tickCount - this.lastCropScanTick >= CROP_SCAN_INTERVAL) {
            this.lastCropScanTick = this.tickCount;
            BlockPos crop = this.findNearestUtilityBlock(this::isCropBlock, SCAN_RADIUS, 4, 6);
            if (crop != null && !crop.equals(this.rememberedCrop)) {
                this.recordUtilityDiscovery("作物", this.rememberedCrop, crop);
                this.rememberedCrop = crop;
            }
        }
    }

    private void refreshKnownUtilityPoints() {
        BlockPos previousBed = this.rememberedBed;
        if (!this.isRememberedUtilityBlockValid(this.rememberedBed, this::isBedBlock)) {
            this.rememberedBed = this.findNearestUtilityBlock(this::isBedBlock, SCAN_RADIUS, 5, 8);
            this.recordUtilityDiscovery("床位", previousBed, this.rememberedBed);
        }

        BlockPos previousChest = this.rememberedChest;
        if (!this.isRememberedUtilityBlockValid(this.rememberedChest, this::isStorageBlock)) {
            this.rememberedChest = this.findNearestUtilityBlock(this::isStorageBlock, SCAN_RADIUS, 5, 8);
            this.recordUtilityDiscovery("储物点", previousChest, this.rememberedChest);
        }

        BlockPos previousCrafting = this.rememberedCraftingTable;
        if (!this.isRememberedUtilityBlockValid(this.rememberedCraftingTable, this::isCraftingBlock)) {
            this.rememberedCraftingTable = this.findNearestUtilityBlock(this::isCraftingBlock, SCAN_RADIUS, 5, 8);
            this.recordUtilityDiscovery("工作台", previousCrafting, this.rememberedCraftingTable);
        }

        BlockPos previousFurnace = this.rememberedFurnace;
        if (!this.isRememberedUtilityBlockValid(this.rememberedFurnace, this::isFurnaceBlock)) {
            this.rememberedFurnace = this.findNearestUtilityBlock(this::isFurnaceBlock, SCAN_RADIUS, 5, 8);
            this.recordUtilityDiscovery("熔炉", previousFurnace, this.rememberedFurnace);
        }
    }

    private void tickDebugStatusBroadcast() {
        if (this.tickCount - this.lastDebugStatusTick < DEBUG_STATUS_BROADCAST_INTERVAL) {
            return;
        }
        this.lastDebugStatusTick = this.tickCount;
        String observation = this.lastObservation == null ? "" : this.lastObservation;
        if (observation.length() > 36) {
            observation = observation.substring(0, 36) + "…";
        }
        String debugLine = "模式=" + this.safeModeDisplayName()
                + " | 动作=" + this.agentRuntime.currentActionLabel()
                + " | 目标=" + this.buildDebugTargetSummary()
                + " | 路径=" + this.buildDebugPathSummary()
                + " | 观察=" + observation;
        AIPlayersMod.LOGGER.info("[AIPlayers][Debug][{}] {}", this.getAIName(), debugLine);
        ServerPlayer owner = this.getOwnerPlayer();
        if (owner != null && !owner.isRemoved() && !owner.isSpectator()) {
            owner.sendSystemMessage(Component.literal("[AI-DBG][" + this.getAIName() + "] " + debugLine));
        }
    }

    private String buildDebugTargetSummary() {
        if (this.getTarget() != null && this.getTarget().isAlive()) {
            return "战斗:" + this.getTarget().getName().getString() + "@" + this.formatPos(this.getTarget().blockPosition());
        }
        if (this.miningTarget != null && this.miningMode != MiningMode.NONE) {
            return "采掘:" + this.formatPos(this.miningTarget) + "(" + this.miningMode.name() + ")";
        }
        if (this.harvestTaskTarget != null) {
            return "采集:" + this.formatPos(this.harvestTaskTarget) + "(" + this.harvestTaskState.name() + ")";
        }
        if (this.activeNavigationTarget != null) {
            return "移动:" + this.formatPos(this.activeNavigationTarget);
        }
        if (this.huntLastKnownPos != null && this.hasActiveHuntDirective()) {
            return "狩猎:" + this.huntTargetLabel + "@" + this.formatPos(this.huntLastKnownPos);
        }
        return "无";
    }

    private String buildDebugPathSummary() {
        String path = this.agentRuntime.pathStatus();
        if (this.activeNavigationTarget == null) {
            return path;
        }
        return path + "->" + this.formatPos(this.activeNavigationTarget) + "/卡住" + this.stuckNavigationTicks + "t";
    }

    private String buildEnvironmentBroadcastFallback() {
        return "\u73af\u5883\u53d8\u5316\uff1a" + this.getEnvironmentSummary();
    }

    private void requestAwarenessBroadcast(ServerPlayer owner, String fallbackMessage) {
        if (this.pendingAwarenessBroadcast) {
            return;
        }
        this.pendingAwarenessBroadcast = true;
        String prompt = "\u8fd9\u4e0d\u662f\u73a9\u5bb6\u547d\u4ee4\uff0c\u800c\u662f AI \u7684\u73af\u5883\u81ea\u68c0\u64ad\u62a5\u3002\u8bf7\u53ea\u7528\u4e00\u53e5\u7b80\u77ed\u4e2d\u6587\u64ad\u62a5\u5f53\u524d\u73af\u5883\uff0c\u4e0d\u8981\u7ed9\u6a21\u5f0f\u3001\u52a8\u4f5c\u6216\u76ee\u6807\u6307\u4ee4\u3002\u73af\u5883\u6458\u8981\uff1a"
                + this.getEnvironmentSummary();
        AIServiceManager.tryRespondAsync(this, owner, prompt).whenComplete((response, throwable) -> {
            var server = owner.level().getServer();
            if (server == null) {
                this.pendingAwarenessBroadcast = false;
                return;
            }
            server.execute(() -> {
                this.pendingAwarenessBroadcast = false;
                String reply = fallbackMessage;
                if (throwable == null && response != null && response.reply() != null && !response.reply().isBlank()) {
                    reply = response.reply();
                }
                this.sendOwnerAlert(reply, false);
            });
        });
    }

    private String normalizeConversationText(String content) {
        if (content == null) {
            return "";
        }
        return content.toLowerCase(Locale.ROOT)
                .replace('，', ' ')
                .replace('。', ' ')
                .replace('！', ' ')
                .replace('？', ' ')
                .replace('、', ' ')
                .replace('：', ' ')
                .replace(':', ' ')
                .replace('；', ' ')
                .replace(';', ' ')
                .replace('·', ' ')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void runAgentPipeline() {
        AIAgentPlan localPlan = AIAgentPipeline.evaluate(this);
        this.maybeRequestTaskAiPlan(localPlan);
        AIAgentPlan plan = this.mergeHybridPlan(localPlan);
        this.activeTaskName = plan.goal();
        this.latestAgentGoal = plan.goal();
        this.latestAgentReasoning = plan.reasoning();
        this.latestAgentPlanSummary = plan.summary();
        this.setGoalNote(plan.goal());
        AILongTermMemoryStore.updateNote(this, "pipeline_goal", plan.goal());
        AILongTermMemoryStore.updateNote(this, "pipeline_reasoning", plan.reasoning());
        AILongTermMemoryStore.updateNote(this, "task_feedback", this.lastTaskFeedback);

        AIPlayerMode recommendedMode = plan.recommendedMode();
        if (recommendedMode != null && this.shouldAdoptPipelineMode(recommendedMode)) {
            this.setMode(recommendedMode);
        }

        String learnKey = plan.goal() + "|" + this.safeModeCommandName() + "|" + plan.reasoning();
        if (!learnKey.equals(this.lastAgentLearnKey)) {
            this.lastAgentLearnKey = learnKey;
            this.remember("规划", plan.goal() + "（" + plan.source() + "）");
            AILongTermMemoryStore.record(this, "规划", plan.goal() + " -> " + plan.reasoning());
        }
    }

    private void maybeRequestTaskAiPlan(AIAgentPlan localPlan) {
        if (this.level().isClientSide() || !AIServiceManager.canUseTaskPlanningService()) {
            return;
        }
        if (this.pendingTaskAiPlan || this.tickCount < this.nextTaskAiPlanTick) {
            return;
        }

        this.pendingTaskAiPlan = true;
        this.nextTaskAiPlanTick = this.tickCount + AIServiceManager.getTaskAiIntervalTicks();
        this.markPersistentDirty();
        AIServiceManager.tryPlanTaskAsync(this, localPlan).whenComplete((response, throwable) -> {
            var server = this.level().getServer();
            if (server == null) {
                this.pendingTaskAiPlan = false;
                return;
            }

            server.execute(() -> {
                this.pendingTaskAiPlan = false;
                if (throwable != null || response == null || !response.hasPlan()) {
                    this.lastTaskAiServiceStatus = AIServiceManager.getLastStatusText();
                    this.clearTaskAiPlanState();
                    this.nextTaskAiPlanTick = this.tickCount + AIServiceManager.getTaskAiIntervalTicks() * 4;
                    this.remember("任务AI", "任务规划暂不可用，继续使用本地计划：" + this.lastTaskAiServiceStatus);
                    return;
                }
                this.lastTaskAiServiceStatus = "最近一次任务规划成功";
                this.taskAiGoal = response.goal();
                this.taskAiMode = response.resolveMode(this.safeMode());
                this.taskAiSubtasks = response.subtasks() == null ? new ArrayList<>() : new ArrayList<>(response.subtasks());
                this.taskAiFallback = response.fallback();
                this.taskAiPlanValidUntilTick = this.tickCount + AIServiceManager.getTaskAiIntervalTicks() * 2;
                this.reportTaskProgress(this.activeTaskName, "任务 AI 已重规划：" + response.goal());
                this.remember("任务AI", response.goal() + " -> " + String.join(" -> ", this.taskAiSubtasks));
                AILongTermMemoryStore.record(this, "任务AI", response.goal() + "；fallback=" + response.fallback());
            });
        });
    }

    private AIAgentPlan mergeHybridPlan(AIAgentPlan localPlan) {
        if (!this.hasActiveTaskAiPlan()) {
            return localPlan;
        }

        if (this.taskFailureStreak >= 3) {
            return new AIAgentPlan(
                    this.taskAiFallback == null || this.taskAiFallback.isBlank() ? localPlan.goal() : this.taskAiFallback,
                    this.interpretFallbackMode(localPlan.recommendedMode()),
                    List.of("停止当前高层策略", "执行回退方案", "回到可稳定推进的任务"),
                    "Hybrid 规划连续失败，触发任务 AI 回退；最近反馈=" + this.lastTaskFeedback,
                    "hybrid-fallback");
        }

        List<String> steps = this.taskAiSubtasks == null || this.taskAiSubtasks.isEmpty() ? localPlan.steps() : List.copyOf(this.taskAiSubtasks);
        String goal = this.taskAiGoal == null || this.taskAiGoal.isBlank() ? localPlan.goal() : this.taskAiGoal;
        AIPlayerMode mode = this.taskAiMode == null ? localPlan.recommendedMode() : this.taskAiMode;
        return new AIAgentPlan(
                goal,
                mode,
                steps,
                "Hybrid 规划：LLM 负责高层目标，本地执行负责落地；反馈=" + this.lastTaskFeedback,
                "hybrid-task-ai");
    }

    private boolean hasActiveTaskAiPlan() {
        return this.taskAiGoal != null
                && !this.taskAiGoal.isBlank()
                && this.tickCount <= this.taskAiPlanValidUntilTick;
    }

    private AIPlayerMode interpretFallbackMode(AIPlayerMode localFallback) {
        if (this.taskAiFallback == null || this.taskAiFallback.isBlank()) {
            return localFallback;
        }
        String normalized = this.taskAiFallback.toLowerCase();
        for (AIPlayerMode mode : AIPlayerMode.values()) {
            if (normalized.contains(mode.commandName())) {
                return mode;
            }
        }
        if (normalized.contains("跟随") || normalized.contains("follow")) {
            return AIPlayerMode.FOLLOW;
        }
        if (normalized.contains("护卫") || normalized.contains("guard")) {
            return AIPlayerMode.GUARD;
        }
        if (normalized.contains("探索") || normalized.contains("explore")) {
            return AIPlayerMode.EXPLORE;
        }
        if (normalized.contains("生存") || normalized.contains("survive")) {
            return AIPlayerMode.SURVIVE;
        }
        return localFallback;
    }

    private boolean shouldAdoptPipelineMode(AIPlayerMode recommendedMode) {
        if (recommendedMode == this.safeMode()) {
            return false;
        }
        if (this.playerModePinned && this.playerPinnedMode == this.safeMode()) {
            return false;
        }
        return switch (this.safeMode()) {
            case IDLE, FOLLOW, GUARD, EXPLORE, SURVIVE -> true;
            case GATHER_WOOD -> !this.hasRememberedHarvestTarget(true) && recommendedMode == AIPlayerMode.EXPLORE;
            case MINE -> !this.hasRememberedHarvestTarget(false) && recommendedMode == AIPlayerMode.EXPLORE;
            case BUILD_SHELTER -> this.countAvailableBuildingUnits() < 12 && recommendedMode == AIPlayerMode.GATHER_WOOD;
        };
    }

    public AIAgentWorldState buildAgentWorldState() {
        ServerPlayer owner = this.getOwnerPlayer();
        int usedSlots = this.getUsedBackpackSlots();
        boolean progressStalled = this.tickCount - this.lastMeaningfulProgressTick > 120;
        return new AIAgentWorldState(
                this.safeMode(),
                owner != null,
                owner == null ? Double.MAX_VALUE : Math.sqrt(this.distanceToSqr(owner)),
                this.isOwnerUnderThreat(),
                this.observedHostile != null && this.observedHostile.isAlive(),
                this.getHealth() <= 10.0F,
                this.isInWater() || this.isUnderWater() || this.isInLava(),
                this.isOnFire(),
                this.stuckNavigationTicks >= NAVIGATION_STUCK_THRESHOLD / 2,
                this.pendingDeliveryRequest != null && !this.pendingDeliveryRequest.isBlank(),
                this.hasRememberedHarvestTarget(true),
                this.hasRememberedHarvestTarget(false),
                this.hasLowFoodSupply(),
                this.rememberedCrop != null,
                this.observedDrop != null && this.observedDrop.isAlive(),
                this.rememberedBed != null,
                this.rememberedCraftingTable != null || this.rememberedFurnace != null || this.rememberedChest != null,
                this.hasLowTools(),
                (this.level().getDayTime() % 24000L) >= 12500L,
                this.countAvailableBuildingUnits(),
                usedSlots,
                Math.max(0, BACKPACK_SIZE - usedSlots),
                progressStalled,
                this.taskFailureStreak,
                this.taskSuccessStreak,
                this.getObservationSummary(),
                this.getInventoryPreview(),
                this.activeTaskName,
                this.lastTaskFeedback,
                this.getCognitiveSummary());
    }

    public boolean hasRememberedHarvestTarget(boolean woodTask) {
        return woodTask ? this.isValidHarvestTarget(this.rememberedLog, true) : this.isValidHarvestTarget(this.rememberedOre, false);
    }

    private void runModeLogic() {
        this.autoMaintainSurvival();
        if (this.hasActiveHuntDirective()) {
            if (this.autoRecoverFromHazards()) {
                return;
            }
            if (this.performHuntDirective()) {
                return;
            }
        }

        if (this.autoRecoverFromHazards()) {
            return;
        }

        if (this.performPendingDeliveryTask()) {
            return;
        }

        if (this.autoCollectNearbyDrops()) {
            return;
        }

        if (this.getTarget() != null && !this.getTarget().isAlive()) {
            this.setTarget(null);
        }

        if (this.performHuntDirective()) {
            return;
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
        this.autoSortBackpack();
        if (this.hasActiveHuntDirective()) {
            this.equipHuntCombatLoadout();
        } else {
            this.autoEquipBestAvailableGear();
        }
        this.tryCraftBread();
        this.tryCraftTorchBundle();
        if (!this.hasActiveHuntDirective()) {
            this.tryCraftStoneTool(Items.STONE_AXE, "石斧");
            this.tryCraftStoneTool(Items.STONE_PICKAXE, "石镐");
            this.tryCraftStoneTool(Items.STONE_SHOVEL, "石铲", 1);
            this.tryCraftStoneTool(Items.STONE_HOE, "石锄", 2);
        }
        this.shareResourcesWithTeammates();

        ItemStack food = this.findBestRecoveryFood();
        if (this.getHealth() <= 12.0F && !food.isEmpty() && this.consumeBackpackItem(food.getItem(), 1)) {
            this.heal(this.getHealingValue(food));
            this.crouchTicks = Math.max(this.crouchTicks, 15);
            this.lastObservation = "已消耗" + food.getHoverName().getString() + "进行恢复。";
            this.remember("生存", "消耗" + food.getHoverName().getString() + "恢复生命");
        }
    }

    private boolean hasLowFoodSupply() {
        return this.countRecoveryFoodItems() <= 2;
    }

    private int countRecoveryFoodItems() {
        int total = 0;
        for (ItemStack stack : this.backpack) {
            if (!stack.isEmpty() && this.isRecoveryFood(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private boolean hasLowTools() {
        boolean hasAxe = this.countBackpackItem(Items.STONE_AXE) + this.countBackpackItem(Items.IRON_AXE) + this.countBackpackItem(Items.DIAMOND_AXE) + this.countBackpackItem(Items.NETHERITE_AXE) > 0;
        boolean hasPickaxe = this.countBackpackItem(Items.STONE_PICKAXE) + this.countBackpackItem(Items.IRON_PICKAXE) + this.countBackpackItem(Items.DIAMOND_PICKAXE) + this.countBackpackItem(Items.NETHERITE_PICKAXE) > 0;
        boolean hasShovel = this.countBackpackItem(Items.STONE_SHOVEL) + this.countBackpackItem(Items.IRON_SHOVEL) + this.countBackpackItem(Items.DIAMOND_SHOVEL) + this.countBackpackItem(Items.NETHERITE_SHOVEL) > 0;
        boolean hasHoe = this.countBackpackItem(Items.STONE_HOE) + this.countBackpackItem(Items.IRON_HOE) + this.countBackpackItem(Items.DIAMOND_HOE) + this.countBackpackItem(Items.NETHERITE_HOE) > 0;
        return !hasAxe || !hasPickaxe || !hasShovel || !hasHoe;
    }

    private boolean isOwnerUnderThreat() {
        ServerPlayer owner = this.getOwnerPlayer();
        return owner != null
                && this.observedHostile != null
                && this.observedHostile.isAlive()
                && owner.distanceToSqr(this.observedHostile) <= 12.0D * 12.0D;
    }

    private boolean autoCollectNearbyDrops() {
        if (this.observedDrop == null || !this.observedDrop.isAlive() || this.observedDrop.getItem().isEmpty()) {
            return false;
        }
        if (!this.canStoreInBackpack(this.observedDrop.getItem())) {
            return false;
        }

        double distanceSqr = this.distanceToSqr(this.observedDrop);
        if (distanceSqr <= 4.0D) {
            ItemStack before = this.observedDrop.getItem().copy();
            ItemStack remainder = this.storeInBackpack(before.copy());
            int collected = before.getCount() - remainder.getCount();
            if (collected <= 0) {
                return false;
            }
            if (remainder.isEmpty()) {
                this.observedDrop.discard();
            } else {
                this.observedDrop.setItem(remainder);
            }
            this.lastObservation = "已拾取掉落物 " + before.getHoverName().getString() + "x" + collected + "。";
            this.remember("拾取", this.lastObservation);
            this.reportTaskProgress(this.activeTaskName, "已回收掉落物：" + before.getHoverName().getString() + "x" + collected);
            return true;
        }

        if (distanceSqr <= 81.0D && this.observedHostile == null && this.pendingDeliveryRequest == null) {
            BlockPos approach = this.findWalkablePositionNear(this.observedDrop.blockPosition(), 1, 2);
            if (approach != null && this.navigateToPosition(approach, 1.08D)) {
                this.lastObservation = "发现掉落物，正在前往拾取@" + this.formatPos(this.observedDrop.blockPosition());
                return true;
            }
        }
        return false;
    }

    private boolean seekKnownRestSpot() {
        if ((this.level().getDayTime() % 24000L) < 12500L) {
            return false;
        }

        BlockPos restTarget = this.rememberedBed != null ? this.rememberedBed : this.shelterAnchor;
        if (restTarget == null) {
            return false;
        }

        if (this.distanceToSqr(Vec3.atCenterOf(restTarget)) > 9.0D) {
            BlockPos approach = this.findApproachPosition(restTarget);
            if (approach != null && this.navigateToPosition(approach, 1.0D)) {
                this.lastObservation = "夜间前往安全休整点@" + this.formatPos(restTarget);
                return true;
            }
            return false;
        }

        this.setForcedLookTarget(Vec3.atCenterOf(restTarget), 12);
        this.crouchTicks = Math.max(this.crouchTicks, 20);
        if (this.getHealth() < this.getMaxHealth()) {
            this.heal(0.4F);
        }
        this.lastObservation = this.rememberedBed != null
                ? "夜间在床边休整，等待天亮。"
                : "夜间在避难所休整，保持警戒。";
        return true;
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
        boolean navigationInProgress = this.agentRuntime.movementController().isPathActive()
                && !this.agentRuntime.movementController().isRecovering();
        if (this.lookTicks > 0) {
            if (this.forcedLookTarget != null && !navigationInProgress) {
                this.getLookControl().setLookAt(this.forcedLookTarget);
            }
            this.lookTicks--;
            if (this.lookTicks <= 0) {
                this.forcedLookTarget = null;
            }
        }
        if (this.shieldGuardTicks > 0) {
            this.shieldGuardTicks--;
        }
        if (this.shieldGuardTicks > 0 && this.getOffhandItem().getItem() == Items.SHIELD) {
            if (!this.isUsingItem() || this.getUsedItemHand() != InteractionHand.OFF_HAND) {
                this.startUsingItem(InteractionHand.OFF_HAND);
            }
        } else if (this.isUsingItem() && this.getUsedItemHand() == InteractionHand.OFF_HAND) {
            this.stopUsingItem();
        }

        boolean shouldCrouch = this.crouchTicks > 0 || this.shouldAutoCrouch();
        this.setShiftKeyDown(shouldCrouch);
    }

    private void tickNavigationState() {
        if (this.isMiningLocked()) {
            this.getNavigation().stop();
            return;
        }
        BlockPos movementTarget = this.agentRuntime.movementController().getActiveTargetPos();
        if (movementTarget != null) {
            this.activeNavigationTarget = movementTarget;
        } else if (this.activeNavigationTarget != null && this.agentRuntime.movementController().isIdle()) {
            this.resetNavigationState();
            return;
        }
        if (this.activeNavigationTarget == null) {
            if (this.agentRuntime.movementController().isIdle()) {
                this.resetNavigationState();
            }
            return;
        }
        this.ensureVanillaNavigationProgress();

        if (this.agentRuntime.movementController().hasReachedTarget(this.activeNavigationTarget)) {
            this.resetNavigationState();
            return;
        }
        if (this.agentRuntime.movementController().isRecovering()) {
            this.lastNavigationSample = this.position();
            return;
        }
        if (this.tickCount % SCAN_INTERVAL != 0) {
            return;
        }

        Vec3 currentPos = this.position();
        if (currentPos.distanceToSqr(this.lastNavigationSample) < 0.09D) {
            this.stuckNavigationTicks += SCAN_INTERVAL;
            if (this.stuckNavigationTicks >= NAVIGATION_STUCK_THRESHOLD) {
                if (this.tryNavigationRecovery(false)) {
                    this.stuckNavigationTicks = 0;
                    this.lastNavigationSample = currentPos;
                } else {
                    this.lastObservation = "导航推进停滞，正在等待新路径。";
                    this.reportTaskFailure(this.activeTaskName, "导航推进停滞，当前路径失效");
                    this.resetNavigationState();
                }
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
        this.activeNavigationSpeed = NORMAL_FOLLOW_SPEED;
        this.agentRuntime.movementController().clear();
    }

    private boolean navigateToPosition(BlockPos pos, double speed) {
        if (pos == null) {
            return false;
        }
        if (this.activeNavigationTarget != null && this.activeNavigationTarget.distSqr(pos) <= 1.0D && this.agentRuntime.movementController().isPathActive()) {
            return true;
        }

        if (this.tryStartNavigation(pos, speed)) {
            return true;
        }

        BlockPos fallback = this.findWalkablePositionNear(pos, 2, 3);
        return fallback != null && !fallback.equals(pos) && this.tryStartNavigation(fallback, speed);
    }

    private boolean tryStartNavigation(BlockPos pos, double speed) {
        boolean started = this.agentRuntime.movementController().requestPathTo(pos, speed);
        if (started) {
            BlockPos resolvedTarget = this.agentRuntime.movementController().getActiveTargetPos();
            this.activeNavigationTarget = resolvedTarget != null ? resolvedTarget : pos;
            this.activeNavigationSpeed = speed;
            this.stuckNavigationTicks = 0;
            this.lastNavigationSample = this.position();
            this.ensureVanillaNavigationProgress();
        }
        return started;
    }

    private void ensureVanillaNavigationProgress() {
        if (this.activeNavigationTarget == null) {
            return;
        }
        if (this.tickCount % 10 != 0) {
            return;
        }
        if (this.getNavigation().getPath() == null || this.getNavigation().isDone()) {
            Vec3 center = Vec3.atCenterOf(this.activeNavigationTarget);
            this.getNavigation().moveTo(center.x, center.y, center.z, this.activeNavigationSpeed);
        }
    }

    private boolean shouldAutoCrouch() {
        AIPlayerMode currentMode = this.safeMode();
        if (currentMode != AIPlayerMode.MINE && currentMode != AIPlayerMode.BUILD_SHELTER) {
            return false;
        }
        if (this.agentRuntime.movementController().isPathActive()) {
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

        if (owner == null) {
            this.autonomousIdleTicks++;
            if (this.autonomousIdleTicks >= 100) {
                this.autonomousIdleTicks = 0;
                this.lastObservation = "长时间待命后开始自主生存。";
                this.remember("认知", this.lastObservation);
                this.setMode(AIPlayerMode.SURVIVE);
            }
        } else {
            this.autonomousIdleTicks = 0;
        }
    }

    private void performFollow(boolean guardMode) {
        ServerPlayer owner = this.getOwnerPlayer();
        if (owner == null || owner.isRemoved() || owner.isSpectator()) {
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
        double ownerDistanceSqr = this.distanceToSqr(owner);
        if (ownerDistanceSqr >= OWNER_TELEPORT_FOLLOW_DISTANCE_SQR
                && this.tryTeleportNearOwnerLikeWolf(owner, "距离过远，已快速跟上主人@")) {
            return;
        }

        if (ownerDistanceSqr > followDistance * followDistance) {
            double speed = ownerDistanceSqr >= FAST_FOLLOW_DISTANCE_SQR ? FAST_FOLLOW_SPEED : NORMAL_FOLLOW_SPEED;
            BlockPos ownerGoal = this.findWalkablePositionNear(owner.blockPosition(), 1, 2);
            if (ownerGoal == null) {
                ownerGoal = owner.blockPosition();
            }
            this.navigateToPosition(ownerGoal, speed);
        } else {
            this.getNavigation().stop();
            this.resetNavigationState();
            this.setForcedLookTarget(owner.getEyePosition(), 10);
        }
    }

    private void performHarvestTask(boolean woodTask) {
        this.tryPlaceTorchForSafety();
        if (this.observedHostile != null && this.distanceToSqr(this.observedHostile) <= 49.0D) {
            this.setTarget(this.observedHostile);
            this.setForcedLookTarget(this.observedHostile.getEyePosition(), 10);
            return;
        }

        if (this.getTarget() != null && this.getTarget().isAlive()) {
            this.setForcedLookTarget(this.getTarget().getEyePosition(), 10);
            return;
        }

        BlockPos target = this.resolveHarvestTarget(woodTask, woodTask ? this.rememberedLog : this.rememberedOre);
        if (woodTask) {
            this.rememberedLog = target;
        } else {
            this.rememberedOre = target;
        }

        if (target == null) {
            this.lastObservation = woodTask ? "附近没有可采伐木头，先去搜索。" : "附近没有可挖掘矿石，先去搜索。";
            this.reportTaskFailure(this.activeTaskName, woodTask ? "附近没有可采伐木头，转入搜索" : "附近没有可挖掘矿石，转入搜索");
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
        if (!this.canHarvestFromHere(target)
                && target.getY() > this.blockPosition().getY() + 2
                && this.tryPillarUpToward(target, "已脚底垫块接近高处目标@")) {
            this.reportTaskProgress(this.activeTaskName, "目标较高，已脚底垫块继续采集");
            return;
        }

        BlockPos obstacle = this.findHarvestObstacle(target, woodTask);
        if (obstacle != null && this.canHarvestFromHere(obstacle)) {
            if (this.breakAuxiliaryBlock(obstacle, woodTask ? "已清理树叶@" : "已清理遮挡方块@")) {
                this.reportTaskProgress(this.activeTaskName, woodTask ? "已清理树叶，继续采木" : "已清理矿点遮挡，继续采掘");
                return;
            }
        }

        if (this.canHarvestFromHere(target)) {
            if (this.harvestBlock(target, woodTask)) {
                BlockPos nextTarget = this.findConnectedHarvestTarget(target, woodTask);
                if (woodTask) {
                    this.rememberedLog = nextTarget;
                } else {
                    this.rememberedOre = nextTarget;
                }
                this.scanSurroundings();
            }
            return;
        }

        BlockPos focusTarget = obstacle != null ? obstacle : target;
        BlockPos approach = this.findApproachPosition(focusTarget);
        BlockPos navigationGoal = approach != null ? approach : this.runtimeResolveMovementTarget(focusTarget);

        if (navigationGoal == null || !this.navigateToPosition(navigationGoal, 1.05D)) {
            if (this.tryDiggableAdvance(target)) {
                this.reportTaskProgress(this.activeTaskName, "路径受阻，已尝试破障开路");
                return;
            }
            if (this.tryBuildClimbSupportToward(target, "已搭建攀爬台阶@")) {
                this.reportTaskProgress(this.activeTaskName, "路径受阻，已尝试搭建台阶继续采集");
                return;
            }
            if (this.tryPillarUpToward(target, "已脚底垫块抬升@")) {
                this.reportTaskProgress(this.activeTaskName, "目标较高，已脚底垫块抬升");
                return;
            }
            this.lastObservation = "目标路径受阻，正在重新规划@" + this.formatPos(target);
            this.reportTaskFailure(this.activeTaskName, "采集目标路径受阻：" + this.formatPos(target));
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

        if (this.tryPlaceTorchForSafety()) {
            return;
        }

        if (this.getTarget() != null && this.getTarget().isAlive()) {
            return;
        }

        if (this.tickCount % 40 == 0 && (this.activeNavigationTarget == null || !this.agentRuntime.movementController().isPathActive())) {
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

        this.coordinateBuildTeamRoles();
        this.tryPlaceTorchForSafety();

        if (this.countAvailableBuildingUnits() < 12) {
            this.lastObservation = "建材不足，先去砍树。";
            this.reportTaskFailure(this.activeTaskName, "建材不足，切回采木补足材料");
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
            this.reportTaskProgress(this.activeTaskName, "避难所蓝图施工完成");
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
            this.reportTaskFailure(this.activeTaskName, "建造点路径受阻：" + this.formatPos(nextPlacement));
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

        if (this.seekKnownRestSpot()) {
            return;
        }

        this.tryPlaceTorchForSafety();

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

    private ItemStack findBestRecoveryFood() {
        ItemStack best = ItemStack.EMPTY;
        int bestScore = Integer.MIN_VALUE;
        for (ItemStack stack : this.backpack) {
            if (stack.isEmpty() || !this.isRecoveryFood(stack)) {
                continue;
            }
            int score = this.getHealingScore(stack);
            if (score > bestScore) {
                bestScore = score;
                best = stack;
            }
        }
        return best;
    }

    private int getHealingScore(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.COOKED_BEEF || item == Items.COOKED_PORKCHOP || item == Items.COOKED_MUTTON) {
            return 6;
        }
        if (item == Items.BREAD || item == Items.COOKED_CHICKEN || item == Items.COOKED_COD || item == Items.COOKED_SALMON) {
            return 4;
        }
        if (item == Items.CARROT || item == Items.POTATO || item == Items.BAKED_POTATO || item == Items.BEETROOT) {
            return 3;
        }
        return 2;
    }

    private float getHealingValue(ItemStack stack) {
        return (float)this.getHealingScore(stack);
    }

    private boolean tryCraftTorchBundle() {
        if (this.countBackpackItem(Items.TORCH) >= 8) {
            return false;
        }
        if (this.countBackpackItem(Items.STICK) <= 0) {
            return false;
        }
        Item fuel = this.countBackpackItem(Items.COAL) > 0 ? Items.COAL : (this.countBackpackItem(Items.CHARCOAL) > 0 ? Items.CHARCOAL : Items.AIR);
        if (fuel == Items.AIR) {
            return false;
        }

        this.consumeBackpackItem(fuel, 1);
        this.consumeBackpackItem(Items.STICK, 1);
        this.storeInBackpack(new ItemStack(Items.TORCH, 4));
        this.remember("合成", "制作火把");
        return true;
    }

    private boolean tryPlaceTorchForSafety() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        AIPlayerMode mode = this.safeMode();
        if (mode != AIPlayerMode.MINE && mode != AIPlayerMode.EXPLORE && mode != AIPlayerMode.SURVIVE && mode != AIPlayerMode.BUILD_SHELTER) {
            return false;
        }
        boolean darkCave = this.isInDarkCave();
        int interval = darkCave ? 20 : 40;
        if (this.tickCount % interval != 0) {
            return false;
        }
        if (darkCave) {
            this.ensureTorchSupplyForDarkness();
        }
        if (this.countBackpackItem(Items.TORCH) <= 0) {
            return false;
        }
        int brightness = serverLevel.getMaxLocalRawBrightness(this.blockPosition());
        if (brightness > (darkCave ? 7 : 5)) {
            return false;
        }

        BlockPos placePos = this.findTorchPlacement();
        if (placePos == null) {
            return false;
        }
        BlockState torchState = Blocks.TORCH.defaultBlockState();
        if (!torchState.canSurvive(serverLevel, placePos)) {
            return false;
        }

        serverLevel.setBlock(placePos, torchState, 3);
        WorldScanner.invalidateAt(serverLevel, placePos);
        this.consumeBackpackItem(Items.TORCH, 1);
        this.lastObservation = "已放置火把@" + this.formatPos(placePos);
        this.remember("照明", this.lastObservation);
        return true;
    }

    private void ensureTorchSupplyForDarkness() {
        if (this.countBackpackItem(Items.TORCH) >= 8) {
            return;
        }
        this.tryCraftTorchBundle();
    }

    private boolean isInDarkCave() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        BlockPos pos = this.blockPosition();
        if (serverLevel.canSeeSky(pos)) {
            return false;
        }
        int brightness = serverLevel.getMaxLocalRawBrightness(pos);
        return brightness <= 7;
    }

    private BlockPos findTorchPlacement() {
        BlockPos origin = this.blockPosition();
        for (int y = -1; y <= 1; y++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = origin.offset(direction.getStepX(), y, direction.getStepZ());
                if (this.level().getBlockState(candidate).isAir() && !this.level().getBlockState(candidate.below()).isAir()) {
                    return candidate;
                }
            }
        }
        return this.level().getBlockState(origin).isAir() && !this.level().getBlockState(origin.below()).isAir() ? origin : null;
    }

    private void autoSortBackpack() {
        if (this.tickCount % 40 != 0) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : this.backpack) {
            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
        }
        items.sort(Comparator
                .comparingInt(this::getBackpackSortCategory)
                .thenComparing(stack -> this.getItemPath(stack.getItem()))
                .thenComparingInt(ItemStack::getCount).reversed());
        for (int index = 0; index < this.backpack.size(); index++) {
            this.backpack.set(index, index < items.size() ? items.get(index) : ItemStack.EMPTY);
        }
    }

    private int getBackpackSortCategory(ItemStack stack) {
        Item item = stack.getItem();
        if (this.isRecoveryFood(stack)) {
            return 0;
        }
        if (item == Items.TORCH) {
            return 1;
        }
        if (this.isCombatOrToolItem(item) || this.isArmorItem(item)) {
            return 2;
        }
        if (this.isLogItem(stack) || this.isPlankItem(item)) {
            return 3;
        }
        if (this.isOreMaterial(item)
                || this.isInterestingOre(item instanceof BlockItem blockItem ? blockItem.getBlock().defaultBlockState() : Blocks.AIR.defaultBlockState())) {
            return 4;
        }
        return 5;
    }

    private void autoEquipBestAvailableGear() {
        if (this.tickCount % 20 != 0) {
            return;
        }
        this.equipBestMainHand();
        this.equipBestArmorPiece(EquipmentSlot.HEAD, Items.LEATHER_HELMET);
        this.equipBestArmorPiece(EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE);
        this.equipBestArmorPiece(EquipmentSlot.LEGS, Items.LEATHER_LEGGINGS);
        this.equipBestArmorPiece(EquipmentSlot.FEET, Items.LEATHER_BOOTS);
        if (this.countBackpackItem(Items.SHIELD) > 0 || this.getOffhandItem().isEmpty()) {
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        }
    }

    private void equipBestMainHand() {
        ItemStack best = switch (this.safeMode()) {
            case GATHER_WOOD, BUILD_SHELTER -> this.findBestToolInBackpack(this::isAxeItem, Items.STONE_AXE);
            case MINE -> this.findBestToolInBackpack(this::isPickaxeItem, Items.STONE_PICKAXE);
            default -> this.findBestCombatItem();
        };
        if (!best.isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, best.copy());
        }
    }

    private ItemStack findBestToolInBackpack(Predicate<Item> matcher, Item fallback) {
        return this.findBestToolInBackpack(matcher, fallback, Integer.MIN_VALUE);
    }

    private ItemStack findBestToolInBackpack(Predicate<Item> matcher, Item fallback, int minimumScore) {
        ItemStack best = ItemStack.EMPTY;
        int bestScore = Integer.MIN_VALUE;
        for (ItemStack stack : this.backpack) {
            if (stack.isEmpty() || !matcher.test(stack.getItem())) {
                continue;
            }
            int score = this.getToolScore(stack.getItem());
            if (score < minimumScore) {
                continue;
            }
            if (score > bestScore) {
                bestScore = score;
                best = stack;
            }
        }
        if (!best.isEmpty()) {
            return best;
        }
        if (this.getToolScore(fallback) < minimumScore) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(fallback);
    }

    private ItemStack findBestToolForRequirement(String requirement) {
        if (requirement == null || requirement.isBlank()) {
            return ItemStack.EMPTY;
        }
        String normalized = requirement.toLowerCase(Locale.ROOT).trim();
        return switch (normalized) {
            case "axe" -> this.findBestToolInBackpack(this::isAxeItem, Items.STONE_AXE);
            case "sword" -> this.findBestToolInBackpack(this::isSwordItem, Items.IRON_SWORD);
            case "bow" -> this.countBackpackItem(Items.BOW) > 0 ? new ItemStack(Items.BOW) : ItemStack.EMPTY;
            case "pickaxe" -> this.findBestToolInBackpack(this::isPickaxeItem, Items.STONE_PICKAXE);
            case "stone_pickaxe" -> this.findBestToolInBackpack(this::isPickaxeItem, Items.STONE_PICKAXE, 3);
            case "iron_pickaxe" -> this.findBestToolInBackpack(this::isPickaxeItem, Items.IRON_PICKAXE, 4);
            case "diamond_pickaxe" -> this.findBestToolInBackpack(this::isPickaxeItem, Items.DIAMOND_PICKAXE, 5);
            case "netherite_pickaxe" -> this.findBestToolInBackpack(this::isPickaxeItem, Items.NETHERITE_PICKAXE, 6);
            case "shovel" -> this.findBestToolInBackpack(this::isShovelItem, Items.STONE_SHOVEL);
            case "stone_shovel" -> this.findBestToolInBackpack(this::isShovelItem, Items.STONE_SHOVEL, 3);
            case "iron_shovel" -> this.findBestToolInBackpack(this::isShovelItem, Items.IRON_SHOVEL, 4);
            case "diamond_shovel" -> this.findBestToolInBackpack(this::isShovelItem, Items.DIAMOND_SHOVEL, 5);
            case "netherite_shovel" -> this.findBestToolInBackpack(this::isShovelItem, Items.NETHERITE_SHOVEL, 6);
            case "hoe" -> this.findBestToolInBackpack(this::isHoeItem, Items.STONE_HOE);
            case "stone_hoe" -> this.findBestToolInBackpack(this::isHoeItem, Items.STONE_HOE, 3);
            case "iron_hoe" -> this.findBestToolInBackpack(this::isHoeItem, Items.IRON_HOE, 4);
            case "diamond_hoe" -> this.findBestToolInBackpack(this::isHoeItem, Items.DIAMOND_HOE, 5);
            case "netherite_hoe" -> this.findBestToolInBackpack(this::isHoeItem, Items.NETHERITE_HOE, 6);
            case "melee" -> this.findBestCombatItem();
            default -> ItemStack.EMPTY;
        };
    }

    private void ensureKnowledgePreferredTool(BlockState state, MiningMode mode) {
        if (state == null || state.isAir()) {
            return;
        }
        String requirement = KnowledgeManager.getRecommendedTool(state);
        ItemStack preferred = this.findBestToolForRequirement(requirement);
        if (!preferred.isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, preferred.copy());
            return;
        }
        if (mode == MiningMode.HARVEST_WOOD) {
            this.runtimePrepareHarvestTool(true);
            return;
        }
        if (mode == MiningMode.AUXILIARY && this.isShovelPreferredBlock(state)) {
            ItemStack shovel = this.findBestToolInBackpack(this::isShovelItem, Items.STONE_SHOVEL);
            if (!shovel.isEmpty()) {
                this.setItemSlot(EquipmentSlot.MAINHAND, shovel.copy());
                return;
            }
        }
        if (mode == MiningMode.HARVEST_ORE || mode == MiningMode.AUXILIARY) {
            this.runtimePrepareHarvestTool(false);
        }
    }

    private ItemStack findBestCombatItem() {
        ItemStack best = ItemStack.EMPTY;
        int bestScore = Integer.MIN_VALUE;
        for (ItemStack stack : this.backpack) {
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            if (!this.isSwordItem(item) && !this.isAxeItem(item)) {
                continue;
            }
            int score = this.getToolScore(item) + (this.isSwordItem(item) ? 2 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = stack;
            }
        }
        return best.isEmpty() ? new ItemStack(Items.IRON_SWORD) : best;
    }

    private void equipBestArmorPiece(EquipmentSlot slot, Item fallback) {
        ItemStack best = ItemStack.EMPTY;
        int bestScore = Integer.MIN_VALUE;
        for (ItemStack stack : this.backpack) {
            if (stack.isEmpty() || !this.matchesArmorSlot(stack.getItem(), slot)) {
                continue;
            }
            int score = this.getArmorScore(stack.getItem(), slot);
            if (score > bestScore) {
                bestScore = score;
                best = stack;
            }
        }
        this.setItemSlot(slot, best.isEmpty() ? new ItemStack(fallback) : best.copy());
    }

    private int getToolScore(Item item) {
        String path = this.getItemPath(item);
        if (path.contains("netherite")) {
            return 6;
        }
        if (path.contains("diamond")) {
            return 5;
        }
        if (path.contains("iron") || path.contains("chainmail")) {
            return 4;
        }
        if (path.contains("stone")) {
            return 3;
        }
        if (path.contains("gold")) {
            return 2;
        }
        if (path.contains("leather") || path.contains("wooden")) {
            return 1;
        }
        return 0;
    }

    private int getArmorScore(Item item, EquipmentSlot slot) {
        return this.matchesArmorSlot(item, slot) ? this.getToolScore(item) * 10 : Integer.MIN_VALUE;
    }

    private boolean isCombatOrToolItem(Item item) {
        return this.isSwordItem(item)
                || this.isAxeItem(item)
                || this.isPickaxeItem(item)
                || this.isShovelItem(item)
                || this.isHoeItem(item)
                || this.isArmorItem(item);
    }

    private boolean isSwordItem(Item item) {
        return this.getItemPath(item).endsWith("_sword");
    }

    private boolean isAxeItem(Item item) {
        return this.getItemPath(item).endsWith("_axe");
    }

    private boolean isPickaxeItem(Item item) {
        return this.getItemPath(item).endsWith("_pickaxe");
    }

    private boolean isShovelItem(Item item) {
        return this.getItemPath(item).endsWith("_shovel");
    }

    private boolean isHoeItem(Item item) {
        return this.getItemPath(item).endsWith("_hoe");
    }

    private boolean isArmorItem(Item item) {
        return this.matchesArmorSlot(item, EquipmentSlot.HEAD)
                || this.matchesArmorSlot(item, EquipmentSlot.CHEST)
                || this.matchesArmorSlot(item, EquipmentSlot.LEGS)
                || this.matchesArmorSlot(item, EquipmentSlot.FEET);
    }

    private boolean matchesArmorSlot(Item item, EquipmentSlot slot) {
        String path = this.getItemPath(item);
        return switch (slot) {
            case HEAD -> path.endsWith("_helmet");
            case CHEST -> path.endsWith("_chestplate");
            case LEGS -> path.endsWith("_leggings");
            case FEET -> path.endsWith("_boots");
            default -> false;
        };
    }

    private String getItemPath(Item item) {
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        return key == null ? "" : key.getPath();
    }

    private String getItemDisplayName(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    private boolean isRecoveryFood(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.BREAD
                || item == Items.COOKED_BEEF
                || item == Items.COOKED_PORKCHOP
                || item == Items.COOKED_MUTTON
                || item == Items.COOKED_CHICKEN
                || item == Items.COOKED_COD
                || item == Items.COOKED_SALMON
                || item == Items.BAKED_POTATO
                || item == Items.CARROT
                || item == Items.POTATO
                || item == Items.BEETROOT
                || item == Items.APPLE;
    }

    private void shareResourcesWithTeammates() {
        if (this.tickCount % 60 != 0) {
            return;
        }
        for (AIPlayerEntity teammate : AITaskCoordinator.getTeam(this, this.safeMode(), 8.0D)) {
            if (teammate == this) {
                continue;
            }
            this.tryShareItemWithTeammate(teammate, Items.BREAD, 2, 4);
            this.tryShareItemWithTeammate(teammate, Items.TORCH, 4, 8);
            this.tryShareItemWithTeammate(teammate, Items.OAK_PLANKS, 8, 16);
        }
    }

    private void tryShareItemWithTeammate(AIPlayerEntity teammate, Item item, int batchSize, int reserve) {
        int ownCount = this.countBackpackItem(item);
        if (ownCount <= reserve || teammate.countBackpackItem(item) >= reserve / 2) {
            return;
        }
        int moved = Math.min(batchSize, ownCount - reserve);
        if (moved <= 0 || !this.consumeBackpackItem(item, moved)) {
            return;
        }
        teammate.storeInBackpack(new ItemStack(item, moved));
        String itemName = this.getItemDisplayName(item);
        teammate.remember("协作", "收到共享物资 " + itemName + "x" + moved);
        this.remember("协作", "向队友共享 " + itemName + "x" + moved);
    }

    private void coordinateBuildTeamRoles() {
        if (this.safeMode() != AIPlayerMode.BUILD_SHELTER) {
            return;
        }
        int teamSize = AITaskCoordinator.getTeamSize(this, AIPlayerMode.BUILD_SHELTER, 20.0D);
        if (teamSize <= 1) {
            return;
        }
        int slot = AITaskCoordinator.getTeamSlot(this, AIPlayerMode.BUILD_SHELTER, 20.0D);
        if (slot % 3 == 1 && this.countAvailableBuildingUnits() < 16) {
            this.lastObservation = "队伍分工：我去补建筑材料。";
            this.performHarvestTask(true);
        } else if (slot % 3 == 2 && this.observedHostile == null) {
            ServerPlayer owner = this.getOwnerPlayer();
            if (owner != null) {
                this.performFollow(true);
            }
        }
    }

    private String handleDeliveryRequest(ServerPlayer speaker, String content) {
        DeliveryRequest request = this.parseDeliveryRequest(content);
        if (request == null) {
            return "我没听懂你想要哪种物品。你可以说：把木头给我、给我 16 个原木、把圆石给我。";
        }

        int available = this.countMatchingBackpackItems(request.matcher());
        if (available <= 0) {
            return "我的背包里没有" + request.label() + "。当前背包：" + this.getInventoryPreview();
        }

        this.assignOwner(speaker);
        this.pendingDeliveryReceiverId = speaker.getUUID();
        this.pendingDeliveryRequest = content;
        this.lastObservation = "收到交付请求，准备把" + request.label() + "送给" + speaker.getName().getString() + "。";
        this.markPersistentDirty();

        if (request.deliverAll()) {
            return "收到，我会把背包里的" + request.label() + "都拿给你，共 " + available + " 个。";
        }

        int deliverCount = Math.min(request.requestedCount(), available);
        if (deliverCount < request.requestedCount()) {
            return "收到，我背包里只有 " + available + " 个" + request.label() + "，先全部拿给你。";
        }
        return "收到，我给你送 " + deliverCount + " 个" + request.label() + "。";
    }

    private boolean performPendingDeliveryTask() {
        if (this.pendingDeliveryReceiverId == null || this.pendingDeliveryRequest == null || this.pendingDeliveryRequest.isBlank()) {
            return false;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            this.clearPendingDelivery();
            return false;
        }

        Player player = serverLevel.getPlayerByUUID(this.pendingDeliveryReceiverId);
        if (!(player instanceof ServerPlayer receiver) || !receiver.isAlive()) {
            this.lastObservation = "收货玩家暂时不在，已取消交付。";
            this.reportTaskFailure(this.activeTaskName, "收货玩家暂时不在，取消交付");
            this.clearPendingDelivery();
            return false;
        }

        DeliveryRequest request = this.parseDeliveryRequest(this.pendingDeliveryRequest);
        if (request == null) {
            this.lastObservation = "未能重新识别交付物品，已取消。";
            this.reportTaskFailure(this.activeTaskName, "未能解析交付物品，取消交付");
            this.clearPendingDelivery();
            return false;
        }

        int available = this.countMatchingBackpackItems(request.matcher());
        if (available <= 0) {
            this.lastObservation = "背包里已经没有" + request.label() + "，交付结束。";
            this.reportTaskFailure(this.activeTaskName, "背包里没有可交付的" + request.label());
            this.clearPendingDelivery();
            return false;
        }

        int deliverCount = request.deliverAll() ? available : Math.min(request.requestedCount(), available);
        double distanceSqr = this.distanceToSqr(receiver);
        if (distanceSqr >= TELEPORT_FOLLOW_DISTANCE_SQR && this.tryTeleportNearPlayer(receiver, "距离过远，已快速赶去交付@")) {
            this.reportTaskProgress(this.activeTaskName, "距离过远，已快速靠近 " + receiver.getName().getString() + " 进行交付");
            return true;
        }

        if (distanceSqr > 9.0D) {
            BlockPos receiverGoal = this.findWalkablePositionNear(receiver.blockPosition(), 2, 3);
            if (receiverGoal != null) {
                this.navigateToPosition(receiverGoal, FAST_FOLLOW_SPEED);
            } else if (!this.navigateToPosition(receiver.blockPosition(), FAST_FOLLOW_SPEED)) {
                this.reportTaskFailure(this.activeTaskName, "无法靠近 " + receiver.getName().getString() + " 完成交付");
            }
            this.setForcedLookTarget(receiver.getEyePosition(), 10);
            this.lastObservation = "正在靠近" + receiver.getName().getString() + "并准备交付" + request.label() + "。";
            return true;
        }

        List<ItemStack> extracted = this.extractMatchingBackpackItems(request.matcher(), deliverCount);
        if (extracted.isEmpty()) {
            this.lastObservation = "准备交付时没有找到可用物品。";
            this.reportTaskFailure(this.activeTaskName, "准备交付时未提取到 " + request.label());
            this.clearPendingDelivery();
            return false;
        }

        int totalGiven = 0;
        for (ItemStack stack : extracted) {
            totalGiven += this.transferStackToPlayer(receiver, stack);
        }

        this.lastObservation = "已向" + receiver.getName().getString() + "交付 " + totalGiven + " 个" + request.label() + "。";
        this.remember("交付", this.lastObservation);
        this.reportTaskProgress(this.activeTaskName, "成功向 " + receiver.getName().getString() + " 交付 " + totalGiven + " 个" + request.label());
        receiver.sendSystemMessage(Component.literal("[" + this.getAIName() + "] 已交付 " + totalGiven + " 个" + request.label() + "。"));
        this.clearPendingDelivery();
        this.markPersistentDirty();
        return true;
    }

    private void clearPendingDelivery() {
        this.pendingDeliveryReceiverId = null;
        this.pendingDeliveryRequest = null;
    }

    private DeliveryRequest parseDeliveryRequest(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        String normalized = message.toLowerCase().trim();
        if (!containsAnyToken(normalized, "给我", "交给我", "给一下", "拿给我", "递给我", "give me", "bring me")) {
            return null;
        }

        boolean deliverAll = containsAnyToken(normalized, "全部", "所有", "都给我", "all");
        int requestedCount = this.extractRequestedCount(normalized);
        if (requestedCount <= 0) {
            deliverAll = true;
        }

        if (containsAnyToken(normalized, "橡木原木", "oak log", "oak_log")) {
            return new DeliveryRequest("橡木原木", deliverAll, requestedCount, stack -> stack.getItem() == Items.OAK_LOG);
        }
        if (containsAnyToken(normalized, "橡木木板", "oak plank", "oak_planks")) {
            return new DeliveryRequest("橡木木板", deliverAll, requestedCount, stack -> stack.getItem() == Items.OAK_PLANKS);
        }
        if (containsAnyToken(normalized, "圆石", "cobblestone", "cobble")) {
            return new DeliveryRequest("圆石", deliverAll, requestedCount, stack -> stack.getItem() == Items.COBBLESTONE);
        }
        if (containsAnyToken(normalized, "木板", "板材", "plank", "planks")) {
            return new DeliveryRequest("木板", deliverAll, requestedCount, stack -> this.isPlankItem(stack.getItem()));
        }
        if (containsAnyToken(normalized, "木头", "原木", "log", "wood")) {
            return new DeliveryRequest("木头", deliverAll, requestedCount, stack -> this.isWoodItem(stack.getItem()));
        }
        if (containsAnyToken(normalized, "煤炭", "木炭", "煤", "coal", "charcoal")) {
            return new DeliveryRequest("煤炭", deliverAll, requestedCount, stack -> stack.getItem() == Items.COAL || stack.getItem() == Items.CHARCOAL);
        }
        if (containsAnyToken(normalized, "铁锭", "iron ingot")) {
            return new DeliveryRequest("铁锭", deliverAll, requestedCount, stack -> stack.getItem() == Items.IRON_INGOT);
        }
        if (containsAnyToken(normalized, "粗铁", "生铁", "raw iron")) {
            return new DeliveryRequest("粗铁", deliverAll, requestedCount, stack -> stack.getItem() == Items.RAW_IRON);
        }
        if (containsAnyToken(normalized, "面包", "bread")) {
            return new DeliveryRequest("面包", deliverAll, requestedCount, stack -> stack.getItem() == Items.BREAD);
        }
        if (containsAnyToken(normalized, "小麦", "wheat")) {
            return new DeliveryRequest("小麦", deliverAll, requestedCount, stack -> stack.getItem() == Items.WHEAT);
        }
        if (containsAnyToken(normalized, "木棍", "stick", "sticks")) {
            return new DeliveryRequest("木棍", deliverAll, requestedCount, stack -> stack.getItem() == Items.STICK);
        }
        if (containsAnyToken(normalized, "石镐", "石头镐", "pickaxe", "stone_pickaxe")) {
            return new DeliveryRequest("石镐", deliverAll, requestedCount, stack -> stack.getItem() == Items.STONE_PICKAXE);
        }
        if (containsAnyToken(normalized, "石斧", "斧头", "axe", "stone_axe")) {
            return new DeliveryRequest("石斧", deliverAll, requestedCount, stack -> stack.getItem() == Items.STONE_AXE);
        }
        if (containsAnyToken(normalized, "盾牌", "shield")) {
            return new DeliveryRequest("盾牌", deliverAll, requestedCount, stack -> stack.getItem() == Items.SHIELD);
        }
        if (containsAnyToken(normalized, "铁剑", "剑", "sword", "iron_sword")) {
            return new DeliveryRequest("铁剑", deliverAll, requestedCount, stack -> stack.getItem() == Items.IRON_SWORD);
        }
        if (containsAnyToken(normalized, "矿物", "矿石", "ore", "minerals")) {
            return new DeliveryRequest("矿物", deliverAll, requestedCount, stack -> this.isOreMaterial(stack.getItem()));
        }
        return null;
    }

    private int countMatchingBackpackItems(Predicate<ItemStack> matcher) {
        int total = 0;
        for (ItemStack stack : this.backpack) {
            if (!stack.isEmpty() && matcher.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private List<ItemStack> extractMatchingBackpackItems(Predicate<ItemStack> matcher, int count) {
        List<ItemStack> extracted = new ArrayList<>();
        int remaining = count;

        for (int index = 0; index < this.backpack.size() && remaining > 0; index++) {
            ItemStack stack = this.backpack.get(index);
            if (stack.isEmpty() || !matcher.test(stack)) {
                continue;
            }

            int takenCount = Math.min(stack.getCount(), remaining);
            ItemStack taken = stack.copy();
            taken.setCount(takenCount);
            stack.shrink(takenCount);
            if (stack.isEmpty()) {
                this.backpack.set(index, ItemStack.EMPTY);
            }
            extracted.add(taken);
            remaining -= takenCount;
        }

        if (!extracted.isEmpty()) {
            this.markPersistentDirty();
        }
        return extracted;
    }

    private int transferStackToPlayer(ServerPlayer receiver, ItemStack stack) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return 0;
        }

        ItemStack remaining = stack.copy();
        receiver.getInventory().add(remaining);
        receiver.containerMenu.broadcastChanges();
        if (!remaining.isEmpty()) {
            ItemEntity itemEntity = new ItemEntity(serverLevel, receiver.getX(), receiver.getY() + 0.5D, receiver.getZ(), remaining.copy());
            itemEntity.setDefaultPickUpDelay();
            itemEntity.setDeltaMovement(receiver.getLookAngle().scale(0.05D).add(0.0D, 0.12D, 0.0D));
            serverLevel.addFreshEntity(itemEntity);
        }
        return stack.getCount();
    }

    private int extractRequestedCount(String text) {
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        if (containsAnyToken(text, "一组", "一组的", "一组吧")) {
            return 64;
        }
        if (containsAnyToken(text, "一个", "一份", "一块", "一根", "一把", "一柄")) {
            return 1;
        }
        if (containsAnyToken(text, "两个", "两块", "两根", "两把")) {
            return 2;
        }
        if (containsAnyToken(text, "三个")) {
            return 3;
        }
        if (containsAnyToken(text, "四个")) {
            return 4;
        }
        if (containsAnyToken(text, "五个")) {
            return 5;
        }
        if (containsAnyToken(text, "十个")) {
            return 10;
        }
        if (containsAnyToken(text, "半组")) {
            return 32;
        }
        return -1;
    }

    private boolean isWoodItem(Item item) {
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        if (key == null) {
            return false;
        }
        String path = key.getPath();
        return path.endsWith("_log") || path.endsWith("_stem") || path.endsWith("_hyphae");
    }

    private boolean isPlankItem(Item item) {
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        return key != null && key.getPath().endsWith("_planks");
    }

    private boolean isOreMaterial(Item item) {
        return item == Items.COAL
                || item == Items.CHARCOAL
                || item == Items.RAW_IRON
                || item == Items.RAW_COPPER
                || item == Items.RAW_GOLD
                || item == Items.DIAMOND
                || item == Items.EMERALD
                || item == Items.REDSTONE
                || item == Items.LAPIS_LAZULI
                || item == Items.QUARTZ
                || item == Items.NETHERITE_SCRAP;
    }

    private static boolean containsAnyToken(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean autoRecoverFromHazards() {
        if (!this.isInWater() && !this.isUnderWater() && !this.isInLava()) {
            return false;
        }

        BlockPos safePos = this.findNearbyDryStandPosition(this.blockPosition(), 6, 4);
        if (safePos != null) {
            this.navigateToPosition(safePos, FAST_FOLLOW_SPEED);
            this.nudgeToward(Vec3.atCenterOf(safePos), 0.18D, 0.30D);
            this.lastObservation = this.isInLava() ? "掉进危险液体，正在紧急脱困。" : "正在脱离水域。";
            this.reportTaskProgress(this.activeTaskName, this.isInLava() ? "已找到危险液体脱困方向" : "已找到水域脱困方向");
            if (this.stuckNavigationTicks >= NAVIGATION_STUCK_THRESHOLD && this.distanceToSqr(Vec3.atCenterOf(safePos)) > 9.0D) {
                this.emergencyReposition(safePos, this.isInLava() ? "危险液体中强制脱困@" : "水中强制脱困@");
                return true;
            }
        } else {
            this.lastObservation = this.isInLava() ? "掉进危险液体，正在尝试上浮。" : "暂时没有找到干燥落脚点，正在上浮。";
            this.reportTaskFailure(this.activeTaskName, this.isInLava() ? "未找到危险液体安全点，改为上浮脱困" : "未找到干燥落脚点，改为上浮脱困");
        }

        if (this.jumpCooldown <= 0) {
            this.getJumpControl().jump();
            this.jumpCooldown = 10;
        }
        return true;
    }

    private boolean attemptImmediateRecovery() {
        if (this.autoRecoverFromHazards()) {
            return true;
        }

        BlockPos blocker = null;
        if (this.activeNavigationTarget != null) {
            blocker = this.findNavigationObstacle(this.activeNavigationTarget);
        }
        if (blocker == null) {
            if (this.safeMode() == AIPlayerMode.GATHER_WOOD && this.rememberedLog != null) {
                blocker = this.findHarvestObstacle(this.rememberedLog, true);
            } else if (this.safeMode() == AIPlayerMode.MINE && this.rememberedOre != null) {
                blocker = this.findHarvestObstacle(this.rememberedOre, false);
            }
        }

        if (blocker != null && this.canHarvestFromHere(blocker) && this.breakAuxiliaryBlock(blocker, "已清理路径障碍@")) {
            this.reportTaskProgress(this.activeTaskName, "已清理路径障碍，继续脱困");
            return true;
        }
        if (blocker != null && this.tryOpenToggleablePathBlock(blocker, "已开启脱困通道@")) {
            this.reportTaskProgress(this.activeTaskName, "已开启门/活板门，继续脱困");
            return true;
        }

        if (blocker != null) {
            BlockPos obstacleApproach = this.findApproachPosition(blocker);
            if (obstacleApproach != null && this.tryStartNavigation(obstacleApproach, FAST_FOLLOW_SPEED)) {
                this.lastObservation = "靠近障碍并准备脱困@" + this.formatPos(blocker);
                this.reportTaskProgress(this.activeTaskName, "已靠近障碍，准备继续脱困");
                return true;
            }
        }

        if (this.activeNavigationTarget != null) {
            BlockPos detour = this.findRecoveryDetour(this.activeNavigationTarget);
            if (detour != null && this.tryStartNavigation(detour, FAST_FOLLOW_SPEED)) {
                this.lastObservation = "路径卡住，尝试侧向绕行@" + this.formatPos(detour);
                this.reportTaskProgress(this.activeTaskName, "路径卡住，已尝试侧向绕行");
                return true;
            }
            if (this.stuckNavigationTicks >= NAVIGATION_STUCK_THRESHOLD * 2 && detour != null) {
                return this.emergencyReposition(detour, "严重卡住，已强制脱困@");
            }
        }

        boolean likelyStuck = this.horizontalCollision
                || this.stuckNavigationTicks >= SCAN_INTERVAL * 2
                || this.agentRuntime.movementController().isRecovering();
        if (likelyStuck && this.activeNavigationTarget != null && this.onGround() && this.jumpCooldown <= 0) {
            if (this.tryDiggableAdvance(this.activeNavigationTarget)) {
                this.reportTaskProgress(this.activeTaskName, "已尝试挖掘前方障碍，继续脱困");
                return true;
            }
            if (this.tryBuildClimbSupportToward(this.activeNavigationTarget, "已搭建脱困台阶@")) {
                this.reportTaskProgress(this.activeTaskName, "已搭建脱困台阶，继续前进");
                return true;
            }
            if (this.tryPillarUpToward(this.activeNavigationTarget, "已脚底垫块脱困@")) {
                this.reportTaskProgress(this.activeTaskName, "已脚底垫块抬升，继续脱困");
                return true;
            }
            this.performAction(AIPlayerAction.JUMP);
            this.nudgeToward(Vec3.atCenterOf(this.activeNavigationTarget), 0.22D, 0.32D);
            this.lastObservation = "尝试跳跃脱困。";
            this.reportTaskFailure(this.activeTaskName, "仍被地形卡住，尝试跳跃脱困");
            return true;
        }

        return false;
    }

    private boolean tryDiggableAdvance(BlockPos targetPos) {
        if (targetPos == null) {
            return false;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!serverLevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return false;
        }

        // First clear the direct blocker, then try forward/stair candidates.
        BlockPos blocker = this.findNavigationObstacle(targetPos);
        if (blocker != null && this.tryDiggableCandidate(blocker, "已挖通路径障碍@")) {
            return true;
        }

        BlockPos self = this.blockPosition();
        Direction facing = this.resolveDigDirection(targetPos);
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos front = self.relative(facing);
        candidates.add(front);
        candidates.add(front.above());
        if (targetPos.getY() > self.getY() + 1) {
            candidates.add(self.above());
            candidates.add(front.above());
        }
        if (targetPos.getY() < self.getY() - 1) {
            candidates.add(front.below());
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            candidates.add(self.relative(direction).above());
        }

        Set<BlockPos> dedupe = new HashSet<>();
        for (BlockPos candidate : candidates) {
            if (candidate == null || !dedupe.add(candidate)) {
                continue;
            }
            if (this.tryDiggableCandidate(candidate, "已挖通前方道路@")) {
                return true;
            }
        }
        return false;
    }

    private boolean tryDiggableCandidate(BlockPos candidate, String observationPrefix) {
        if (candidate == null || !this.runtimeCanBreakPathBlock(candidate)) {
            return false;
        }
        if (this.canHarvestFromHere(candidate)) {
            boolean completed = this.breakAuxiliaryBlock(candidate, observationPrefix);
            return completed || this.isMiningInProgress(candidate, MiningMode.AUXILIARY);
        }
        BlockPos approach = this.findApproachPosition(candidate);
        if (approach != null && this.tryStartNavigation(approach, FAST_FOLLOW_SPEED)) {
            this.lastObservation = "前往可挖通路点@" + this.formatPos(candidate);
            return true;
        }
        return false;
    }

    private Direction resolveDigDirection(BlockPos targetPos) {
        Vec3 delta = Vec3.atCenterOf(targetPos).subtract(this.position());
        double horizontal = delta.x * delta.x + delta.z * delta.z;
        if (horizontal < 1.0E-4D) {
            return Direction.fromYRot(this.getYRot());
        }
        if (Math.abs(delta.x) >= Math.abs(delta.z)) {
            return delta.x >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        return delta.z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private boolean tryBuildClimbSupportToward(BlockPos targetPos, String observationPrefix) {
        if (targetPos == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!serverLevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return false;
        }
        if (!this.runtimeHasPathSupportBlocks()) {
            return false;
        }
        BlockPos self = this.blockPosition();
        if (targetPos.getY() <= self.getY()) {
            return false;
        }
        Direction direction = this.resolveDigDirection(targetPos);
        List<BlockPos> candidates = List.of(
                self.relative(direction),
                self.relative(direction).above(),
                self.relative(direction).above().relative(direction));
        for (BlockPos candidate : candidates) {
            if (!this.runtimeCanPlacePathSupport(candidate)) {
                continue;
            }
            if (!this.runtimePlacePathSupport(candidate)) {
                continue;
            }
            this.lastObservation = observationPrefix + this.formatPos(candidate);
            if (this.onGround() && this.jumpCooldown <= 0) {
                this.getJumpControl().jump();
                this.jumpCooldown = 8;
            }
            return true;
        }
        return false;
    }

    private boolean tryPillarUpToward(BlockPos targetPos, String observationPrefix) {
        if (targetPos == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!serverLevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return false;
        }
        if (!this.onGround() || this.jumpCooldown > 0) {
            return false;
        }
        BlockPos self = this.blockPosition();
        if (targetPos.getY() <= self.getY() + 1) {
            return false;
        }
        if (this.distanceToSqr(Vec3.atCenterOf(targetPos)) > 25.0D) {
            return false;
        }
        if (!this.runtimeCanPlacePathSupport(self)) {
            return false;
        }
        BlockPos nextStand = self.above();
        BlockState feet = this.level().getBlockState(nextStand);
        BlockState head = this.level().getBlockState(nextStand.above());
        if (!feet.getCollisionShape(this.level(), nextStand).isEmpty()
                || !head.getCollisionShape(this.level(), nextStand.above()).isEmpty()
                || this.level().getFluidState(nextStand).is(FluidTags.LAVA)
                || this.level().getFluidState(nextStand.above()).is(FluidTags.LAVA)) {
            return false;
        }
        if (!this.runtimePlacePathSupport(self)) {
            return false;
        }
        if (!this.canStandAt(nextStand, true)) {
            return false;
        }
        this.teleportTo(this.getX(), nextStand.getY(), this.getZ());
        this.getNavigation().stop();
        this.resetNavigationState();
        this.jumpCooldown = 6;
        this.lastObservation = observationPrefix + this.formatPos(self);
        return true;
    }

    private boolean tryNavigationRecovery(boolean severe) {
        if (this.attemptImmediateRecovery()) {
            return true;
        }
        if (this.activeNavigationTarget == null) {
            return false;
        }

        BlockPos fallback = this.findRecoveryDetour(this.activeNavigationTarget);
        if (fallback != null && this.tryStartNavigation(fallback, FAST_FOLLOW_SPEED)) {
            this.lastObservation = severe ? "路径失败，正在强制绕行@" + this.formatPos(fallback) : "路径受阻，重新绕行@" + this.formatPos(fallback);
            return true;
        }

        if (severe && fallback != null) {
            return this.emergencyReposition(fallback, "长时间卡住，已强制重定位@");
        }
        return false;
    }

    private BlockPos findRecoveryDetour(BlockPos target) {
        BlockPos approach = this.findApproachPosition(target);
        if (approach != null) {
            return approach;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int step = 1; step <= 3; step++) {
                BlockPos candidate = this.findWalkablePositionNear(this.blockPosition().relative(direction, step), 2, 2);
                if (candidate == null) {
                    continue;
                }
                double score = candidate.distSqr(target) * 2.0D + this.distanceToSqr(Vec3.atCenterOf(candidate));
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best != null ? best : this.findWalkablePositionNear(target, 4, 4);
    }

    private boolean emergencyReposition(BlockPos pos, String observationPrefix) {
        if (pos == null) {
            return false;
        }
        this.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        this.getNavigation().stop();
        this.resetNavigationState();
        this.lastObservation = observationPrefix + this.formatPos(pos);
        return true;
    }

    private void nudgeToward(Vec3 target, double horizontalSpeed, double verticalSpeed) {
        Vec3 delta = target.subtract(this.position());
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        if (horizontal.lengthSqr() > 1.0E-4D) {
            double moveSpeed = Math.max(0.85D, horizontalSpeed * 5.0D);
            this.facePosition(target);
            this.runtimeClearWasdOverride();
            this.getMoveControl().setWantedPosition(target.x, target.y, target.z, moveSpeed);
            if ((this.isInWater() || this.isUnderWater() || this.isInLava()) && this.jumpCooldown <= 0) {
                this.getJumpControl().jump();
                this.jumpCooldown = 8;
            }
        }
    }

    private boolean tryTeleportNearPlayer(ServerPlayer player, String observationPrefix) {
        BlockPos destination = this.findWalkablePositionNear(player.blockPosition(), 3, 4);
        if (destination == null) {
            destination = this.findNearbyDryStandPosition(player.blockPosition(), 4, 4);
        }
        if (destination == null) {
            return false;
        }

        this.teleportTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D);
        this.getNavigation().stop();
        this.resetNavigationState();
        this.lastObservation = observationPrefix + this.formatPos(destination);
        return true;
    }

    private boolean tryTeleportNearOwnerLikeWolf(ServerPlayer owner, String observationPrefix) {
        BlockPos ownerPos = owner.blockPosition();
        for (int attempt = 0; attempt < 10; attempt++) {
            int offsetX = this.random.nextInt(7) - 3;
            int offsetY = this.random.nextInt(3) - 1;
            int offsetZ = this.random.nextInt(7) - 3;
            if (Math.abs(offsetX) < 2 && Math.abs(offsetZ) < 2) {
                continue;
            }
            BlockPos candidate = ownerPos.offset(offsetX, offsetY, offsetZ);
            if (!this.canTeleportNearOwner(candidate, ownerPos)) {
                continue;
            }
            this.teleportTo(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
            this.getNavigation().stop();
            this.resetNavigationState();
            this.setForcedLookTarget(owner.getEyePosition(), 10);
            this.lastObservation = observationPrefix + this.formatPos(candidate);
            return true;
        }
        return this.tryTeleportNearPlayer(owner, observationPrefix);
    }

    private boolean tryTeleportToOwnerIfTooFar(String observationPrefix) {
        ServerPlayer owner = this.getOwnerPlayer();
        if (owner == null || owner.isRemoved() || owner.isSpectator()) {
            return false;
        }
        if (this.distanceToSqr(owner) < OWNER_TELEPORT_FOLLOW_DISTANCE_SQR) {
            return false;
        }
        if (this.tryTeleportNearOwnerLikeWolf(owner, observationPrefix)) {
            this.reportTaskProgress(this.activeTaskName, "与主人距离过远，已执行瞬移跟随");
            return true;
        }
        BlockPos fallback = this.findWalkablePositionNear(owner.blockPosition(), 8, 6, true);
        if (fallback == null) {
            fallback = this.findWalkablePositionNear(owner.blockPosition(), 12, 8, true);
        }
        if (fallback == null) {
            fallback = owner.blockPosition();
        }
        this.teleportTo(fallback.getX() + 0.5D, fallback.getY(), fallback.getZ() + 0.5D);
        this.getNavigation().stop();
        this.resetNavigationState();
        this.setForcedLookTarget(owner.getEyePosition(), 12);
        this.lastObservation = observationPrefix + this.formatPos(fallback);
        this.reportTaskProgress(this.activeTaskName, "与主人距离过远，已使用兜底瞬移");
        return true;
    }

    private boolean canTeleportNearOwner(BlockPos candidate, BlockPos ownerPos) {
        if (candidate == null) {
            return false;
        }
        if (Math.abs(candidate.getX() - ownerPos.getX()) < 2 && Math.abs(candidate.getZ() - ownerPos.getZ()) < 2) {
            return false;
        }
        if (!this.canTeleportToLikeWolf(candidate)) {
            return false;
        }
        BlockPos movement = candidate.subtract(this.blockPosition());
        return this.level().noCollision(this, this.getBoundingBox().move(movement));
    }

    private boolean canTeleportToLikeWolf(BlockPos pos) {
        BlockPos.MutableBlockPos mutable = pos.mutable();
        PathType pathType = WalkNodeEvaluator.getPathTypeStatic(this, mutable);
        if (pathType != PathType.WALKABLE) {
            return false;
        }
        BlockState below = this.level().getBlockState(pos.below());
        if (below.getBlock() instanceof LeavesBlock) {
            return false;
        }
        return this.canStandAt(pos);
    }

    private BlockPos findNearbyDryStandPosition(BlockPos center, int horizontalRadius, int verticalRadius) {
        BlockPos bestPos = null;
        double bestScore = Double.MAX_VALUE;

        for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
            for (int y = -verticalRadius; y <= verticalRadius; y++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    BlockPos candidate = center.offset(x, y, z);
                    if (!this.canStandDryAt(candidate)) {
                        continue;
                    }

                    double distance = this.distanceToSqr(Vec3.atCenterOf(candidate));
                    if (distance < bestScore) {
                        bestScore = distance;
                        bestPos = candidate;
                    }
                }
            }
        }

        return bestPos;
    }

    private boolean canStandDryAt(BlockPos pos) {
        return this.canStandAt(pos)
                && !this.level().getFluidState(pos).is(FluidTags.WATER)
                && !this.level().getFluidState(pos.above()).is(FluidTags.WATER)
                && !this.level().getFluidState(pos).is(FluidTags.LAVA)
                && !this.level().getFluidState(pos.above()).is(FluidTags.LAVA);
    }

    private BlockPos findNavigationObstacle(BlockPos target) {
        BlockPos blocker = this.findBlockingBlockOnLine(target, LOCAL_BLOCKER_CHECK_DISTANCE);
        if (blocker == null) {
            return null;
        }
        BlockState state = this.level().getBlockState(blocker);
        return (this.isBreakableNavigationObstacle(state) || this.isToggleablePathBlock(state)) ? blocker : null;
    }

    private BlockPos findHarvestObstacle(BlockPos target, boolean woodTask) {
        if (target == null) {
            return null;
        }
        double maxDistance = LOCAL_BLOCKER_CHECK_DISTANCE + 0.75D;
        BlockPos blocker = this.findBlockingBlockOnLine(target, maxDistance);
        if (blocker != null && this.isBreakableHarvestObstacle(this.level().getBlockState(blocker), woodTask)) {
            return blocker;
        }
        if (this.distanceToSqr(Vec3.atCenterOf(target)) > maxDistance * maxDistance) {
            return null;
        }
        return this.isExposed(target) ? null : this.findAdjacentHarvestCover(target, woodTask);
    }

    private BlockPos findBlockingBlockOnLine(BlockPos target, double maxDistance) {
        if (target == null) {
            return null;
        }
        Vec3 targetCenter = Vec3.atCenterOf(target);
        Vec3 eye = this.getEyePosition();
        if (eye.distanceTo(targetCenter) > Math.max(1.0D, maxDistance)) {
            return null;
        }
        BlockHitResult hitResult = this.level().clip(new ClipContext(eye, targetCenter, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos hitPos = hitResult.getBlockPos();
        return hitPos.equals(target) ? null : hitPos;
    }

    private BlockPos findAdjacentHarvestCover(BlockPos target, boolean woodTask) {
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            BlockPos candidate = target.relative(direction);
            BlockState candidateState = this.level().getBlockState(candidate);
            if (!this.isBreakableHarvestObstacle(candidateState, woodTask)) {
                continue;
            }
            if (!this.hasOpenFaceExcluding(candidate, target)) {
                continue;
            }

            double distance = this.distanceToSqr(Vec3.atCenterOf(candidate));
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPos = candidate;
            }
        }

        return bestPos;
    }

    private BlockPos findConnectedHarvestTarget(BlockPos origin, boolean woodTask) {
        if (origin == null) {
            return null;
        }
        if (woodTask) {
            if (!(this.level() instanceof ServerLevel serverLevel)) {
                return null;
            }
            BlockPos seed = this.normalizeHarvestTarget(origin, true);
            BlockPos nextTarget = TreeChopper.nextTreeLog(
                    serverLevel,
                    seed,
                    candidate -> !candidate.equals(origin)
                            && this.isValidHarvestTarget(candidate, true)
                            && this.isHarvestTargetReachable(candidate, true));
            if (nextTarget != null) {
                return nextTarget.immutable();
            }
            SearchBlocks.resetVisited();
            return this.resolveHarvestTarget(true, this.findNearestHarvestBlock(true));
        }

        BlockPos bestPos = null;
        double bestScore = Double.MAX_VALUE;

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    BlockPos candidate = origin.offset(x, y, z);
                    if (!this.isValidHarvestTarget(candidate, woodTask)) {
                        continue;
                    }

                    double score = origin.distSqr(candidate) + this.distanceToSqr(Vec3.atCenterOf(candidate));
                    if (score < bestScore) {
                        bestScore = score;
                        bestPos = candidate;
                    }
                }
            }
        }

        return bestPos;
    }

    private boolean hasOpenFaceExcluding(BlockPos pos, BlockPos excludedNeighbor) {
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.relative(direction);
            if (adjacent.equals(excludedNeighbor)) {
                continue;
            }
            if (this.level().isEmptyBlock(adjacent)) {
                return true;
            }
        }
        return false;
    }

    private boolean isToggleablePathBlock(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock;
    }

    private boolean tryOpenToggleablePathBlock(BlockPos pos, String observationPrefix) {
        if (pos == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        BlockState state = serverLevel.getBlockState(pos);
        if (!this.isToggleablePathBlock(state)) {
            return false;
        }
        if (!state.hasProperty(BlockStateProperties.OPEN)) {
            return false;
        }
        if (Boolean.TRUE.equals(state.getValue(BlockStateProperties.OPEN))) {
            return true;
        }
        BlockState opened = state.setValue(BlockStateProperties.OPEN, true);
        serverLevel.setBlock(pos, opened, 10);
        WorldScanner.invalidateAt(serverLevel, pos);
        this.lastObservation = observationPrefix + this.formatPos(pos);
        return true;
    }

    private boolean isBreakableNavigationObstacle(BlockState state) {
        return this.isBreakableMiningCover(state) || state.is(BlockTags.LEAVES);
    }

    private boolean isBreakableHarvestObstacle(BlockState state, boolean woodTask) {
        if (state.isAir() || state.getDestroySpeed(this.level(), BlockPos.ZERO) < 0.0F) {
            return false;
        }
        return woodTask ? state.is(BlockTags.LEAVES) : this.isBreakableMiningCover(state);
    }

    private boolean isBreakableMiningCover(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.LEAVES)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.BLACKSTONE)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.COBBLED_DEEPSLATE)
                || state.is(Blocks.NETHERRACK);
    }

    private boolean isShovelPreferredBlock(BlockState state) {
        return state.is(Blocks.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK);
    }

    private boolean breakAuxiliaryBlock(BlockPos pos, String observationPrefix) {
        return this.mineBlockTick(pos, MiningMode.AUXILIARY, observationPrefix);
    }

    private void tickMiningState() {
        if (this.miningMode == MiningMode.NONE) {
            return;
        }
        if (this.miningTarget == null || !(this.level() instanceof ServerLevel serverLevel)) {
            this.resetMiningState();
            return;
        }
        BlockPos pos = this.miningTarget;
        BlockState state = serverLevel.getBlockState(pos);
        if (state.isAir()) {
            this.resetMiningState();
            return;
        }
        if (state.getDestroySpeed(serverLevel, pos) < 0.0F) {
            this.resetMiningState();
            return;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        boolean reachableNow = this.distanceToSqr(center) <= HARVEST_REACH * HARVEST_REACH && this.canHarvestFromHere(pos);
        if (!reachableNow) {
            this.miningOutOfReachTicks++;
            if (this.miningOutOfReachTicks <= 12) {
                this.setForcedLookTarget(center, 6);
                if (this.tickCount % 6 == 0) {
                    BlockPos approach = this.findApproachPosition(pos);
                    if (approach != null) {
                        this.navigateToPosition(approach, 1.08D);
                    }
                }
                return;
            }
            BlockPos approach = this.findApproachPosition(pos);
            if (approach != null) {
                this.navigateToPosition(approach, 1.1D);
                this.lastObservation = "采掘位点重定位@" + this.formatPos(approach);
            }
            this.resetMiningState();
            return;
        }
        this.miningOutOfReachTicks = 0;
        this.getNavigation().stop();
        this.runtimeClearWasdOverride();
        this.setZza(0.0F);
        this.setXxa(0.0F);
        this.setSpeed(0.0F);
        this.setSprinting(false);
        if (this.tickCount % 8 == 0) {
            this.facePosition(center);
        } else {
            this.setForcedLookTarget(center, 8);
        }

        this.miningLastTick = this.tickCount;
        this.miningRequiredTicks = this.computeMiningRequiredTicks(state, pos);
        if (this.miningMode == MiningMode.HARVEST_WOOD || this.miningMode == MiningMode.HARVEST_ORE) {
            this.touchHarvestTaskProgress();
        }
        this.miningProgressTicks++;

        if (this.tickCount % 5 == 0) {
            this.swing(InteractionHand.MAIN_HAND);
        }
        this.updateMiningProgressVisual(serverLevel, pos);

        if (this.miningProgressTicks < this.miningRequiredTicks) {
            this.lastObservation = "采掘中 " + this.miningProgressTicks + "/" + this.miningRequiredTicks + " @" + this.formatPos(pos);
            return;
        }

        this.finishMiningBlock(serverLevel, pos, state);
    }

    private void resetMiningState() {
        this.clearMiningProgressVisual();
        this.miningTarget = null;
        this.miningMode = MiningMode.NONE;
        this.miningProgressTicks = 0;
        this.miningRequiredTicks = 0;
        this.miningLastTick = 0;
        this.miningLastStage = -1;
        this.miningOutOfReachTicks = 0;
        this.miningObservationPrefix = "";
    }

    private boolean isMiningInProgress(BlockPos pos, MiningMode mode) {
        return pos != null && mode != MiningMode.NONE && pos.equals(this.miningTarget) && this.miningMode == mode;
    }

    private boolean isMiningLocked() {
        return this.miningMode != MiningMode.NONE && this.miningTarget != null;
    }

    private void resetHarvestTaskState() {
        this.harvestTaskState = HarvestTaskState.IDLE;
        this.harvestTaskTarget = null;
        this.harvestTaskObstacle = null;
        this.harvestTaskMoveTarget = null;
        this.harvestTaskStateTick = this.tickCount;
        this.harvestTaskLastProgressTick = this.tickCount;
        SearchBlocks.resetVisited();
    }

    private void touchHarvestTaskProgress() {
        this.harvestTaskLastProgressTick = this.tickCount;
    }

    private void setHarvestTaskState(HarvestTaskState nextState) {
        if (nextState == null || this.harvestTaskState == nextState) {
            return;
        }
        this.harvestTaskState = nextState;
        this.harvestTaskStateTick = this.tickCount;
    }

    private HarvestTaskView updateHarvestTaskView(boolean woodTask) {
        // Harvest state machine: SEARCH -> MOVE -> CLEAR -> MINE -> COLLECT.
        if (this.harvestTaskState == HarvestTaskState.IDLE || this.harvestTaskWoodMode != woodTask) {
            this.harvestTaskWoodMode = woodTask;
            this.harvestTaskTarget = null;
            this.harvestTaskObstacle = null;
            this.harvestTaskMoveTarget = null;
            this.setHarvestTaskState(HarvestTaskState.SEARCH_TARGET);
            this.touchHarvestTaskProgress();
        }

        if (this.tickCount - this.harvestTaskLastProgressTick > HARVEST_TASK_STALE_TICKS) {
            this.harvestTaskTarget = null;
            this.harvestTaskObstacle = null;
            this.harvestTaskMoveTarget = null;
            this.setHarvestTaskState(HarvestTaskState.SEARCH_TARGET);
        }

        BlockPos target = this.resolveHarvestTarget(woodTask, woodTask ? this.rememberedLog : this.rememberedOre);
        if (woodTask) {
            this.rememberedLog = target;
        } else {
            this.rememberedOre = target;
        }

        if (target == null || !this.isValidHarvestTarget(target, woodTask)) {
            this.harvestTaskTarget = null;
            this.harvestTaskObstacle = null;
            BlockPos exploreTarget = this.findExplorationDestination();
            this.harvestTaskMoveTarget = this.runtimeResolveMovementTarget(exploreTarget != null ? exploreTarget : this.blockPosition());
            this.setHarvestTaskState(HarvestTaskState.SEARCH_TARGET);
            return new HarvestTaskView(this.harvestTaskState, woodTask, null, null, this.harvestTaskMoveTarget);
        }

        if (!target.equals(this.harvestTaskTarget)) {
            this.harvestTaskTarget = target.immutable();
            this.setHarvestTaskState(HarvestTaskState.MOVE_TO_TARGET);
            this.touchHarvestTaskProgress();
        }

        BlockPos obstacle = this.findHarvestObstacle(this.harvestTaskTarget, woodTask);
        BlockPos focus = obstacle != null ? obstacle : this.harvestTaskTarget;
        BlockPos approach = this.findApproachPosition(focus);
        BlockPos moveTarget = approach != null ? approach : this.runtimeResolveMovementTarget(focus);

        this.harvestTaskObstacle = obstacle == null ? null : obstacle.immutable();
        this.harvestTaskMoveTarget = moveTarget == null ? null : moveTarget.immutable();

        if (this.harvestTaskState == HarvestTaskState.IDLE || this.harvestTaskState == HarvestTaskState.SEARCH_TARGET) {
            this.setHarvestTaskState(HarvestTaskState.MOVE_TO_TARGET);
        }

        switch (this.harvestTaskState) {
            case MOVE_TO_TARGET -> {
                if (obstacle != null && this.canHarvestFromHere(obstacle)) {
                    this.setHarvestTaskState(HarvestTaskState.CLEAR_OBSTACLE);
                } else if (obstacle == null && this.canHarvestFromHere(this.harvestTaskTarget)) {
                    this.setHarvestTaskState(HarvestTaskState.MINE_TARGET);
                }
            }
            case CLEAR_OBSTACLE -> {
                if (obstacle == null) {
                    this.setHarvestTaskState(HarvestTaskState.MINE_TARGET);
                } else if (!this.canHarvestFromHere(obstacle)) {
                    this.setHarvestTaskState(HarvestTaskState.MOVE_TO_TARGET);
                }
            }
            case MINE_TARGET -> {
                if (!this.isValidHarvestTarget(this.harvestTaskTarget, woodTask)) {
                    BlockPos next = this.findConnectedHarvestTarget(this.harvestTaskTarget, woodTask);
                    if (next != null && this.isValidHarvestTarget(next, woodTask)) {
                        this.harvestTaskTarget = next.immutable();
                        if (woodTask) {
                            this.rememberedLog = next;
                        } else {
                            this.rememberedOre = next;
                        }
                        this.setHarvestTaskState(HarvestTaskState.MOVE_TO_TARGET);
                        this.touchHarvestTaskProgress();
                    } else {
                        this.setHarvestTaskState(HarvestTaskState.COLLECT_DROPS);
                    }
                } else if (obstacle != null) {
                    this.setHarvestTaskState(HarvestTaskState.CLEAR_OBSTACLE);
                } else if (!this.canHarvestFromHere(this.harvestTaskTarget)) {
                    this.setHarvestTaskState(HarvestTaskState.MOVE_TO_TARGET);
                }
            }
            case COLLECT_DROPS -> {
                if (this.observedDrop == null || !this.observedDrop.isAlive()) {
                    BlockPos next = this.findConnectedHarvestTarget(this.harvestTaskTarget, woodTask);
                    if (next != null && this.isValidHarvestTarget(next, woodTask)) {
                        this.harvestTaskTarget = next.immutable();
                        if (woodTask) {
                            this.rememberedLog = next;
                        } else {
                            this.rememberedOre = next;
                        }
                        this.setHarvestTaskState(HarvestTaskState.MOVE_TO_TARGET);
                        this.touchHarvestTaskProgress();
                    } else {
                        this.harvestTaskTarget = null;
                        this.setHarvestTaskState(HarvestTaskState.SEARCH_TARGET);
                    }
                }
            }
            case COMPLETE, FAILED, IDLE, SEARCH_TARGET -> {
            }
        }

        return new HarvestTaskView(
                this.harvestTaskState,
                woodTask,
                this.harvestTaskTarget,
                this.harvestTaskObstacle,
                this.harvestTaskMoveTarget);
    }

    private AgentGoal getHarvestTaskLockGoal() {
        if (this.harvestTaskState == HarvestTaskState.IDLE || this.harvestTaskState == HarvestTaskState.SEARCH_TARGET) {
            return null;
        }
        if (this.tickCount - this.harvestTaskLastProgressTick > HARVEST_TASK_LOCK_TICKS) {
            return null;
        }
        if (this.isInLava() || this.isOnFire()) {
            return null;
        }
        if (this.observedHostile != null && this.observedHostile.isAlive() && this.distanceToSqr(this.observedHostile) <= 49.0D) {
            return null;
        }
        GoalType goalType = this.harvestTaskWoodMode ? GoalType.COLLECT_WOOD : GoalType.COLLECT_ORE;
        return AgentGoal.of(goalType, "task-lock", "采集任务状态机锁定，避免被跟随/探索打断");
    }

    private boolean mineBlockTick(BlockPos pos, MiningMode mode, String observationPrefix) {
        if (pos == null || mode == MiningMode.NONE) {
            return false;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!serverLevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
            this.lastObservation = "服务器关闭了生物破坏方块，无法执行采集任务。";
            return false;
        }
        BlockState state = serverLevel.getBlockState(pos);
        if (state.isAir()) {
            if (pos.equals(this.miningTarget)) {
                this.resetMiningState();
            }
            return true;
        }
        if (state.getDestroySpeed(serverLevel, pos) < 0.0F) {
            return false;
        }
        this.ensureKnowledgePreferredTool(state, mode);

        if (this.isMiningLocked() && !this.isMiningInProgress(pos, mode)) {
            return false;
        }

        Vec3 center = Vec3.atCenterOf(pos);
        if (this.distanceToSqr(center) > HARVEST_REACH * HARVEST_REACH || !this.canHarvestFromHere(pos)) {
            return false;
        }

        if (!pos.equals(this.miningTarget) || this.miningMode != mode) {
            this.clearMiningProgressVisual();
            this.miningTarget = pos.immutable();
            this.miningMode = mode;
            this.miningObservationPrefix = observationPrefix == null ? "" : observationPrefix;
            this.miningProgressTicks = 0;
            this.miningRequiredTicks = this.computeMiningRequiredTicks(state, pos);
            this.miningOutOfReachTicks = 0;
            this.miningLastTick = this.tickCount;
            if (mode == MiningMode.HARVEST_WOOD || mode == MiningMode.HARVEST_ORE) {
                this.touchHarvestTaskProgress();
            }
            this.facePosition(center);
            this.setForcedLookTarget(center, 10);
        }
        return false;
    }

    private int computeMiningRequiredTicks(BlockState state, BlockPos pos) {
        float hardness = state.getDestroySpeed(this.level(), pos);
        if (hardness < 0.0F) {
            return Integer.MAX_VALUE;
        }
        if (hardness == 0.0F) {
            return 1;
        }
        float destroySpeed = Math.max(0.0F, this.getVanillaLikeDestroySpeed(state, pos));
        boolean correctTool = this.canHarvestDrops(state);
        float divisor = correctTool ? 30.0F : 100.0F;
        float progressPerTick = destroySpeed / hardness / divisor;
        if (progressPerTick <= 0.0F) {
            return Integer.MAX_VALUE;
        }
        return Mth.clamp(Mth.ceil(1.0F / progressPerTick), 1, 4096);
    }

    private float getVanillaLikeDestroySpeed(BlockState state, BlockPos pos) {
        float speed = this.getMainHandItem().getDestroySpeed(state);
        if (speed > 1.0F) {
            speed += (float)this.getAttributeValue(Attributes.MINING_EFFICIENCY);
        }
        if (MobEffectUtil.hasDigSpeed(this)) {
            speed *= 1.0F + (MobEffectUtil.getDigSpeedAmplification(this) + 1) * 0.2F;
        }
        if (this.hasEffect(MobEffects.MINING_FATIGUE)) {
            int amplifier = this.getEffect(MobEffects.MINING_FATIGUE).getAmplifier();
            float fatigueMultiplier = switch (amplifier) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 0.00081F;
            };
            speed *= fatigueMultiplier;
        }
        speed *= this.getAttributeMultiplier(Attributes.BLOCK_BREAK_SPEED, 1.0F);
        if (this.isEyeInFluid(FluidTags.WATER)) {
            speed *= this.getAttributeMultiplier(Attributes.SUBMERGED_MINING_SPEED, 0.2F);
        }
        if (!this.onGround()) {
            speed /= 5.0F;
        }
        return Math.max(0.0F, speed);
    }

    private float getAttributeMultiplier(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, float fallback) {
        var instance = this.getAttribute(attribute);
        return instance == null ? fallback : (float)instance.getValue();
    }

    private boolean canHarvestDrops(BlockState state) {
        return !state.requiresCorrectToolForDrops() || this.getMainHandItem().isCorrectToolForDrops(state);
    }

    private void updateMiningProgressVisual(ServerLevel serverLevel, BlockPos pos) {
        if (this.miningRequiredTicks <= 0) {
            return;
        }
        int stage = Mth.clamp((int)((long)this.miningProgressTicks * 10L / this.miningRequiredTicks), 0, 9);
        if (stage == this.miningLastStage) {
            return;
        }
        serverLevel.destroyBlockProgress(this.getId(), pos, stage);
        this.miningLastStage = stage;
    }

    private void clearMiningProgressVisual() {
        if (this.miningLastStage < 0 || this.miningTarget == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.destroyBlockProgress(this.getId(), this.miningTarget, -1);
        this.miningLastStage = -1;
    }

    private void finishMiningBlock(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        ItemStack heldTool = this.getMainHandItem();
        ItemStack toolSnapshot = heldTool.copy();
        boolean canHarvest = this.canHarvestDrops(state);
        List<ItemStack> drops = canHarvest ? Block.getDrops(state, serverLevel, pos, blockEntity, this, toolSnapshot) : List.of();

        this.clearMiningProgressVisual();
        serverLevel.levelEvent(2001, pos, Block.getId(state));
        BlockState replacement = serverLevel.getFluidState(pos).createLegacyBlock();
        if (!serverLevel.setBlock(pos, replacement, 3)) {
            this.resetMiningState();
            return;
        }

        state.getBlock().destroy(serverLevel, pos, state);
        WorldScanner.invalidateAt(serverLevel, pos);

        if (!heldTool.isEmpty()) {
            heldTool.getItem().mineBlock(heldTool, serverLevel, state, pos, this);
        }

        for (ItemStack stack : drops) {
            ItemStack remainder = this.storeInBackpack(stack.copy());
            if (!remainder.isEmpty()) {
                this.spawnAtLocation(serverLevel, remainder);
            }
        }

        if (this.miningMode == MiningMode.HARVEST_WOOD || this.miningMode == MiningMode.HARVEST_ORE) {
            TeamKnowledge.forgetResourceNear(this, this.miningMode == MiningMode.HARVEST_WOOD ? ResourceType.WOOD : ResourceType.ORE, pos, 9.0D);
            this.lastObservation = this.miningObservationPrefix + this.formatPos(pos);
            this.remember("资源", this.lastObservation);
            this.touchHarvestTaskProgress();
            this.reportTaskProgress(this.activeTaskName, this.miningMode == MiningMode.HARVEST_WOOD
                    ? "已采集木头：" + this.formatPos(pos)
                    : "已采集矿石：" + this.formatPos(pos));
        } else {
            this.lastObservation = this.miningObservationPrefix + this.formatPos(pos);
            this.remember("路径", this.lastObservation);
        }

        this.resetMiningState();
    }

    private boolean tryCraftBread() {
        if (this.tryCraftByKnowledge("minecraft:bread", "面包", 4)) {
            return true;
        }
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
        return this.tryCraftStoneTool(toolItem, toolName, 3);
    }

    private boolean tryCraftStoneTool(Item toolItem, String toolName, int stoneCost) {
        Identifier toolId = BuiltInRegistries.ITEM.getKey(toolItem);
        if (toolId != null && this.tryCraftByKnowledge(toolId.toString(), toolName, 1)) {
            return true;
        }
        if (this.countBackpackItem(toolItem) > 0) {
            return false;
        }
        int requiredStone = Math.max(1, stoneCost);
        if (this.countBackpackItem(Items.COBBLESTONE) < requiredStone) {
            return false;
        }
        if (!this.ensurePlanksCount(2)) {
            return false;
        }
        if (this.countBackpackItem(Items.STICK) < 2) {
            this.consumeBackpackItem(Items.OAK_PLANKS, 2);
            this.storeInBackpack(new ItemStack(Items.STICK, 4));
            this.remember("合成", "制作木棍");
        }
        if (this.countBackpackItem(Items.STICK) < 2) {
            return false;
        }
        this.consumeBackpackItem(Items.COBBLESTONE, requiredStone);
        this.consumeBackpackItem(Items.STICK, 2);
        this.storeInBackpack(new ItemStack(toolItem));
        this.remember("合成", "制作" + toolName);
        return true;
    }

    private boolean tryCraftByKnowledge(String outputId, String outputName, int minimumDesiredCount) {
        if (outputId == null || outputId.isBlank()) {
            return false;
        }
        if (this.countBackpackItemById(outputId) >= minimumDesiredCount) {
            return false;
        }
        return this.craftOneRecipeByKnowledge(outputId, outputName, 3);
    }

    private boolean craftOneRecipeByKnowledge(String outputId, String outputName, int depth) {
        if (depth <= 0 || outputId == null || outputId.isBlank()) {
            return false;
        }
        KnowledgeManager.RecipeKnowledge recipe = KnowledgeManager.getRecipe(outputId);
        if (recipe == null || recipe.inputs() == null || recipe.inputs().isEmpty()) {
            return false;
        }

        for (Map.Entry<String, Integer> ingredient : recipe.inputs().entrySet()) {
            String ingredientId = ingredient.getKey();
            int needed = Math.max(1, ingredient.getValue() == null ? 1 : ingredient.getValue());
            if (!this.ensureKnowledgeIngredient(ingredientId, needed, depth - 1)) {
                return false;
            }
        }

        for (Map.Entry<String, Integer> ingredient : recipe.inputs().entrySet()) {
            String ingredientId = ingredient.getKey();
            int needed = Math.max(1, ingredient.getValue() == null ? 1 : ingredient.getValue());
            if (!this.consumeBackpackItemById(ingredientId, needed)) {
                return false;
            }
        }

        Item outputItem = this.resolveItemById(outputId);
        if (outputItem == Items.AIR) {
            return false;
        }
        int outputCount = KnowledgeManager.getRecipeOutputCount(outputId);
        this.storeInBackpack(new ItemStack(outputItem, outputCount));
        String resolvedName = outputName == null || outputName.isBlank() ? this.getItemDisplayNameById(outputId) : outputName;
        this.lastObservation = "已根据知识库合成" + resolvedName + "。";
        this.remember("合成", "知识库制作" + resolvedName + "x" + outputCount);
        return true;
    }

    private boolean ensureKnowledgeIngredient(String ingredientId, int needed, int depth) {
        if (ingredientId == null || ingredientId.isBlank()) {
            return false;
        }
        if (this.countBackpackItemById(ingredientId) >= needed) {
            return true;
        }
        if (this.isPlankItemId(ingredientId)) {
            return this.ensurePlanksCount(needed);
        }
        if (depth <= 0) {
            return false;
        }

        KnowledgeManager.RecipeKnowledge ingredientRecipe = KnowledgeManager.getRecipe(ingredientId);
        if (ingredientRecipe == null || ingredientRecipe.inputs() == null || ingredientRecipe.inputs().isEmpty()) {
            return false;
        }

        int current = this.countBackpackItemById(ingredientId);
        int missing = Math.max(0, needed - current);
        int outputCount = KnowledgeManager.getRecipeOutputCount(ingredientId);
        int craftTimes = (int)Math.ceil(missing / (double)Math.max(1, outputCount));
        for (int i = 0; i < craftTimes; i++) {
            if (!this.craftOneRecipeByKnowledge(ingredientId, this.getItemDisplayNameById(ingredientId), depth - 1)) {
                return false;
            }
        }
        return this.countBackpackItemById(ingredientId) >= needed;
    }

    private boolean isPlankItemId(String itemId) {
        return itemId != null && itemId.endsWith("_planks");
    }

    private Item resolveItemById(String itemId) {
        Identifier identifier = Identifier.tryParse(itemId);
        if (identifier == null) {
            return Items.AIR;
        }
        return BuiltInRegistries.ITEM.get(identifier).map(reference -> reference.value()).orElse(Items.AIR);
    }

    private String getItemDisplayNameById(String itemId) {
        Item item = this.resolveItemById(itemId);
        if (item != Items.AIR) {
            return this.getItemDisplayName(item);
        }
        int split = itemId.indexOf(':');
        return split >= 0 ? itemId.substring(split + 1) : itemId;
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

        BlockPos tillableSoil = this.findTillableSoil();
        if (tillableSoil != null && this.hasAnyCropSeed()) {
            if (this.distanceToSqr(Vec3.atCenterOf(tillableSoil)) > 9.0D) {
                BlockPos approach = this.findApproachPosition(tillableSoil);
                return approach != null && this.navigateToPosition(approach, 1.0D);
            }
            return this.tillSoil(tillableSoil);
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

    private BlockPos findTillableSoil() {
        BlockPos center = this.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -8; x <= 8; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -8; z <= 8; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!this.isTillableSoilState(this.level().getBlockState(pos))) {
                        continue;
                    }
                    if (!this.level().getBlockState(pos.above()).isAir()) {
                        continue;
                    }
                    double distance = this.distanceToSqr(Vec3.atCenterOf(pos));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    private boolean isTillableSoilState(BlockState state) {
        return state.is(Blocks.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT);
    }

    private boolean tillSoil(BlockPos pos) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!serverLevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return false;
        }
        BlockState state = serverLevel.getBlockState(pos);
        if (!this.isTillableSoilState(state) || !serverLevel.getBlockState(pos.above()).isAir()) {
            return false;
        }
        ItemStack hoe = this.findBestToolInBackpack(this::isHoeItem, Items.STONE_HOE);
        if (hoe.isEmpty() && !this.tryCraftStoneTool(Items.STONE_HOE, "石锄", 2)) {
            return false;
        }
        if (hoe.isEmpty()) {
            hoe = this.findBestToolInBackpack(this::isHoeItem, Items.STONE_HOE);
        }
        if (!hoe.isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, hoe.copy());
        }
        this.facePosition(Vec3.atCenterOf(pos));
        this.swing(InteractionHand.MAIN_HAND);
        serverLevel.setBlock(pos, Blocks.FARMLAND.defaultBlockState(), 3);
        WorldScanner.invalidateAt(serverLevel, pos);
        this.lastObservation = "已耕地@" + this.formatPos(pos);
        this.remember("农场", this.lastObservation);
        this.reportTaskProgress(this.activeTaskName, "已使用锄头耕地：" + this.formatPos(pos));
        return true;
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
        WorldScanner.invalidateAt(serverLevel, pos);
        TeamKnowledge.forgetResourceNear(this, ResourceType.CROP, pos, 9.0D);
        for (ItemStack stack : drops) {
            ItemStack remainder = this.storeInBackpack(stack.copy());
            if (!remainder.isEmpty()) {
                this.spawnAtLocation(serverLevel, remainder);
            }
        }

        Item seed = this.getSeedForCrop(state);
        if (seed != Items.AIR && this.consumeBackpackItem(seed, 1)) {
            serverLevel.setBlock(pos, crop.getStateForAge(0), 3);
            WorldScanner.invalidateAt(serverLevel, pos);
        }
        this.lastObservation = "已收割作物@" + this.formatPos(pos);
        this.remember("农场", this.lastObservation);
        this.reportTaskProgress(this.activeTaskName, "已收割作物：" + this.formatPos(pos));
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
            WorldScanner.invalidateAt(serverLevel, pos);
        } else if (this.consumeBackpackItem(Items.CARROT, 1)) {
            serverLevel.setBlock(pos, Blocks.CARROTS.defaultBlockState(), 3);
            WorldScanner.invalidateAt(serverLevel, pos);
        } else if (this.consumeBackpackItem(Items.POTATO, 1)) {
            serverLevel.setBlock(pos, Blocks.POTATOES.defaultBlockState(), 3);
            WorldScanner.invalidateAt(serverLevel, pos);
        } else if (this.consumeBackpackItem(Items.BEETROOT_SEEDS, 1)) {
            serverLevel.setBlock(pos, Blocks.BEETROOTS.defaultBlockState(), 3);
            WorldScanner.invalidateAt(serverLevel, pos);
        } else {
            return false;
        }

        this.lastObservation = "已补种农作物@" + this.formatPos(pos);
        this.remember("农场", this.lastObservation);
        this.reportTaskProgress(this.activeTaskName, "已补种农作物：" + this.formatPos(pos));
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
        ItemEntity previousDrop = this.observedDrop;

        this.observedHostile = this.level().getEntitiesOfClass(LivingEntity.class, scanBox, entity -> entity != this
                        && entity.isAlive()
                        && entity instanceof Enemy
                        && !(entity instanceof AIPlayerEntity))
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        this.observedDrop = this.level().getEntitiesOfClass(ItemEntity.class, scanBox, item -> item.isAlive() && !item.getItem().isEmpty())
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);

        WorldScanner.PerceptionResult perception = WorldScanner.scanFor(this);
        this.rememberedLog = perception.wood() != null ? perception.wood().pos() : TeamKnowledge.findNearestResource(this, ResourceType.WOOD, this.blockPosition());
        this.rememberedOre = perception.ore() != null ? perception.ore().pos() : TeamKnowledge.findNearestResource(this, ResourceType.ORE, this.blockPosition());
        this.rememberedCrop = perception.crop() != null ? perception.crop().pos() : TeamKnowledge.findNearestResource(this, ResourceType.CROP, this.blockPosition());
        this.rememberedBed = perception.bed() != null ? perception.bed().pos() : TeamKnowledge.findNearestResource(this, ResourceType.BED, this.blockPosition());
        this.rememberedChest = perception.chest() != null ? perception.chest().pos() : TeamKnowledge.findNearestResource(this, ResourceType.CHEST, this.blockPosition());
        this.rememberedCraftingTable = perception.crafting() != null ? perception.crafting().pos() : TeamKnowledge.findNearestResource(this, ResourceType.CRAFTING, this.blockPosition());
        this.rememberedFurnace = perception.furnace() != null ? perception.furnace().pos() : TeamKnowledge.findNearestResource(this, ResourceType.FURNACE, this.blockPosition());

        int nearbyPlayers = this.level().getEntitiesOfClass(Player.class, scanBox, player -> player.isAlive() && !player.isSpectator()).size();
        List<String> notices = new ArrayList<>();
        notices.add("附近玩家=" + nearbyPlayers);
        notices.add((this.level().getDayTime() % 24000L) >= 12500L ? "夜晚" : "白天");
        if (this.observedHostile != null) {
            notices.add("敌对生物=" + this.observedHostile.getName().getString());
            String weaknessHint = KnowledgeManager.getMobWeaknessHint(this.observedHostile);
            if (!weaknessHint.isBlank()) {
                notices.add("弱点=" + weaknessHint);
            }
            if (previousHostile == null || !previousHostile.equals(this.observedHostile)) {
                this.remember("感知", "发现敌对目标 " + this.observedHostile.getName().getString());
                if (!weaknessHint.isBlank()) {
                    this.remember("战斗知识", this.observedHostile.getName().getString() + " -> " + weaknessHint);
                }
            }
        }
        if (this.isOwnerUnderThreat()) {
            notices.add("主人附近有威胁");
        }
        if (this.observedDrop != null) {
            notices.add("掉落物=" + this.observedDrop.getItem().getHoverName().getString());
            if (previousDrop == null || !previousDrop.getUUID().equals(this.observedDrop.getUUID())) {
                this.remember("感知", "发现掉落物 " + this.observedDrop.getItem().getHoverName().getString() + "@" + this.formatPos(this.observedDrop.blockPosition()));
            }
        }
        notices.addAll(perception.notices());
        this.lastObservation = notices.isEmpty() ? DEFAULT_OBSERVATION : String.join(" | ", notices);
        this.latestCognitiveSummary = this.buildCognitiveSummary() + " | 团队知识=" + TeamKnowledge.getSummary(this);
        this.updateKnowledgeNotes();
    }

    private void recordUtilityDiscovery(String label, BlockPos previous, BlockPos current) {
        if (current == null || current.equals(previous)) {
            return;
        }
        this.remember("感知", "发现" + label + "@" + this.formatPos(current));
    }

    private void updateKnowledgeNotes() {
        AILongTermMemoryStore.updateNote(this, "bed", this.rememberedBed == null ? "" : this.formatPos(this.rememberedBed));
        AILongTermMemoryStore.updateNote(this, "chest", this.rememberedChest == null ? "" : this.formatPos(this.rememberedChest));
        AILongTermMemoryStore.updateNote(this, "crafting", this.rememberedCraftingTable == null ? "" : this.formatPos(this.rememberedCraftingTable));
        AILongTermMemoryStore.updateNote(this, "furnace", this.rememberedFurnace == null ? "" : this.formatPos(this.rememberedFurnace));
        AILongTermMemoryStore.updateNote(this, "crop", this.rememberedCrop == null ? "" : this.formatPos(this.rememberedCrop));
        AILongTermMemoryStore.updateNote(this, "cognition", this.getCognitiveSummary());
    }

    private boolean isRememberedUtilityBlockValid(BlockPos pos, Predicate<BlockState> matcher) {
        return pos != null && matcher.test(this.level().getBlockState(pos));
    }

    private BlockPos findNearestUtilityBlock(Predicate<BlockState> matcher, int horizontalRadius, int verticalDown, int verticalUp) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos origin = this.blockPosition();

        for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
            for (int y = -verticalDown; y <= verticalUp; y++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (!matcher.test(this.level().getBlockState(cursor))) {
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

    private boolean isBedBlock(BlockState state) {
        return state.getBlock() instanceof BedBlock;
    }

    private boolean isStorageBlock(BlockState state) {
        return state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL);
    }

    private boolean isCraftingBlock(BlockState state) {
        return state.is(Blocks.CRAFTING_TABLE);
    }

    private boolean isFurnaceBlock(BlockState state) {
        return state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER);
    }

    private boolean isCropBlock(BlockState state) {
        return state.getBlock() instanceof CropBlock;
    }

    private boolean isValidHarvestTarget(BlockPos pos, boolean woodTask) {
        if (pos == null) {
            return false;
        }

        BlockState state = this.level().getBlockState(pos);
        if (woodTask) {
            return KnowledgeManager.isTreeLog(state) || state.is(BlockTags.LOGS);
        }
        return this.isInterestingOre(state, pos) && (this.isExposed(pos) || this.findAdjacentHarvestCover(pos, false) != null);
    }

    private BlockPos normalizeHarvestTarget(BlockPos pos, boolean woodTask) {
        if (!woodTask || pos == null) {
            return pos;
        }
        BlockPos seed = pos.immutable();
        BlockPos base = seed;
        int descendGuard = 0;
        while (descendGuard < 20 && (KnowledgeManager.isTreeLog(this.level().getBlockState(base.below()))
                || this.level().getBlockState(base.below()).is(BlockTags.LOGS))) {
            base = base.below();
            descendGuard++;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int x = -2; x <= 2; x++) {
            for (int y = -3; y <= 12; y++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos candidate = base.offset(x, y, z);
                    BlockState candidateState = this.level().getBlockState(candidate);
                    if (!KnowledgeManager.isTreeLog(candidateState) && !candidateState.is(BlockTags.LOGS)) {
                        continue;
                    }
                    double score = this.distanceToSqr(Vec3.atCenterOf(candidate));
                    if (candidate.getY() > base.getY()) {
                        score -= 0.9D * (candidate.getY() - base.getY());
                    }
                    if (candidate.getY() > seed.getY()) {
                        score -= 0.6D * (candidate.getY() - seed.getY());
                    }
                    if (this.canHarvestFromHere(candidate)) {
                        score -= 1.4D;
                    }
                    if (best == null || score < bestScore) {
                        bestScore = score;
                        best = candidate.immutable();
                    }
                }
            }
        }
        if (best != null) {
            return best;
        }
        return (KnowledgeManager.isTreeLog(this.level().getBlockState(seed)) || this.level().getBlockState(seed).is(BlockTags.LOGS)) ? seed : null;
    }

    private BlockPos resolveHarvestTarget(boolean woodTask, BlockPos seed) {
        BlockPos preferred = this.normalizeHarvestTarget(seed, woodTask);
        if (this.isHarvestTargetReachable(preferred, woodTask)) {
            return preferred;
        }
        SearchBlocks.resetVisited();
        BlockPos scanned = this.normalizeHarvestTarget(this.findNearestHarvestBlock(woodTask), woodTask);
        if (this.isHarvestTargetReachable(scanned, woodTask)) {
            return scanned;
        }
        return scanned != null ? scanned : preferred;
    }

    private boolean isHarvestTargetReachable(BlockPos target, boolean woodTask) {
        if (!this.isValidHarvestTarget(target, woodTask)) {
            return false;
        }
        if (this.canHarvestFromHere(target)) {
            return true;
        }
        BlockPos obstacle = this.findHarvestObstacle(target, woodTask);
        BlockPos focus = obstacle != null ? obstacle : target;
        if (this.findApproachPosition(focus) != null) {
            return true;
        }
        return this.findWalkablePositionNear(focus, 2, 3, true) != null;
    }

    private BlockPos findNearestHarvestBlock(boolean woodTask) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        BlockPos origin = this.blockPosition();
        return SearchBlocks.searchNearest(
                serverLevel,
                origin,
                SCAN_RADIUS,
                SCAN_VERTICAL_DOWN,
                SCAN_VERTICAL_UP,
                (state, pos) -> woodTask
                        ? (KnowledgeManager.isTreeLog(state) || state.is(BlockTags.LOGS))
                        : this.isInterestingOre(state, pos) && (this.isExposed(pos) || this.findAdjacentHarvestCover(pos, false) != null));
    }

    private boolean canHarvestFromHere(BlockPos target) {
        return this.canHarvestFromPosition(this.getEyePosition(), target);
    }

    private boolean canHarvestFromPosition(Vec3 eyes, BlockPos target) {
        if (target == null || eyes == null) {
            return false;
        }
        Vec3 center = Vec3.atCenterOf(target);
        double dx = center.x - eyes.x;
        double dy = center.y - eyes.y;
        double dz = center.z - eyes.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (distance > HARVEST_REACH || horizontal > 4.5D || Math.abs(dy) > 4.6D) {
            return false;
        }
        BlockHitResult hitResult = this.level().clip(new ClipContext(eyes, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hitResult.getType() != HitResult.Type.BLOCK
                || hitResult.getBlockPos().equals(target)
                || hitResult.getBlockPos().closerThan(target, 1.0D);
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
        if (target == null) {
            return null;
        }
        BlockPos standPos = this.findStandPositionForBlock(target);
        if (standPos != null) {
            return standPos;
        }
        List<BlockPos> candidates = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int step = 1; step <= 4; step++) {
                BlockPos lateral = target.relative(direction, step);
                candidates.add(lateral);
                candidates.add(lateral.above());
                candidates.add(lateral.above().above());
                candidates.add(lateral.above().above().above());
                candidates.add(lateral.below());
                candidates.add(lateral.below().below());
                candidates.add(lateral.below().below().below());
            }
        }
        candidates.add(target.below());
        candidates.add(target.below().below());
        candidates.add(target.below().below().below());
        candidates.add(target.above());
        candidates.add(target.above().above());
        candidates.add(target.above().above().above());
        candidates.add(target.north().east());
        candidates.add(target.north().west());
        candidates.add(target.south().east());
        candidates.add(target.south().west());
        candidates.add(target.north(2).east(2));
        candidates.add(target.north(2).west(2));
        candidates.add(target.south(2).east(2));
        candidates.add(target.south(2).west(2));

        return candidates.stream()
                .distinct()
                .filter(pos -> this.canStandAt(pos, true))
                .min(Comparator.comparingDouble(pos -> this.scoreApproachCandidate(pos, target)))
                .orElse(null);
    }

    private BlockPos findStandPositionForBlock(BlockPos target) {
        List<BlockPos> candidates = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int step = 1; step <= 4; step++) {
                BlockPos lateral = target.relative(direction, step);
                candidates.add(lateral);
                candidates.add(lateral.above());
                candidates.add(lateral.above().above());
                candidates.add(lateral.above().above().above());
                candidates.add(lateral.below());
                candidates.add(lateral.below().below());
                candidates.add(lateral.below().below().below());
            }
        }
        candidates.add(target.north().east());
        candidates.add(target.north().west());
        candidates.add(target.south().east());
        candidates.add(target.south().west());
        candidates.add(target.north(2).east());
        candidates.add(target.north(2).west());
        candidates.add(target.south(2).east());
        candidates.add(target.south(2).west());
        candidates.add(target.north().east(2));
        candidates.add(target.north().west(2));
        candidates.add(target.south().east(2));
        candidates.add(target.south().west(2));

        return candidates.stream()
                .distinct()
                .filter(candidate -> this.canStandAt(candidate, true))
                .filter(candidate -> this.canHarvestFromPosition(Vec3.atCenterOf(candidate).add(0.0D, 1.5D, 0.0D), target))
                .min(Comparator.comparingDouble(candidate -> this.scoreStandPosition(candidate, target)))
                .orElse(null);
    }

    private double scoreStandPosition(BlockPos candidate, BlockPos target) {
        double selfDistance = this.distanceToSqr(Vec3.atCenterOf(candidate));
        double targetDistance = candidate.distSqr(target);
        double verticalDelta = Math.abs(candidate.getY() - target.getY());
        return selfDistance + targetDistance * 1.8D + verticalDelta * 0.75D;
    }

    private boolean canStandAt(BlockPos pos) {
        return this.canStandAt(pos, false);
    }

    private boolean isPassable(BlockState state, BlockPos pos) {
        if (state == null || pos == null) {
            return false;
        }
        return state.getCollisionShape(this.level(), pos).isEmpty();
    }

    private boolean canStandAt(BlockPos pos, boolean allowWater) {
        if (pos == null) {
            return false;
        }
        BlockState feet = this.level().getBlockState(pos);
        BlockState head = this.level().getBlockState(pos.above());
        BlockState floor = this.level().getBlockState(pos.below());
        boolean feetInWater = this.level().getFluidState(pos).is(FluidTags.WATER);
        boolean headInWater = this.level().getFluidState(pos.above()).is(FluidTags.WATER);
        boolean feetClear = this.isPassable(feet, pos)
                && !this.level().getFluidState(pos).is(FluidTags.LAVA)
                && (allowWater || !feetInWater);
        boolean headClear = this.isPassable(head, pos.above())
                && !this.level().getFluidState(pos.above()).is(FluidTags.LAVA)
                && (allowWater || !headInWater);
        boolean floorStable = !this.isPassable(floor, pos.below())
                && !floor.is(BlockTags.LEAVES)
                && !this.level().getFluidState(pos.below()).is(FluidTags.LAVA);
        if (allowWater && (feetInWater || headInWater)) {
            return feetClear && headClear;
        }
        return feetClear && headClear && floorStable;
    }

    private BlockPos findWalkablePositionNear(BlockPos center, int horizontalRadius, int verticalRadius) {
        return this.findWalkablePositionNear(center, horizontalRadius, verticalRadius, false);
    }

    private BlockPos findWalkablePositionNear(BlockPos center, int horizontalRadius, int verticalRadius, boolean allowWater) {
        if (center == null) {
            return null;
        }
        BlockPos bestPos = null;
        double bestScore = Double.MAX_VALUE;

        for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
            for (int y = -verticalRadius; y <= verticalRadius; y++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    BlockPos candidate = center.offset(x, y, z);
                    if (!this.canStandAt(candidate, allowWater)) {
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
        if (!this.isValidHarvestTarget(pos, woodTask)) {
            return false;
        }
        return this.mineBlockTick(pos, woodTask ? MiningMode.HARVEST_WOOD : MiningMode.HARVEST_ORE, woodTask ? "已采集木头@" : "已采集矿石@");
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

        this.facePosition(Vec3.atCenterOf(pos));
        this.swing(InteractionHand.MAIN_HAND);
        serverLevel.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        WorldScanner.invalidateAt(serverLevel, pos);
        TeamKnowledge.reportStructure(this, this.activeBlueprintId, pos);
        this.consumeBackpackItem(Items.OAK_PLANKS, 1);
        this.lastObservation = "已放置木板@" + this.formatPos(pos);
        this.remember("建造", this.lastObservation);
        this.reportTaskProgress(this.activeTaskName, "已放置建筑方块：" + this.formatPos(pos));
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
        BlockState state = blockItem.getBlock().defaultBlockState();
        return KnowledgeManager.isTreeLog(state) || state.is(BlockTags.LOGS);
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

    private int countBackpackItemById(String itemId) {
        Item item = this.resolveItemById(itemId);
        if (item == Items.AIR) {
            return 0;
        }
        return this.countBackpackItem(item);
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

    private boolean consumeBackpackItemById(String itemId, int count) {
        Item item = this.resolveItemById(itemId);
        if (item == Items.AIR) {
            return false;
        }
        return this.consumeBackpackItem(item, count);
    }

    private boolean isInterestingOre(BlockState state) {
        if (KnowledgeManager.isKnownOre(state)) {
            return true;
        }
        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null && (key.getPath().endsWith("_ore") || key.getPath().contains("ancient_debris"));
    }

    private boolean isInterestingOre(BlockState state, BlockPos pos) {
        if (pos == null) {
            return this.isInterestingOre(state);
        }
        if (KnowledgeManager.isKnownOre(state, pos.getY())) {
            return true;
        }
        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null && (key.getPath().endsWith("_ore") || key.getPath().contains("ancient_debris"));
    }

    private boolean isSurfacePathBreakable(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DANDELION)
                || state.is(Blocks.POPPY)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MUD)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK);
    }

    private Item selectPathSupportItem() {
        if (this.countBackpackItem(Items.OAK_PLANKS) > 0) {
            return Items.OAK_PLANKS;
        }
        if (this.countBackpackItem(Items.COBBLESTONE) > 0) {
            return Items.COBBLESTONE;
        }
        if (this.countBackpackItem(Items.DIRT) > 0) {
            return Items.DIRT;
        }
        return null;
    }

    private boolean isOreResourceItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) {
            return false;
        }
        String path = key.getPath();
        return path.endsWith("_ore")
                || path.startsWith("raw_")
                || path.endsWith("_ingot")
                || path.contains("ancient_debris")
                || path.endsWith("_gem");
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
        this.setForcedLookTarget(target, 6);
    }

    private CompoundTag createRespawnSnapshot() {
        this.syncPersistentState();
        return this.getPersistentData().copy();
    }

    private void applyPersistentSnapshot(CompoundTag snapshot) {
        this.getPersistentData().merge(snapshot.copy());
        this.persistentStateLoaded = false;
        this.ensurePersistentStateLoaded();
        this.syncClientState();
    }

    private void respawnCompanion(CompoundTag snapshot, ServerPlayer owner, BlockPos respawnPos) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        AIPlayerEntity respawned = ModEntities.AI_PLAYER.get().create(serverLevel, EntitySpawnReason.EVENT);
        if (respawned == null) {
            return;
        }

        respawned.setPos(respawnPos.getX() + 0.5D, respawnPos.getY(), respawnPos.getZ() + 0.5D);
        float spawnYaw = owner != null ? owner.getYRot() : this.getYRot();
        respawned.setYRot(spawnYaw);
        respawned.setYHeadRot(spawnYaw);
        respawned.setYBodyRot(spawnYaw);
        respawned.applyPersistentSnapshot(snapshot);
        respawned.setHealth(respawned.getMaxHealth());
        respawned.setTarget(null);
        respawned.autonomousIdleTicks = 0;
        respawned.lastObservation = "已完成重生，正在恢复行动。";
        serverLevel.addFreshEntity(respawned);
        respawned.remember("重生", "已在" + respawned.formatPos(respawnPos) + "重生并恢复记忆与背包。");
        AILongTermMemoryStore.record(respawned, "重生", "在" + respawned.formatPos(respawnPos) + "恢复行动");
        respawned.sendOwnerAlert("我已在你附近重生，记忆与背包已恢复。", true);
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
        String storedCognition = tag.getString("Cognition").orElse("");
        if (!storedCognition.isBlank()) {
            this.latestCognitiveSummary = storedCognition;
        }

        this.rememberedLog = this.readBlockPos(tag, "RememberedLog");
        this.rememberedOre = this.readBlockPos(tag, "RememberedOre");
        this.rememberedBed = this.readBlockPos(tag, "RememberedBed");
        this.rememberedChest = this.readBlockPos(tag, "RememberedChest");
        this.rememberedCraftingTable = this.readBlockPos(tag, "RememberedCraftingTable");
        this.rememberedFurnace = this.readBlockPos(tag, "RememberedFurnace");
        this.rememberedCrop = this.readBlockPos(tag, "RememberedCrop");
        this.shelterAnchor = this.readBlockPos(tag, "ShelterAnchor");
        this.lastExploreRecord = this.readBlockPos(tag, "LastExploreRecord");
        this.huntTargetId = tag.getString("HuntTargetId").orElse("");
        this.huntTargetLabel = tag.getString("HuntTargetLabel").orElse("");
        this.huntLastKnownPos = this.readBlockPos(tag, "HuntLastKnownPos");

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
        tag.putString("Cognition", this.getCognitiveSummary());
        tag.putString("MemoryData", this.serializeMemory());
        tag.putString("BackpackData", this.serializeBackpack());
        tag.putString("BlueprintId", this.activeBlueprintId);
        this.writeBlockPos(tag, "RememberedLog", this.rememberedLog);
        this.writeBlockPos(tag, "RememberedOre", this.rememberedOre);
        this.writeBlockPos(tag, "RememberedBed", this.rememberedBed);
        this.writeBlockPos(tag, "RememberedChest", this.rememberedChest);
        this.writeBlockPos(tag, "RememberedCraftingTable", this.rememberedCraftingTable);
        this.writeBlockPos(tag, "RememberedFurnace", this.rememberedFurnace);
        this.writeBlockPos(tag, "RememberedCrop", this.rememberedCrop);
        this.writeBlockPos(tag, "ShelterAnchor", this.shelterAnchor);
        this.writeBlockPos(tag, "LastExploreRecord", this.lastExploreRecord);
        tag.putString("HuntTargetId", this.huntTargetId == null ? "" : this.huntTargetId);
        tag.putString("HuntTargetLabel", this.huntTargetLabel == null ? "" : this.huntTargetLabel);
        this.writeBlockPos(tag, "HuntLastKnownPos", this.huntLastKnownPos);
        this.persistentStateDirty = false;
    }

    private void markPersistentDirty() {
        if (!this.level().isClientSide()) {
            this.persistentStateDirty = true;
            if (this.tickCount - this.lastTelemetrySyncTick >= TELEMETRY_SYNC_INTERVAL) {
                this.syncClientTelemetry();
            }
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
        this.storeInBackpack(new ItemStack(Items.STONE_SHOVEL));
        this.storeInBackpack(new ItemStack(Items.STONE_HOE));
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

    private boolean canStoreInBackpack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (ItemStack existing : this.backpack) {
            if (existing.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                return true;
            }
        }
        return false;
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

    public String getDetailedInventorySummary() {
        List<String> entries = new ArrayList<>();
        ItemStack mainHand = this.getMainHandItem();
        ItemStack offHand = this.getOffhandItem();
        if (!mainHand.isEmpty()) {
            entries.add("主手=" + mainHand.getHoverName().getString() + "x" + mainHand.getCount());
        }
        if (!offHand.isEmpty()) {
            entries.add("副手=" + offHand.getHoverName().getString() + "x" + offHand.getCount());
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : this.backpack) {
            if (stack.isEmpty()) {
                continue;
            }
            counts.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            entries.add(entry.getKey() + "x" + entry.getValue());
        }

        return entries.isEmpty() ? "空" : String.join("、", entries);
    }

    public String getClientStatusLine() {
        String synced = this.entityData.get(DATA_STATUS);
        return synced == null || synced.isBlank() ? this.buildClientStatusLine() : synced;
    }

    public String getClientObservationSummary() {
        String synced = this.entityData.get(DATA_OBSERVATION);
        return synced == null || synced.isBlank() ? this.getObservationSummary() : synced;
    }

    public String getClientInventorySummary() {
        String synced = this.entityData.get(DATA_INVENTORY);
        return synced == null || synced.isBlank() ? this.getInventoryPreview() : synced;
    }

    public String getClientGoalSummary() {
        String synced = this.entityData.get(DATA_GOAL);
        return synced == null || synced.isBlank() ? this.latestAgentGoal : synced;
    }

    public String getClientActionSummary() {
        String synced = this.entityData.get(DATA_ACTION);
        return synced == null || synced.isBlank() ? this.agentRuntime.currentActionLabel() : synced;
    }

    public String getClientPathSummary() {
        String synced = this.entityData.get(DATA_PATH);
        return synced == null || synced.isBlank() ? this.agentRuntime.pathStatus() : synced;
    }

    public String getClientMemorySummary() {
        String synced = this.entityData.get(DATA_MEMORY);
        return synced == null || synced.isBlank() ? this.getMemorySummary() : synced;
    }

    public String getClientLlmSummary() {
        String synced = this.entityData.get(DATA_LLM);
        return synced == null || synced.isBlank() ? AIServiceManager.getLastStatusText() : synced;
    }

    public String getClientPlanSummary() {
        String synced = this.entityData.get(DATA_PLAN);
        return synced == null || synced.isBlank() ? this.getPlanSummary() : synced;
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
        this.syncClientTelemetry();
        this.setCustomName(Component.literal(this.aiName + " [" + this.safeModeDisplayName() + "]"));
        this.setCustomNameVisible(true);
    }

    private void syncClientState() {
        this.entityData.set(DATA_AI_NAME, this.aiName);
        this.entityData.set(DATA_OWNER_ID, this.ownerId == null ? "" : this.ownerId.toString());
        this.entityData.set(DATA_MODE, this.safeModeCommandName());
        this.entityData.set(DATA_BLUEPRINT, this.activeBlueprintId);
        this.syncClientTelemetry();
    }

    private void syncClientTelemetry() {
        this.lastTelemetrySyncTick = this.tickCount;
        AgentSnapshot snapshot = this.agentRuntime.snapshot();
        PlannedAction currentAction = snapshot.currentPlan() == null ? null : snapshot.currentPlan().findAction(snapshot.currentAction());
        this.entityData.set(DATA_STATUS, this.clipSyncText(this.buildClientStatusLine()));
        this.entityData.set(DATA_GOAL, this.clipSyncText(snapshot.currentGoal().label() + " | 条件=" + snapshot.currentPlan().goalConditionSummary()));
        this.entityData.set(DATA_ACTION, this.clipSyncText(snapshot.currentAction() + (currentAction == null ? "" : " | " + currentAction.detailSummary())));
        this.entityData.set(DATA_PATH, this.clipSyncText(snapshot.pathStatus()));
        this.entityData.set(DATA_MEMORY, this.clipSyncText(snapshot.memory().recentSummary()));
        this.entityData.set(DATA_LLM, this.clipSyncText(snapshot.llmStatus() + " | " + snapshot.plannerStatus()));
        this.entityData.set(DATA_PLAN, this.clipSyncText(snapshot.currentPlan().summary()));
        this.entityData.set(DATA_OBSERVATION, this.clipSyncText(this.lastObservation == null || this.lastObservation.isBlank() ? DEFAULT_OBSERVATION : this.lastObservation));
        this.entityData.set(DATA_INVENTORY, this.clipSyncText(this.getInventoryPreview()));
    }

    private String buildClientStatusLine() {
        return "模式=" + this.safeModeDisplayName()
                + " | 生命=" + Math.round(this.getHealth()) + "/" + Math.round(this.getMaxHealth())
                + " | 背包=" + this.getUsedBackpackSlots() + "/" + BACKPACK_SIZE
                + " | 目标=" + this.latestAgentGoal
                + " | 动作=" + this.agentRuntime.currentActionLabel()
                + " | 路径=" + this.agentRuntime.pathStatus()
                + " | 认知=" + this.getCognitiveSummary()
                + (this.pendingDeliveryRequest != null ? " | 待交付" : "");
    }

    private String clipSyncText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() <= 240 ? text : text.substring(0, 240) + "...";
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

    private void reportTaskProgress(String task, String detail) {
        String nextTask = task == null || task.isBlank() ? this.activeTaskName : task;
        if (detail == null || detail.isBlank()) {
            return;
        }
        if (nextTask.equals(this.activeTaskName)
                && detail.equals(this.lastTaskFeedback)
                && !this.lastTaskFeedbackWasFailure
                && this.tickCount - this.lastTaskFeedbackTick < 40) {
            this.lastMeaningfulProgressTick = this.tickCount;
            return;
        }
        this.activeTaskName = nextTask;
        this.lastTaskFeedback = detail;
        this.taskSuccessStreak++;
        this.taskFailureStreak = 0;
        this.lastMeaningfulProgressTick = this.tickCount;
        this.lastTaskFeedbackTick = this.tickCount;
        this.lastTaskFeedbackWasFailure = false;
        this.markPersistentDirty();
    }

    private void reportTaskFailure(String task, String detail) {
        String nextTask = task == null || task.isBlank() ? this.activeTaskName : task;
        if (detail == null || detail.isBlank()) {
            return;
        }
        if (nextTask.equals(this.activeTaskName)
                && detail.equals(this.lastTaskFeedback)
                && this.lastTaskFeedbackWasFailure
                && this.tickCount - this.lastTaskFeedbackTick < 40) {
            return;
        }
        this.activeTaskName = nextTask;
        this.lastTaskFeedback = detail;
        this.taskFailureStreak++;
        this.taskSuccessStreak = 0;
        this.lastTaskFeedbackTick = this.tickCount;
        this.lastTaskFeedbackWasFailure = true;
        this.markPersistentDirty();
    }

    private String buildCognitiveSummary() {
        List<String> parts = new ArrayList<>();
        if (this.observedHostile != null && this.observedHostile.isAlive()) {
            parts.add("感知到敌对威胁");
        }
        if (this.observedDrop != null && this.observedDrop.isAlive()) {
            parts.add("附近有可拾取掉落物");
        }
        if (this.hasLowFoodSupply()) {
            parts.add("食物偏少");
        }
        if (this.hasLowTools()) {
            parts.add("工具不足");
        }
        if (this.rememberedLog != null) {
            parts.add("记得木材位置");
        }
        if (this.rememberedOre != null) {
            parts.add("记得矿点位置");
        }
        if (parts.isEmpty()) {
            return "环境稳定，继续观察";
        }
        return String.join("；", parts);
    }

    private void sendOwnerAlert(String message, boolean force) {
        ServerPlayer owner = this.getOwnerPlayer();
        if (owner == null || owner.isRemoved() || message == null || message.isBlank()) {
            return;
        }
        if (!force && message.equals(this.lastOwnerAlertMessage) && this.tickCount - this.lastOwnerAlertTick < ALERT_COOLDOWN_TICKS) {
            return;
        }
        this.lastOwnerAlertMessage = message;
        this.lastOwnerAlertTick = this.tickCount;
        owner.sendSystemMessage(Component.literal("[" + this.getAIName() + "] " + message));
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


    public AgentSnapshot getAgentRuntimeSnapshot() {
        return this.agentRuntime.snapshot();
    }

    UUID getRuntimeOwnerId() {
        return this.ownerId;
    }

    ServerPlayer getRuntimeOwnerPlayer() {
        return this.getOwnerPlayer();
    }

    void runtimeRefreshPerception() {
        this.scanSurroundings();
    }

    void runtimeTickCombatSense() {
        if (this.observedHostile != null && this.observedHostile.isAlive()) {
            this.setTarget(this.observedHostile);
        }
        this.tickSelfDefense();
        if (this.hasActiveHuntDirective()) {
            this.performHuntDirective();
        }
    }

    private void tickSelfDefense() {
        LivingEntity threat = this.observedHostile != null && this.observedHostile.isAlive() ? this.observedHostile : this.getTarget();
        boolean closeThreat = threat != null && threat.isAlive() && this.distanceToSqr(threat) <= 64.0D;
        boolean arrowThreat = this.hasIncomingArrowThreat(9.0D);
        boolean shouldGuard = closeThreat || arrowThreat;
        if (!shouldGuard) {
            if (this.shieldGuardTicks <= 0 && this.isUsingItem() && this.getUsedItemHand() == InteractionHand.OFF_HAND) {
                this.stopUsingItem();
            }
            return;
        }

        this.ensureShieldInOffhand();
        if (threat != null && threat.isAlive()) {
            this.setTarget(threat);
            this.setForcedLookTarget(threat.getEyePosition(), 8);
            this.equipPreferredWeaponForThreat(threat);
        }
        this.shieldGuardTicks = Math.max(this.shieldGuardTicks, arrowThreat ? 18 : 10);
        if (this.getOffhandItem().getItem() == Items.SHIELD) {
            if (!this.isUsingItem() || this.getUsedItemHand() != InteractionHand.OFF_HAND) {
                this.startUsingItem(InteractionHand.OFF_HAND);
            }
        }
    }

    private void ensureShieldInOffhand() {
        if (this.getOffhandItem().getItem() == Items.SHIELD) {
            return;
        }
        if (this.countBackpackItem(Items.SHIELD) > 0 || this.getOffhandItem().isEmpty()) {
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        }
    }

    private void equipPreferredWeaponForThreat(LivingEntity threat) {
        if (threat == null || !threat.isAlive()) {
            return;
        }
        String requirement = KnowledgeManager.getPreferredWeaponForMob(threat);
        ItemStack preferred = this.findBestToolForRequirement(requirement);
        if (!preferred.isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, preferred.copy());
        }
    }

    private boolean hasIncomingArrowThreat(double radius) {
        List<Projectile> projectiles = this.level().getEntitiesOfClass(
                Projectile.class,
                this.getBoundingBox().inflate(radius, 3.0D, radius),
                projectile -> projectile != null && projectile.isAlive() && projectile.getOwner() != this);
        if (projectiles.isEmpty()) {
            return false;
        }
        Vec3 eye = this.getEyePosition();
        for (Projectile projectile : projectiles) {
            Vec3 velocity = projectile.getDeltaMovement();
            if (velocity.lengthSqr() < 1.0E-4D) {
                continue;
            }
            Vec3 toSelf = eye.subtract(projectile.position());
            if (toSelf.lengthSqr() > radius * radius) {
                continue;
            }
            double alignment = velocity.normalize().dot(toSelf.normalize());
            if (alignment > 0.62D) {
                return true;
            }
        }
        return false;
    }

    WorldStateSnapshot captureWorldStateSnapshot() {
        ServerPlayer owner = this.getOwnerPlayer();
        int logs = 0;
        int oreItems = 0;
        for (ItemStack stack : this.backpack) {
            if (this.isLogItem(stack)) {
                logs += stack.getCount();
            }
            if (this.isOreResourceItem(stack)) {
                oreItems += stack.getCount();
            }
        }
        boolean shelterReady = this.shelterAnchor != null && this.findNextShelterPlacement() == null;
        BlockPos knownWood = this.rememberedLog != null ? this.rememberedLog : TeamKnowledge.findNearestResource(this, ResourceType.WOOD, this.blockPosition());
        BlockPos knownOre = this.rememberedOre != null ? this.rememberedOre : TeamKnowledge.findNearestResource(this, ResourceType.ORE, this.blockPosition());
        BlockPos knownCrop = this.rememberedCrop != null ? this.rememberedCrop : TeamKnowledge.findNearestResource(this, ResourceType.CROP, this.blockPosition());
        BlockPos knownBed = this.rememberedBed != null ? this.rememberedBed : TeamKnowledge.findNearestResource(this, ResourceType.BED, this.blockPosition());
        return new WorldStateSnapshot(
                this.safeMode(),
                owner != null,
                owner == null ? null : owner.blockPosition(),
                owner == null ? Double.MAX_VALUE : Math.sqrt(this.distanceToSqr(owner)),
                this.isOwnerUnderThreat(),
                this.observedHostile != null && this.observedHostile.isAlive(),
                this.getHealth() <= 10.0F,
                this.isInWater() || this.isUnderWater() || this.isInLava(),
                this.isOnFire(),
                this.stuckNavigationTicks >= NAVIGATION_STUCK_THRESHOLD / 2,
                this.pendingDeliveryRequest != null && !this.pendingDeliveryRequest.isBlank(),
                knownWood != null,
                knownWood,
                knownOre != null,
                knownOre,
                this.hasLowFoodSupply(),
                knownCrop != null,
                knownCrop,
                this.observedDrop != null && this.observedDrop.isAlive(),
                knownBed != null,
                knownBed,
                this.rememberedCraftingTable != null || this.rememberedFurnace != null || this.rememberedChest != null,
                this.hasLowTools(),
                (this.level().getDayTime() % 24000L) >= 12500L,
                this.countAvailableBuildingUnits(),
                this.getUsedBackpackSlots(),
                Math.max(0, BACKPACK_SIZE - this.getUsedBackpackSlots()),
                this.countBackpackItem(Items.BREAD),
                this.countBackpackItem(Items.OAK_PLANKS),
                logs,
                this.countBackpackItem(Items.TORCH),
                this.getObservationSummary(),
                this.getInventoryPreview(),
                this.lastTaskFeedback,
                this.getCognitiveSummary(),
                oreItems,
                shelterReady);
    }

    void runtimeApplyGoalDirective(ServerPlayer speaker, AgentGoal goal, boolean pin) {
        this.playerModePinned = pin;
        this.playerPinnedMode = goal.type().coarseMode();
        this.clearTaskAiPlanState();
        this.clearHuntDirective();
        this.agentRuntime.applyDirectedGoal(speaker, goal, pin);
    }

    void runtimeStopGoal() {
        this.clearPlayerModePin();
        this.clearTaskAiPlanState();
        this.clearHuntDirective();
        this.agentRuntime.clearDirectedGoal();
        this.setMode(AIPlayerMode.IDLE);
    }

    boolean runtimeHasActiveHuntDirective() {
        return this.hasActiveHuntDirective();
    }

    boolean runtimePerformHuntDirective() {
        return this.performHuntDirective();
    }

    String runtimeHuntTargetLabel() {
        return this.huntTargetLabel == null ? "" : this.huntTargetLabel;
    }

    String runtimeHandleDeliveryRequest(ServerPlayer speaker, String content) {
        return this.handleDeliveryRequest(speaker, content);
    }

    String runtimeBuildLocalConversationReply(ServerPlayer speaker, String content, boolean apiFailed) {
        return this.generateLocalConversationReply(speaker, content, apiFailed);
    }

    boolean runtimeIsPendingConversation() {
        return this.pendingAiResponse;
    }

    void runtimeSetPendingConversation(boolean pending) {
        this.pendingAiResponse = pending;
    }

    void runtimeMarkDirty() {
        this.markPersistentDirty();
    }

    void runtimeRemember(String type, String detail) {
        this.remember(type, detail);
    }

    boolean runtimeIsWithin(BlockPos pos, double maxDistanceSqr) {
        return pos != null && this.distanceToSqr(Vec3.atCenterOf(pos)) <= maxDistanceSqr;
    }

    boolean runtimeNavigateToPosition(BlockPos pos, double speed) {
        if (pos == null) {
            return false;
        }
        BlockPos resolved = this.runtimeResolveMovementTarget(pos);
        return resolved != null && this.navigateToPosition(resolved, speed);
    }

    boolean runtimeAttemptImmediateRecovery() {
        return this.attemptImmediateRecovery();
    }

    boolean runtimeShouldAttemptImmediateRecovery() {
        return this.isInWater()
                || this.isUnderWater()
                || this.isInLava()
                || this.isOnFire()
                || this.horizontalCollision
                || this.stuckNavigationTicks >= SCAN_INTERVAL * 2
                || this.agentRuntime.movementController().isRecovering();
    }

    boolean runtimeIsMiningLocked() {
        return this.isMiningLocked();
    }

    boolean runtimeTryDiggableAdvance(BlockPos targetPos) {
        return this.tryDiggableAdvance(targetPos);
    }

    void runtimeLookAt(Vec3 target, int ticks) {
        this.setForcedLookTarget(target, ticks);
    }

    BlockPos runtimeFindApproachPosition(BlockPos target) {
        return this.findApproachPosition(target);
    }

    BlockPos runtimeFindExplorationDestination() {
        return this.findExplorationDestination();
    }

    BlockPos runtimeResolveHarvestTarget(boolean woodTask) {
        return this.updateHarvestTaskView(woodTask).target();
    }

    HarvestTaskView runtimeBuildHarvestTaskView(boolean woodTask) {
        return this.updateHarvestTaskView(woodTask);
    }

    AgentGoal runtimeHarvestTaskLockGoal() {
        return this.getHarvestTaskLockGoal();
    }

    BlockPos runtimeFindHarvestObstacle(BlockPos target, boolean woodTask) {
        return this.findHarvestObstacle(target, woodTask);
    }

    boolean runtimeCanHarvestFromHere(BlockPos target) {
        return this.canHarvestFromHere(target);
    }

    boolean runtimeIsValidHarvestTarget(BlockPos pos, boolean woodTask) {
        return this.isValidHarvestTarget(pos, woodTask);
    }

    boolean runtimeHasBreakablePathBlock(BlockPos pos, boolean woodTask) {
        if (pos == null) {
            return false;
        }
        BlockState state = this.level().getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        if (this.isToggleablePathBlock(state)) {
            return true;
        }
        return woodTask ? this.isBreakableHarvestObstacle(state, true) : this.isBreakableNavigationObstacle(state) || this.isBreakableHarvestObstacle(state, false);
    }

    boolean runtimeBreakPathBlock(BlockPos pos, boolean woodTask) {
        if (this.tryOpenToggleablePathBlock(pos, "已开启通路方块@")) {
            this.touchHarvestTaskProgress();
            return true;
        }
        boolean cleared = this.breakAuxiliaryBlock(pos, woodTask ? "已清理树叶@" : "已清理路径障碍@");
        if (cleared) {
            this.touchHarvestTaskProgress();
            if (this.harvestTaskState == HarvestTaskState.CLEAR_OBSTACLE) {
                this.setHarvestTaskState(HarvestTaskState.MOVE_TO_TARGET);
            }
            return true;
        }
        return false;
    }

    boolean runtimeHarvestTarget(BlockPos pos, boolean woodTask) {
        boolean harvested = this.harvestBlock(pos, woodTask);
        if (!harvested) {
            return false;
        }
        this.touchHarvestTaskProgress();
        BlockPos nextTarget = this.findConnectedHarvestTarget(pos, woodTask);
        if (woodTask) {
            this.rememberedLog = nextTarget;
        } else {
            this.rememberedOre = nextTarget;
        }
        if (nextTarget != null && this.isValidHarvestTarget(nextTarget, woodTask)) {
            this.harvestTaskTarget = nextTarget.immutable();
            this.setHarvestTaskState(HarvestTaskState.MOVE_TO_TARGET);
        } else {
            this.harvestTaskTarget = null;
            this.harvestTaskObstacle = null;
            this.harvestTaskMoveTarget = null;
            this.setHarvestTaskState(HarvestTaskState.COLLECT_DROPS);
        }
        this.scanSurroundings();
        return true;
    }

    boolean runtimePrepareHarvestTool(boolean woodTask) {
        if (this.hasActiveHuntDirective()) {
            this.equipHuntCombatLoadout();
            return true;
        }
        ItemStack preferred = woodTask
                ? this.findBestToolInBackpack(this::isAxeItem, Items.STONE_AXE)
                : this.findBestToolInBackpack(this::isPickaxeItem, Items.STONE_PICKAXE);
        if (preferred.isEmpty()) {
            return false;
        }
        this.setItemSlot(EquipmentSlot.MAINHAND, preferred.copy());
        return true;
    }

    boolean runtimeAdjustViewTo(BlockPos target) {
        if (target == null) {
            return false;
        }
        Vec3 center = Vec3.atCenterOf(target);
        this.facePosition(center);
        this.setForcedLookTarget(center, 12);
        return true;
    }

    boolean runtimeRaiseShieldGuard() {
        this.ensureShieldInOffhand();
        if (this.getOffhandItem().getItem() != Items.SHIELD) {
            return false;
        }
        this.shieldGuardTicks = Math.max(this.shieldGuardTicks, 12);
        if (!this.isUsingItem() || this.getUsedItemHand() != InteractionHand.OFF_HAND) {
            this.startUsingItem(InteractionHand.OFF_HAND);
        }
        return true;
    }

    boolean runtimeShouldUseShieldNow() {
        boolean closeThreat = this.observedHostile != null && this.observedHostile.isAlive() && this.distanceToSqr(this.observedHostile) <= 100.0D;
        return closeThreat || this.hasIncomingArrowThreat(10.0D);
    }

    void runtimeLowerShieldGuard() {
        this.shieldGuardTicks = 0;
        if (this.isUsingItem() && this.getUsedItemHand() == InteractionHand.OFF_HAND) {
            this.stopUsingItem();
        }
    }

    boolean runtimeHasNearbyDrops() {
        return this.observedDrop != null && this.observedDrop.isAlive();
    }

    boolean runtimeCollectNearbyDrops() {
        boolean collected = this.autoCollectNearbyDrops();
        if (collected && this.harvestTaskState == HarvestTaskState.COLLECT_DROPS) {
            this.touchHarvestTaskProgress();
        }
        if (this.harvestTaskState == HarvestTaskState.COLLECT_DROPS
                && (this.observedDrop == null || !this.observedDrop.isAlive())) {
            this.setHarvestTaskState(HarvestTaskState.SEARCH_TARGET);
        }
        return collected;
    }

    boolean runtimeCraftBasicTools() {
        if (this.hasActiveHuntDirective()) {
            return false;
        }
        return this.tryCraftStoneTool(Items.STONE_AXE, "石斧")
                | this.tryCraftStoneTool(Items.STONE_PICKAXE, "石镐")
                | this.tryCraftStoneTool(Items.STONE_SHOVEL, "石铲", 1)
                | this.tryCraftStoneTool(Items.STONE_HOE, "石锄", 2);
    }

    boolean runtimeHasLowTools() {
        return this.hasLowTools();
    }

    boolean runtimeHasLowFoodSupply() {
        return this.hasLowFoodSupply();
    }

    boolean runtimeCraftBread() {
        return this.tryCraftBread();
    }

    boolean runtimeEnsurePlanksSupply() {
        return this.ensurePlanksAvailable();
    }

    BlockPos runtimeFindNavigationObstacle(BlockPos target) {
        return this.findNavigationObstacle(target);
    }

    boolean runtimeHasBuildingMaterials() {
        return this.countAvailableBuildingUnits() >= BlueprintRegistry.get(this.activeBlueprintId).requiredUnits();
    }

    boolean runtimeHasPlankSupply() {
        return this.countBackpackItem(Items.OAK_PLANKS) > 0;
    }

    BlockPos runtimeFindNextShelterPlacement() {
        return this.findNextShelterPlacement();
    }

    boolean runtimeCanPlaceShelterBlockAt(BlockPos pos) {
        return pos != null && this.level().getBlockState(pos).isAir();
    }

    boolean runtimePlaceShelterBlock(BlockPos pos) {
        return this.placeShelterBlock(pos);
    }

    boolean runtimeCanBreakPathBlock(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        BlockState state = this.level().getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        if (this.isToggleablePathBlock(state)) {
            return true;
        }
        return this.isBreakableNavigationObstacle(state) || this.isBreakableHarvestObstacle(state, true) || this.isSurfacePathBreakable(state);
    }

    boolean runtimeBreakPathNavigationBlock(BlockPos pos) {
        if (this.tryOpenToggleablePathBlock(pos, "已开启通路方块@")) {
            return true;
        }
        return this.breakAuxiliaryBlock(pos, "已开路@");
    }

    boolean runtimeCanPlacePathSupport(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!serverLevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return false;
        }
        if (!this.level().getBlockState(pos).isAir()) {
            return false;
        }
        BlockState below = this.level().getBlockState(pos.below());
        return !below.getCollisionShape(this.level(), pos.below()).isEmpty() && this.runtimeHasPathSupportBlocks();
    }

    boolean runtimeHasPathSupportBlocks() {
        return this.selectPathSupportItem() != null;
    }

    boolean runtimePlacePathSupport(BlockPos pos) {
        if (!this.runtimeCanPlacePathSupport(pos)) {
            return false;
        }
        Item support = this.selectPathSupportItem();
        if (support == null) {
            return false;
        }
        Block block = (support instanceof BlockItem blockItem) ? blockItem.getBlock() : Blocks.OAK_PLANKS;
        BlockState placement = block.defaultBlockState();
        if (!placement.canSurvive(this.level(), pos)) {
            placement = Blocks.OAK_PLANKS.defaultBlockState();
        }
        if (!placement.canSurvive(this.level(), pos)) {
            return false;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        this.facePosition(Vec3.atCenterOf(pos));
        this.swing(InteractionHand.MAIN_HAND);
        serverLevel.setBlock(pos, placement, 3);
        WorldScanner.invalidateAt(serverLevel, pos);
        this.consumeBackpackItem(support, 1);
        this.reportTaskProgress(this.activeTaskName, "已放置路径支撑方块：" + this.formatPos(pos));
        this.lastObservation = "已搭建路径支撑@" + this.formatPos(pos);
        return true;
    }

    void runtimeSetWasdControl(float forward, float strafe, float speed, float targetYaw, boolean sprint, boolean jump) {
        boolean previousJump = this.pendingWasdJump;
        this.pendingWasdForward = Mth.clamp(forward, -1.0F, 1.0F);
        this.pendingWasdStrafe = Mth.clamp(strafe, -1.0F, 1.0F);
        this.pendingWasdSpeed = Mth.clamp(speed, 0.0F, 0.42F);
        this.pendingWasdTargetYaw = targetYaw;
        this.pendingWasdSprint = sprint;
        this.pendingWasdJump = jump;
        if (jump && !previousJump) {
            this.pendingWasdJumpQueued = true;
        }
        if (Math.abs(this.pendingWasdForward) > 0.2F || Math.abs(this.pendingWasdStrafe) > 0.12F) {
            this.forcedLookTarget = null;
            this.lookTicks = 0;
        }
        this.wasdOverrideActive = true;
    }

    boolean runtimeApplyPendingWasdInput() {
        if (!this.wasdOverrideActive) {
            return false;
        }
        float yawDelta = Mth.wrapDegrees(this.pendingWasdTargetYaw - this.getYRot());
        float absYawDelta = Math.abs(yawDelta);
        float yawStep = absYawDelta > 100.0F
                ? WASD_MAX_YAW_STEP * 1.65F
                : (absYawDelta > 65.0F ? WASD_MAX_YAW_STEP * 1.35F : WASD_MAX_YAW_STEP);
        float nextYaw = Mth.approachDegrees(this.getYRot(), this.pendingWasdTargetYaw, yawStep);
        this.setYRot(nextYaw);
        this.setYHeadRot(nextYaw);
        this.setYBodyRot(nextYaw);
        this.setZza(this.pendingWasdForward);
        this.setXxa(this.pendingWasdStrafe);
        this.setSpeed(this.pendingWasdSpeed);
        this.setSprinting(this.pendingWasdSprint && this.pendingWasdForward > 0.2F);
        boolean inLiquid = this.isInWater() || this.isUnderWater() || this.isInLava();
        boolean canJumpNow = this.jumpCooldown <= 0 && (this.onGround() || inLiquid);
        boolean holdJumpInLiquid = this.pendingWasdJump && inLiquid && (this.horizontalCollision || this.isUnderWater());
        if ((this.pendingWasdJumpQueued || holdJumpInLiquid) && canJumpNow) {
            this.getJumpControl().jump();
            this.jumpCooldown = inLiquid ? 5 : 7;
            this.pendingWasdJumpQueued = false;
        }
        if (!this.pendingWasdJump) {
            this.pendingWasdJumpQueued = false;
        }
        return true;
    }

    void runtimeClearWasdOverride() {
        this.wasdOverrideActive = false;
        this.pendingWasdForward = 0.0F;
        this.pendingWasdStrafe = 0.0F;
        this.pendingWasdSpeed = 0.0F;
        this.pendingWasdSprint = false;
        this.pendingWasdJump = false;
        this.pendingWasdJumpQueued = false;
    }

    void runtimeApplyCoarseMode(AIPlayerMode mode, boolean pin) {
        if (pin) {
            this.playerModePinned = true;
            this.playerPinnedMode = mode;
        }
        if (this.safeMode() != mode) {
            this.setMode(mode);
        }
    }

    void runtimeApplyGoalSummary(AgentGoal goal, GoalPlan plan, String currentAction, String plannerStatus, String pathStatus, AgentMemory memory) {
        this.latestAgentGoal = goal.label();
        this.latestAgentPlanSummary = plan.summary() + " | 当前动作=" + currentAction + " | 路径=" + pathStatus;
        this.latestAgentReasoning = plannerStatus;
        this.latestCognitiveSummary = this.buildCognitiveSummary()
                + " | 最近失败=" + memory.lastFailure()
                + " | 最近学习=" + memory.lastLearning()
                + " | 团队知识=" + TeamKnowledge.getSummary(this);
        this.setGoalNote(this.latestAgentGoal);
        this.markPersistentDirty();
    }

    public BlockPos runtimeResolveMovementTarget(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        if (this.canStandAt(pos, true)) {
            return pos;
        }
        BlockPos approach = this.findApproachPosition(pos);
        if (approach != null) {
            return approach;
        }
        BlockPos walkable = this.findWalkablePositionNear(pos, 4, 5, true);
        if (walkable != null) {
            return walkable;
        }
        BlockPos expanded = this.findWalkablePositionNear(pos, 7, 6, true);
        if (expanded != null) {
            return expanded;
        }
        return this.findNearbyDryStandPosition(pos, 8, 6);
    }

    boolean runtimeCanStandAt(BlockPos pos) {
        return this.canStandAt(pos, false);
    }

    boolean runtimeCanStandAt(BlockPos pos, boolean allowWater) {
        return this.canStandAt(pos, allowWater);
    }

    boolean runtimeHasLowCeiling() {
        BlockPos head = this.blockPosition().above();
        BlockPos top = head.above();
        BlockState headState = this.level().getBlockState(head);
        BlockState topState = this.level().getBlockState(top);
        boolean headBlocked = !headState.getCollisionShape(this.level(), head).isEmpty();
        boolean topBlocked = !topState.getCollisionShape(this.level(), top).isEmpty();
        return headBlocked || topBlocked;
    }

    BlockPos runtimeFindNearbyDryStandPosition(BlockPos center, int horizontalRadius, int verticalRadius) {
        return this.findNearbyDryStandPosition(center, horizontalRadius, verticalRadius);
    }

    boolean runtimeCanDirectlyTraverse(Vec3 from, Vec3 to) {
        if (from == null || to == null) {
            return false;
        }
        double distance = from.distanceTo(to);
        if (distance <= 0.85D) {
            return true;
        }
        int samples = Mth.clamp((int)Math.ceil(distance / 0.6D), 1, 32);
        AABB baseBox = this.getBoundingBox().inflate(-0.1D, 0.0D, -0.1D);
        boolean allowLiquid = this.isInWater() || this.isUnderWater() || this.isInLava();
        for (int i = 1; i <= samples; i++) {
            Vec3 sample = from.lerp(to, i / (double)samples);
            AABB probe = baseBox.move(sample.x - this.getX(), sample.y - this.getY(), sample.z - this.getZ());
            if (!this.level().noCollision(this, probe)) {
                return false;
            }
            BlockPos feet = BlockPos.containing(sample.x, sample.y + 0.05D, sample.z);
            if (allowLiquid) {
                if (this.level().getFluidState(feet).is(FluidTags.LAVA) || this.level().getFluidState(feet.above()).is(FluidTags.LAVA)) {
                    return false;
                }
                continue;
            }
            BlockState feetState = this.level().getBlockState(feet);
            BlockState headState = this.level().getBlockState(feet.above());
            BlockState floorState = this.level().getBlockState(feet.below());
            if (!this.isPassable(feetState, feet) || !this.isPassable(headState, feet.above())) {
                return false;
            }
            if (this.level().getFluidState(feet).is(FluidTags.WATER) || this.level().getFluidState(feet.above()).is(FluidTags.WATER)) {
                return false;
            }
            if (this.level().getFluidState(feet).is(FluidTags.LAVA) || this.level().getFluidState(feet.above()).is(FluidTags.LAVA)) {
                return false;
            }
            if (!this.isPassable(floorState, feet.below())) {
                continue;
            }
            if (i < samples) {
                return false;
            }
        }
        return true;
    }

    BlockPos resolveRuntimeTarget(String key, BlockPos fallback) {
        return switch (key) {
            case "owner" -> {
                ServerPlayer owner = this.getOwnerPlayer();
                yield owner == null ? fallback : this.findWalkablePositionNear(owner.blockPosition(), 1, 2);
            }
            case "wood" -> {
                BlockPos target = this.rememberedLog != null ? this.rememberedLog : TeamKnowledge.findNearestResource(this, ResourceType.WOOD, this.blockPosition());
                yield target == null ? null : this.runtimeResolveMovementTarget(target);
            }
            case "ore" -> {
                BlockPos target = this.rememberedOre != null ? this.rememberedOre : TeamKnowledge.findNearestResource(this, ResourceType.ORE, this.blockPosition());
                yield target == null ? null : this.runtimeResolveMovementTarget(target);
            }
            case "crop" -> {
                BlockPos target = this.rememberedCrop != null ? this.rememberedCrop : TeamKnowledge.findNearestResource(this, ResourceType.CROP, this.blockPosition());
                yield target == null ? null : this.runtimeResolveMovementTarget(target);
            }
            case "bed" -> {
                BlockPos target = this.rememberedBed != null ? this.rememberedBed : TeamKnowledge.findNearestResource(this, ResourceType.BED, this.blockPosition());
                yield target == null ? null : this.runtimeResolveMovementTarget(target);
            }
            case "delivery_receiver" -> {
                if (this.pendingDeliveryReceiverId == null || !(this.level() instanceof ServerLevel serverLevel)) {
                    yield fallback;
                }
                Player receiverPlayer = serverLevel.getPlayerByUUID(this.pendingDeliveryReceiverId);
                ServerPlayer receiver = receiverPlayer instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                yield receiver == null ? fallback : this.findWalkablePositionNear(receiver.blockPosition(), 2, 3);
            }
            case "explore" -> this.findExplorationDestination();
            default -> fallback == null ? null : this.runtimeResolveMovementTarget(fallback);
        };
    }

    private double scoreApproachCandidate(BlockPos candidate, BlockPos target) {
        double selfDistance = this.distanceToSqr(Vec3.atCenterOf(candidate));
        double targetDistance = candidate.distSqr(target);
        double verticalDelta = Math.abs(candidate.getY() - target.getY());
        return selfDistance + targetDistance * 2.25D + verticalDelta * 0.75D;
    }

    ActionExecutionResult executePlannedAction(PlannedAction action, AgentGoal goal) {
        InteractionPlan interactionPlan = InteractionPlanner.forPlannedAction(this, action, goal);
        if (interactionPlan != null) {
            return InteractionExecutor.execute(this, interactionPlan);
        }
        switch (action.type()) {
            case MOVE_TO_TARGET -> {
                if ("owner".equals(action.targetKey())
                        && this.tryTeleportToOwnerIfTooFar("距离过远，已瞬移跟随主人@")) {
                    return ActionExecutionResult.SUCCESS;
                }
                BlockPos target = this.resolveRuntimeTarget(action.targetKey(), action.fallbackTarget());
                if (target == null) {
                    this.reportTaskFailure(this.activeTaskName, "无法解析动作目标：" + action.label());
                    return ActionExecutionResult.FAILED;
                }
                BlockPos moveTarget = this.runtimeResolveMovementTarget(target);
                if (moveTarget == null) {
                    this.reportTaskFailure(this.activeTaskName, "无法解析可到达站位点：" + this.describePlanTarget(target, action.label()));
                    return ActionExecutionResult.FAILED;
                }
                boolean started = this.navigateToPosition(moveTarget, action.speed());
                if (!started && !this.runtimeIsWithin(moveTarget, 2.56D)) {
                    this.attemptImmediateRecovery();
                    return ActionExecutionResult.RUNNING;
                }
                boolean reached = this.agentRuntime.movementController().hasReachedTarget(moveTarget) || this.runtimeIsWithin(moveTarget, 2.56D);
                return reached ? ActionExecutionResult.SUCCESS : ActionExecutionResult.RUNNING;
            }
            case COLLECT_DROP -> {
                return this.autoCollectNearbyDrops() ? ActionExecutionResult.SUCCESS : ActionExecutionResult.RUNNING;
            }
            case HARVEST_CROP -> {
                if (this.manageNearbyFarm()) {
                    return ActionExecutionResult.RUNNING;
                }
                return this.rememberedCrop == null ? ActionExecutionResult.FAILED : ActionExecutionResult.RUNNING;
            }
            case CRAFT_TORCH -> {
                return this.tryCraftTorchBundle() ? ActionExecutionResult.SUCCESS : ActionExecutionResult.RUNNING;
            }
            case DELIVER_ITEM -> {
                return this.performPendingDeliveryTask() ? ActionExecutionResult.RUNNING : ActionExecutionResult.SUCCESS;
            }
            case RETREAT_TO_SAFE_GROUND -> {
                if (this.autoRecoverFromHazards() || this.attemptImmediateRecovery()) {
                    return ActionExecutionResult.RUNNING;
                }
                return ActionExecutionResult.SUCCESS;
            }
            case REST_AT_BED -> {
                return this.seekKnownRestSpot() ? ActionExecutionResult.RUNNING : ActionExecutionResult.FAILED;
            }
            case OBSERVE_AND_REPORT -> {
                boolean roamingGoal = goal != null && (goal.type() == GoalType.EXPLORE_AREA || goal.type() == GoalType.SURVIVE || goal.type() == GoalType.COLLECT_WOOD || goal.type() == GoalType.COLLECT_ORE || goal.type() == GoalType.COLLECT_FOOD || goal.type() == GoalType.BUILD_SHELTER);
                if (roamingGoal && !this.agentRuntime.movementController().isPathActive()) {
                    BlockPos destination = this.findExplorationDestination();
                    if (destination != null && this.navigateToPosition(destination, 1.0D)) {
                        this.reportTaskProgress(this.activeTaskName, "主动巡视周边，扩展可执行目标");
                        return ActionExecutionResult.RUNNING;
                    }
                }
                this.scanSurroundings();
                this.reportTaskProgress(this.activeTaskName, "观察更新：" + this.getObservationSummary());
                return ActionExecutionResult.SUCCESS;
            }
            default -> {
                return ActionExecutionResult.RUNNING;
            }
        }
    }

    private record DeliveryRequest(String label, boolean deliverAll, int requestedCount, Predicate<ItemStack> matcher) {
    }

    static record HarvestTaskView(
            HarvestTaskState state,
            boolean woodTask,
            BlockPos target,
            BlockPos obstacle,
            BlockPos moveTarget) {
    }

    enum HarvestTaskState {
        IDLE,
        SEARCH_TARGET,
        MOVE_TO_TARGET,
        CLEAR_OBSTACLE,
        MINE_TARGET,
        COLLECT_DROPS,
        COMPLETE,
        FAILED
    }

    private enum MiningMode {
        NONE,
        AUXILIARY,
        HARVEST_WOOD,
        HARVEST_ORE
    }
}


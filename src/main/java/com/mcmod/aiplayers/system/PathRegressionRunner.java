package com.mcmod.aiplayers.system;

import com.mcmod.aiplayers.AIPlayersMod;
import com.mcmod.aiplayers.entity.AIPlayerAction;
import com.mcmod.aiplayers.entity.AIPlayerEntity;
import com.mcmod.aiplayers.entity.AIPlayerMode;
import com.mcmod.aiplayers.registry.ModEntities;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class PathRegressionRunner {
    private static final String ENV_KEY = "AIPLAYERS_REGRESSION";
    private static final String PROPERTY_KEY = "aiplayers.regression";
    private static final boolean ENABLED = isEnabled();
    private static final int SAMPLE_INTERVAL_TICKS = 10;
    private static final int FOLLOW_DURATION_TICKS = 180;
    private static final int GATHER_DURATION_TICKS = 220;
    private static final int RECOVERY_DURATION_TICKS = 220;

    private static RegressionState state;

    private PathRegressionRunner() {
    }

    public static void register() {
        if (!ENABLED) {
            return;
        }
        TickEvent.ServerTickEvent.Post.BUS.addListener(PathRegressionRunner::onServerTick);
    }

    private static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        if (state == null) {
            state = new RegressionState(openLogPath());
            state.log("BOOT", "regression runner enabled");
        }

        ServerLevel level = server.overworld();
        long gameTime = level.getGameTime();
        ServerPlayer player = resolvePlayer(server, state.playerId);
        AIPlayerEntity ai = resolveCompanion(level, state.aiId);

        switch (state.phase) {
            case WAIT_PLAYER -> {
                if (player == null) {
                    return;
                }
                state.playerId = player.getUUID();
                state.origin = player.blockPosition();
                state.log("SETUP", "player ready @" + fmt(state.origin));
                state.phase = Phase.SPAWN_AI;
            }
            case SPAWN_AI -> {
                if (player == null) {
                    state.phase = Phase.WAIT_PLAYER;
                    return;
                }
                if (ai == null) {
                    AIPlayerEntity companion = ModEntities.AI_PLAYER.get().create(level, EntitySpawnReason.EVENT);
                    if (companion == null) {
                        state.log("ERROR", "failed to spawn AI companion");
                        return;
                    }
                    companion.snapTo(player.getX() + 2.0D, player.getY(), player.getZ() + 2.0D, player.getYRot(), 0.0F);
                    companion.initializeCompanion(player, "RegressionBot");
                    level.addFreshEntity(companion);
                    state.aiId = companion.getUUID();
                    ai = companion;
                    state.log("SETUP", "spawned AI @" + fmt(companion.blockPosition()));
                }
                state.stageStartTick = gameTime;
                state.phase = Phase.FOLLOW_SETUP;
            }
            case FOLLOW_SETUP -> {
                if (player == null || ai == null) {
                    state.log("ERROR", "follow setup failed: missing player or ai");
                    state.phase = Phase.FINISHING;
                    state.stageStartTick = gameTime;
                    return;
                }
                ai.applyCommandedMode(player, AIPlayerMode.FOLLOW);
                state.followMoveIndex = 0;
                state.stageStartTick = gameTime;
                state.log("FOLLOW", "start");
                state.phase = Phase.FOLLOW_RUN;
            }
            case FOLLOW_RUN -> {
                if (player == null || ai == null) {
                    state.log("ERROR", "follow run aborted: missing player or ai");
                    state.phase = Phase.FINISHING;
                    state.stageStartTick = gameTime;
                    return;
                }
                if ((gameTime - state.stageStartTick) % 30L == 0L) {
                    movePlayerForFollow(level, player, state);
                }
                sampleStatus("FOLLOW", gameTime, ai, state);
                if (gameTime - state.stageStartTick >= FOLLOW_DURATION_TICKS) {
                    state.phase = Phase.GATHER_SETUP;
                    state.stageStartTick = gameTime;
                }
            }
            case GATHER_SETUP -> {
                if (player == null || ai == null) {
                    state.log("ERROR", "gather setup failed: missing player or ai");
                    state.phase = Phase.FINISHING;
                    state.stageStartTick = gameTime;
                    return;
                }
                BlockPos woodBase = state.origin.offset(12, 0, 0);
                prepareWoodPatch(level, woodBase);
                player.teleportTo(woodBase.getX() - 4.5D, woodBase.getY() + 1.0D, woodBase.getZ() + 0.5D);
                ai.applyCommandedMode(player, AIPlayerMode.GATHER_WOOD);
                state.stageStartTick = gameTime;
                state.log("GATHER", "start @" + fmt(woodBase));
                state.phase = Phase.GATHER_RUN;
            }
            case GATHER_RUN -> {
                if (ai == null) {
                    state.log("ERROR", "gather run aborted: missing ai");
                    state.phase = Phase.FINISHING;
                    state.stageStartTick = gameTime;
                    return;
                }
                sampleStatus("GATHER", gameTime, ai, state);
                if (gameTime - state.stageStartTick >= GATHER_DURATION_TICKS) {
                    state.phase = Phase.RECOVERY_SETUP;
                    state.stageStartTick = gameTime;
                }
            }
            case RECOVERY_SETUP -> {
                if (player == null || ai == null) {
                    state.log("ERROR", "recovery setup failed: missing player or ai");
                    state.phase = Phase.FINISHING;
                    state.stageStartTick = gameTime;
                    return;
                }
                BlockPos poolCenter = state.origin.offset(24, 0, 0);
                prepareWaterPool(level, poolCenter);
                player.teleportTo(poolCenter.getX() - 5.5D, poolCenter.getY() + 2.0D, poolCenter.getZ() + 0.5D);
                ai.teleportTo(poolCenter.getX() + 0.5D, poolCenter.getY() + 1.0D, poolCenter.getZ() + 0.5D);
                ai.applyCommandedMode(player, AIPlayerMode.SURVIVE);
                ai.performAction(AIPlayerAction.RECOVER);
                state.stageStartTick = gameTime;
                state.log("RECOVER", "start @" + fmt(poolCenter));
                state.phase = Phase.RECOVERY_RUN;
            }
            case RECOVERY_RUN -> {
                if (ai == null) {
                    state.log("ERROR", "recovery run aborted: missing ai");
                    state.phase = Phase.FINISHING;
                    state.stageStartTick = gameTime;
                    return;
                }
                sampleStatus("RECOVER", gameTime, ai, state);
                if (gameTime - state.stageStartTick >= RECOVERY_DURATION_TICKS) {
                    state.log("DONE", "all scenarios completed");
                    state.phase = Phase.FINISHING;
                    state.stageStartTick = gameTime;
                }
            }
            case FINISHING -> {
                if (gameTime - state.stageStartTick == 20L) {
                    state.log("FINISHING", "requesting process exit");
                }
                if (gameTime - state.stageStartTick >= 40L) {
                    System.exit(0);
                }
            }
        }
    }

    private static void sampleStatus(String scenario, long gameTime, AIPlayerEntity ai, RegressionState state) {
        if (gameTime % SAMPLE_INTERVAL_TICKS != 0L) {
            return;
        }
        String plan = sanitize(ai.getPlanSummary());
        String path = sanitize(extractPathStatus(plan));
        String observation = sanitize(ai.getObservationSummary());
        String line = "SCENARIO=" + scenario
                + "|tick=" + gameTime
                + "|path=" + path
                + "|mode=" + ai.getMode().commandName()
                + "|pos=" + fmt(ai.blockPosition())
                + "|obs=" + observation
                + "|plan=" + plan;
        state.log("SAMPLE", line);
    }

    private static void movePlayerForFollow(ServerLevel level, ServerPlayer player, RegressionState state) {
        int[][] offsets = new int[][] {
                {8, 0},
                {0, 8},
                {-8, 0},
                {0, -8},
                {10, 6},
                {-10, 6},
                {-10, -6},
                {10, -6}
        };
        int[] offset = offsets[state.followMoveIndex % offsets.length];
        state.followMoveIndex++;
        BlockPos target = state.origin.offset(offset[0], 1, offset[1]);
        player.teleportTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
    }

    private static void prepareWoodPatch(ServerLevel level, BlockPos base) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos floor = base.offset(x, -1, z);
                level.setBlock(floor, Blocks.DIRT.defaultBlockState(), 3);
                for (int y = 0; y <= 4; y++) {
                    level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(base, Blocks.OAK_LOG.defaultBlockState(), 3);
        level.setBlock(base.above(), Blocks.OAK_LOG.defaultBlockState(), 3);
        level.setBlock(base.above(2), Blocks.OAK_LOG.defaultBlockState(), 3);
        level.setBlock(base.north(), Blocks.OAK_LOG.defaultBlockState(), 3);
        level.setBlock(base.south(), Blocks.OAK_LOG.defaultBlockState(), 3);
    }

    private static void prepareWaterPool(ServerLevel level, BlockPos center) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -2; y <= 2; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos floor = center.offset(x, -2, z);
                level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(center.offset(x, -1, z), Blocks.WATER.defaultBlockState(), 3);
                level.setBlock(center.offset(x, 0, z), Blocks.WATER.defaultBlockState(), 3);
            }
        }
    }

    private static ServerPlayer resolvePlayer(MinecraftServer server, UUID preferred) {
        if (preferred != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(preferred);
            if (player != null) {
                return player;
            }
        }
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return null;
        }
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static AIPlayerEntity resolveCompanion(ServerLevel level, UUID id) {
        if (id == null) {
            return null;
        }
        return level.getEntity(id) instanceof AIPlayerEntity ai ? ai : null;
    }

    private static String extractPathStatus(String planSummary) {
        if (planSummary == null || planSummary.isBlank()) {
            return "";
        }
        int pathIndex = planSummary.indexOf("路径=");
        if (pathIndex < 0) {
            return "";
        }
        int begin = pathIndex + 3;
        int end = planSummary.indexOf(" |", begin);
        return end < 0 ? planSummary.substring(begin) : planSummary.substring(begin, end);
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\n', ' ').replace('\r', ' ').replace('|', '/');
    }

    private static String fmt(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static Path openLogPath() {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path dir = Path.of("run", "logs", "telemetry");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir.resolve("path-regression-runclient-" + stamp + ".log");
    }

    private static void append(Path path, String line) {
        try {
            Files.writeString(path, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private static boolean isEnabled() {
        String propertyValue = System.getProperty(PROPERTY_KEY, "false");
        if ("true".equalsIgnoreCase(propertyValue)) {
            return true;
        }
        String envValue = System.getenv(ENV_KEY);
        return envValue != null && "true".equalsIgnoreCase(envValue.trim());
    }

    private enum Phase {
        WAIT_PLAYER,
        SPAWN_AI,
        FOLLOW_SETUP,
        FOLLOW_RUN,
        GATHER_SETUP,
        GATHER_RUN,
        RECOVERY_SETUP,
        RECOVERY_RUN,
        FINISHING
    }

    private static final class RegressionState {
        private final Path logPath;
        private Phase phase = Phase.WAIT_PLAYER;
        private UUID playerId;
        private UUID aiId;
        private BlockPos origin = BlockPos.ZERO;
        private long stageStartTick;
        private int followMoveIndex;

        private RegressionState(Path logPath) {
            this.logPath = logPath;
        }

        private void log(String tag, String message) {
            String line = "REGRESSION|" + tag + "|" + message;
            AIPlayersMod.LOGGER.info(line);
            append(this.logPath, line);
        }
    }
}

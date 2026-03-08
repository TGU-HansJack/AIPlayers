package com.mcmod.aiplayers.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class AntiStuckSystem {
    private final StuckDetector detector;
    private int nodeSwitchLoops;
    private int breakNoProgressTicks;
    private int frequentRepathTicks;
    private BlockPosHash lastNodeHash = BlockPosHash.empty();

    AntiStuckSystem(StuckDetector detector) {
        this.detector = detector;
    }

    StuckSummary sample(Vec3 currentPos, float yaw, long gameTime, PathNode currentNode, boolean breakProgress, boolean repathTriggered) {
        StuckDetector.SampleResult sample = this.detector.sample(currentPos, yaw, gameTime);
        return this.updateFromSample(sample, currentNode, breakProgress, repathTriggered);
    }

    StuckSummary updateFromSample(StuckDetector.SampleResult sample, PathNode currentNode, boolean breakProgress, boolean repathTriggered) {
        if (sample == null) {
            return new StuckSummary(0, 0, this.nodeSwitchLoops, this.breakNoProgressTicks, this.frequentRepathTicks, false, false);
        }
        if (currentNode != null) {
            BlockPosHash hash = BlockPosHash.of(currentNode);
            if (hash.equals(this.lastNodeHash)) {
                this.nodeSwitchLoops = Math.max(0, this.nodeSwitchLoops - 1);
            } else {
                this.nodeSwitchLoops += 2;
                this.lastNodeHash = hash;
            }
        }
        if (!breakProgress) {
            this.breakNoProgressTicks += sample.checked() ? 5 : 0;
        } else {
            this.breakNoProgressTicks = Math.max(0, this.breakNoProgressTicks - 10);
        }
        if (repathTriggered) {
            this.frequentRepathTicks += 10;
        } else {
            this.frequentRepathTicks = Math.max(0, this.frequentRepathTicks - 3);
        }
        boolean severe = sample.stuckTicks() >= 60 || this.breakNoProgressTicks >= 40 || this.frequentRepathTicks >= 40;
        boolean recover = sample.spinTriggered() || sample.stuckTicks() >= 20 || this.nodeSwitchLoops >= 24;
        return new StuckSummary(sample.stuckTicks(), sample.spinTicks(), this.nodeSwitchLoops, this.breakNoProgressTicks, this.frequentRepathTicks, recover, severe);
    }

    void reset(Vec3 pos, float yaw, long gameTime) {
        this.detector.reset(pos, yaw, gameTime);
        this.nodeSwitchLoops = 0;
        this.breakNoProgressTicks = 0;
        this.frequentRepathTicks = 0;
        this.lastNodeHash = BlockPosHash.empty();
    }

    void clear() {
        this.nodeSwitchLoops = 0;
        this.breakNoProgressTicks = 0;
        this.frequentRepathTicks = 0;
        this.lastNodeHash = BlockPosHash.empty();
    }

    record StuckSummary(
            int stuckTicks,
            int spinTicks,
            int nodeSwitchLoops,
            int breakNoProgressTicks,
            int frequentRepathTicks,
            boolean recoverNeeded,
            boolean severe) {
    }

    private record BlockPosHash(int x, int y, int z, int action, int jump) {
        static BlockPosHash of(PathNode node) {
            if (node == null || node.position() == null) {
                return empty();
            }
            return new BlockPosHash(
                    Mth.floor(node.position().x),
                    Mth.floor(node.position().y),
                    Mth.floor(node.position().z),
                    node.action().ordinal(),
                    node.jumpRequired() ? 1 : 0);
        }

        static BlockPosHash empty() {
            return new BlockPosHash(0, 0, 0, 0, 0);
        }
    }
}

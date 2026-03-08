package com.mcmod.aiplayers.entity;

import net.minecraft.server.level.ServerPlayer;

final class AgentRuntime {
    private final AIPlayerEntity entity;
    private final AgentMemory memory = new AgentMemory();
    private final PathManager movementController;
    private final AIPlayerBrain brain;

    AgentRuntime(AIPlayerEntity entity) {
        this.entity = entity;
        this.movementController = new PathManager(entity);
        this.brain = new AIPlayerBrain(entity, this.memory, this.movementController);
    }

    PathManager movementController() {
        return this.movementController;
    }

    void tickWorldScan() {
        this.entity.runtimeRefreshPerception();
        this.brain.tickWorldScan();
    }

    void tickCombat() {
        this.entity.runtimeTickCombatSense();
        this.brain.tickCombat();
    }

    void tickMovement() {
        this.movementController.tick();
    }

    void tickPlanner() {
        if (this.entity.runtimeHasActiveHuntDirective()) {
            this.entity.runtimeApplyGoalSummary(this.snapshot().currentGoal(), this.snapshot().currentPlan(), this.currentActionLabel(), this.plannerStatus(), this.pathStatus(), this.memory);
            return;
        }
        this.brain.tickPlanner();
    }

    void tickExecutor() {
        if (this.entity.runtimeHasActiveHuntDirective()) {
            this.entity.runtimePerformHuntDirective();
            return;
        }
        this.brain.tickExecutor();
    }

    void applyDirectedGoal(ServerPlayer speaker, AgentGoal goal, boolean pin) {
        this.brain.applyDirectedGoal(speaker, goal, pin);
    }

    void clearDirectedGoal() {
        this.brain.clearDirectedGoal();
    }

    String currentActionLabel() {
        return this.brain.currentActionLabel();
    }

    String plannerStatus() {
        return this.brain.plannerStatus();
    }

    String pathStatus() {
        return this.movementController.getPathStatus();
    }

    String lastLearningSummary() {
        return this.memory.lastLearning();
    }

    String lastFailureSummary() {
        return this.memory.lastFailure();
    }

    AgentSnapshot snapshot() {
        return this.brain.snapshot();
    }
}

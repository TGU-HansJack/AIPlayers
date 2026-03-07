package com.mcmod.aiplayers.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

final class AIPlayerGroundNavigation extends GroundPathNavigation {
    AIPlayerGroundNavigation(Mob mob, Level level) {
        super(mob, level);
        this.setCanFloat(true);
        this.setCanOpenDoors(true);
        this.setCanWalkOverFences(false);
        this.setCanPathToTargetsBelowSurface(true);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new WalkNodeEvaluator();
        this.nodeEvaluator.setCanFloat(true);
        this.nodeEvaluator.setCanOpenDoors(true);
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }
}

package com.mcmod.aiplayers.vendor.baritone.api.pathing.goals;

import java.util.Arrays;

// Upstream reference: baritone-1.21.11/src/api/java/baritone/api/pathing/goals/GoalComposite.java
public class GoalComposite implements PathGoal {
    private final Goal[] goals;

    public GoalComposite(Goal... goals) {
        this.goals = goals == null ? new Goal[0] : goals;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        for (Goal goal : this.goals) {
            if (goal != null && goal.isInGoal(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        double min = Double.MAX_VALUE;
        for (Goal goal : this.goals) {
            if (goal != null) {
                min = Math.min(min, goal.heuristic(x, y, z));
            }
        }
        return min == Double.MAX_VALUE ? 0.0D : min;
    }

    public Goal[] goals() {
        return this.goals;
    }

    @Override
    public String toString() {
        return "GoalComposite" + Arrays.toString(this.goals);
    }
}

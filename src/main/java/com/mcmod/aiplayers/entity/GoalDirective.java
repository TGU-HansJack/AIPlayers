package com.mcmod.aiplayers.entity;

public record GoalDirective(String reply, AgentGoal goal, AIPlayerAction action, boolean stopRequested, boolean deliveryRequest) {
    public static GoalDirective info(String reply) {
        return new GoalDirective(reply, null, null, false, false);
    }

    public static GoalDirective goal(String reply, AgentGoal goal) {
        return new GoalDirective(reply, goal, null, false, false);
    }

    public static GoalDirective action(String reply, AIPlayerAction action) {
        return new GoalDirective(reply, null, action, false, false);
    }

    public static GoalDirective stop(String reply) {
        return new GoalDirective(reply, null, null, true, false);
    }

    public static GoalDirective delivery() {
        return new GoalDirective("", null, null, false, true);
    }
}

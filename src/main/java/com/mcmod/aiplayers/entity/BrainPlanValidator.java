package com.mcmod.aiplayers.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;

public final class BrainPlanValidator {
    private static final Set<String> ALLOWED_NODE_TYPES = Set.of("selector", "sequence", "condition", "action", "repeat_until", "timeout");
    private static final Set<String> ALLOWED_ACTION_TYPES = Set.of(
            "move_to",
            "look_at",
            "equip_tool",
            "mine_block",
            "chop_tree",
            "collect_drops",
            "attack_target",
            "place_block",
            "bridge",
            "tunnel",
            "open_container",
            "consume_food",
            "recover_self",
            "observe",
            "harvest_crop",
            "deliver_item",
            "gather_resource",
            "execute_resource_plan");
    private static final Set<String> ALLOWED_ARGS = Set.of(
            "target", "kind", "amount", "speed", "reachDistance", "x", "y", "z", "pos", "targetPos",
            "resource", "resourceKind", "targetId", "label", "timeout", "mode", "goal", "source");
    private static final int MAX_TREE_DEPTH = 10;
    private static final int MAX_NODE_COUNT = 64;
    private static final int MAX_TIMEOUT_TICKS = 20 * 60;

    private BrainPlanValidator() {
    }

    public static ValidatedBrainPlan validate(
            AgentBrainResponse response,
            WorldModelSnapshot worldModel,
            SharedMemorySnapshot sharedMemory,
            TaskRequest preferredTask) {
        if (response == null || !response.hasUsablePlan()) {
            return ValidatedBrainPlan.rejected("brain_plan_missing");
        }
        TaskRequest task = response.task();
        if (task == null) {
            return ValidatedBrainPlan.rejected("brain_task_missing");
        }
        if (!isKnownTask(task.taskType())) {
            return ValidatedBrainPlan.rejected("brain_task_unknown:" + task.taskType());
        }
        BehaviorNodeSpec subtree = response.subtree();
        if (subtree == null) {
            return ValidatedBrainPlan.rejected("brain_subtree_missing");
        }
        ValidationState state = new ValidationState();
        String violation = validateNode(subtree, worldModel, task, state, 1);
        if (violation != null) {
            return ValidatedBrainPlan.rejected(violation);
        }
        if (state.nodeCount > MAX_NODE_COUNT) {
            return ValidatedBrainPlan.rejected("brain_subtree_too_large");
        }
        if (conflictsWithWorld(subtree, worldModel, sharedMemory, preferredTask)) {
            return ValidatedBrainPlan.rejected("brain_plan_conflicts_with_world");
        }
        return ValidatedBrainPlan.accepted(response, "brain_plan_validated");
    }

    private static String validateNode(BehaviorNodeSpec node, WorldModelSnapshot worldModel, TaskRequest task, ValidationState state, int depth) {
        if (node == null) {
            return "brain_node_missing";
        }
        state.nodeCount++;
        if (depth > MAX_TREE_DEPTH) {
            return "brain_tree_too_deep";
        }
        String type = normalized(node.type());
        if (!ALLOWED_NODE_TYPES.contains(type)) {
            return "brain_node_type_invalid:" + node.type();
        }
        if (node.timeoutTicks() > MAX_TIMEOUT_TICKS) {
            return "brain_timeout_too_large";
        }
        if ("action".equals(type)) {
            ActionTask action = node.action();
            if (action == null) {
                return "brain_action_missing";
            }
            String actionType = normalized(action.actionType());
            if (!ALLOWED_ACTION_TYPES.contains(actionType)) {
                return "brain_action_invalid:" + actionType;
            }
            if (!allowedArgs(action.args())) {
                return "brain_action_args_invalid:" + actionType;
            }
            String amountViolation = validateAmount(action.args());
            if (amountViolation != null) {
                return amountViolation;
            }
            String kindViolation = validateKind(action.args());
            if (kindViolation != null) {
                return kindViolation;
            }
        }
        if ("condition".equals(type) && (node.condition() == null || node.condition().isBlank())) {
            return "brain_condition_missing";
        }
        for (BehaviorNodeSpec child : node.children()) {
            String violation = validateNode(child, worldModel, task, state, depth + 1);
            if (violation != null) {
                return violation;
            }
        }
        return null;
    }

    private static boolean conflictsWithWorld(
            BehaviorNodeSpec root,
            WorldModelSnapshot worldModel,
            SharedMemorySnapshot sharedMemory,
            TaskRequest preferredTask) {
        if (worldModel == null) {
            return false;
        }
        Set<String> actionTypes = new HashSet<>();
        collectActionTypes(root, actionTypes);
        if (actionTypes.contains("move_to_owner") && !worldModel.ownerAvailable()) {
            return true;
        }
        if (actionTypes.contains("attack_target") && !worldModel.hostileNearby() && preferredTask != null && preferredTask.goalType() == GoalType.GUARD_OWNER) {
            return true;
        }
        if (referencesOwner(root) && !worldModel.ownerAvailable()) {
            return true;
        }
        if (referencesHostile(root) && !worldModel.hostileNearby()) {
            return true;
        }
        if (referencesResourceKind(root, ResourceKind.TREE)
                && worldModel.nearestResource("wood") == null
                && (sharedMemory == null || sharedMemory.nearestResource("wood", BlockPos.ZERO) == null)) {
            return true;
        }
        if (referencesResourceKind(root, ResourceKind.ORE)
                && worldModel.nearestResource("ore") == null
                && (sharedMemory == null || sharedMemory.nearestResource("ore", BlockPos.ZERO) == null)) {
            return true;
        }
        return false;
    }

    private static boolean referencesOwner(BehaviorNodeSpec node) {
        if (node == null) {
            return false;
        }
        if (node.action() != null && "owner".equalsIgnoreCase(node.action().args().getOrDefault("target", ""))) {
            return true;
        }
        return node.children().stream().anyMatch(BrainPlanValidator::referencesOwner);
    }

    private static boolean referencesHostile(BehaviorNodeSpec node) {
        if (node == null) {
            return false;
        }
        if (node.action() != null && "hostile".equalsIgnoreCase(node.action().args().getOrDefault("target", ""))) {
            return true;
        }
        return node.children().stream().anyMatch(BrainPlanValidator::referencesHostile);
    }

    private static boolean referencesResourceKind(BehaviorNodeSpec node, ResourceKind kind) {
        if (node == null || kind == null) {
            return false;
        }
        if (node.action() != null) {
            ResourceKind actionKind = resolveKind(node.action().args());
            if (kind == actionKind) {
                return true;
            }
            String target = normalized(node.action().args().get("target"));
            return switch (kind) {
                case TREE -> "wood".equals(target) || "tree".equals(target) || "log".equals(target);
                case ORE -> "ore".equals(target) || "mine".equals(target);
                case CROP -> "crop".equals(target) || "food".equals(target);
                case ANIMAL -> "animal".equals(target);
            };
        }
        return node.children().stream().anyMatch(child -> referencesResourceKind(child, kind));
    }

    private static void collectActionTypes(BehaviorNodeSpec node, Set<String> sink) {
        if (node == null || sink == null) {
            return;
        }
        if (node.action() != null) {
            sink.add(normalized(node.action().actionType()));
        }
        for (BehaviorNodeSpec child : node.children()) {
            collectActionTypes(child, sink);
        }
    }

    private static String validateAmount(Map<String, String> args) {
        if (args == null || !args.containsKey("amount")) {
            return null;
        }
        try {
            int amount = Integer.parseInt(args.get("amount"));
            return amount > 0 ? null : "brain_amount_invalid";
        } catch (NumberFormatException ignored) {
            return "brain_amount_invalid";
        }
    }

    private static String validateKind(Map<String, String> args) {
        if (args == null) {
            return null;
        }
        String raw = firstNonBlank(args.get("kind"), args.get("resource"), args.get("resourceKind"));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ResourceKind.fromText(raw) != null ? null : "brain_resource_kind_invalid:" + raw;
    }

    private static boolean allowedArgs(Map<String, String> args) {
        if (args == null || args.isEmpty()) {
            return true;
        }
        return args.keySet().stream().allMatch(key -> ALLOWED_ARGS.contains(key));
    }

    private static boolean isKnownTask(String taskType) {
        GoalType type = GoalType.fromLegacyText(taskType);
        return type != null && (type != GoalType.IDLE || GoalType.IDLE.commandName().equalsIgnoreCase(taskType));
    }

    private static ResourceKind resolveKind(Map<String, String> args) {
        if (args == null) {
            return null;
        }
        return ResourceKind.fromText(firstNonBlank(args.get("kind"), args.get("resource"), args.get("resourceKind"), args.get("target")));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class ValidationState {
        private int nodeCount;
    }
}

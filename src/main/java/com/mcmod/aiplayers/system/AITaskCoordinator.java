package com.mcmod.aiplayers.system;

import com.mcmod.aiplayers.entity.AIPlayerEntity;
import com.mcmod.aiplayers.entity.AIPlayerMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public final class AITaskCoordinator {
    private AITaskCoordinator() {
    }

    public static List<AIPlayerEntity> getTeam(AIPlayerEntity self, AIPlayerMode mode, double radius) {
        if (self.level().isClientSide()) {
            return List.of(self);
        }

        List<AIPlayerEntity> team = new ArrayList<>(self.level().getEntitiesOfClass(
                AIPlayerEntity.class,
                new AABB(self.blockPosition()).inflate(radius),
                candidate -> candidate.isAlive()
                        && candidate.getMode() == mode
                        && candidate.getOwnerIdString().equals(self.getOwnerIdString())));
        team.sort(Comparator.comparing(AIPlayerEntity::getStringUUID));
        if (!team.contains(self)) {
            team.add(self);
            team.sort(Comparator.comparing(AIPlayerEntity::getStringUUID));
        }
        return team;
    }

    public static int getTeamSlot(AIPlayerEntity self, AIPlayerMode mode, double radius) {
        List<AIPlayerEntity> team = getTeam(self, mode, radius);
        for (int index = 0; index < team.size(); index++) {
            if (team.get(index).getUUID().equals(self.getUUID())) {
                return index;
            }
        }
        return 0;
    }

    public static int getTeamSize(AIPlayerEntity self, AIPlayerMode mode, double radius) {
        return Math.max(1, getTeam(self, mode, radius).size());
    }

    public static BlockPos resolveSharedAnchor(AIPlayerEntity self, AIPlayerMode mode, BlockPos fallback, double radius) {
        for (AIPlayerEntity teammate : getTeam(self, mode, radius)) {
            BlockPos anchor = teammate.getSharedBuildAnchor();
            if (anchor != null) {
                return anchor;
            }
        }
        return fallback;
    }
}

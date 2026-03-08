package com.mcmod.aiplayers.entity;

import java.util.List;

interface ResourceSelector<T extends ResourceTarget> {
    T select(AIPlayerEntity entity, List<T> candidates, SharedMemorySnapshot sharedMemory);
}

package com.mcmod.aiplayers.entity;

import java.util.List;

interface ResourceScanner<T extends ResourceTarget> {
    List<T> scan(AIPlayerEntity entity, WorldModelSnapshot worldModel);
}

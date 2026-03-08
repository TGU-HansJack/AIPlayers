package com.mcmod.aiplayers.entity;

interface ResourceExecutor<T extends ResourceTarget> {
    void start(T target);

    ResourceExecutionResult tick(AIPlayerEntity entity, PathManager movementController, DropCollector dropCollector);

    void cancel(AIPlayerEntity entity);

    T currentTarget();
}

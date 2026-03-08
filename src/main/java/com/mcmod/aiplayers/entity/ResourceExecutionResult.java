package com.mcmod.aiplayers.entity;

record ResourceExecutionResult(ResourceExecutionStatus status, int progressDelta, String statusText) {
    static ResourceExecutionResult running(String statusText) {
        return new ResourceExecutionResult(ResourceExecutionStatus.RUNNING, 0, statusText);
    }

    static ResourceExecutionResult running(int progressDelta, String statusText) {
        return new ResourceExecutionResult(ResourceExecutionStatus.RUNNING, progressDelta, statusText);
    }

    static ResourceExecutionResult success(int progressDelta, String statusText) {
        return new ResourceExecutionResult(ResourceExecutionStatus.SUCCESS, progressDelta, statusText);
    }

    static ResourceExecutionResult blocked(String statusText) {
        return new ResourceExecutionResult(ResourceExecutionStatus.BLOCKED, 0, statusText);
    }

    static ResourceExecutionResult failure(String statusText) {
        return new ResourceExecutionResult(ResourceExecutionStatus.FAILURE, 0, statusText);
    }
}

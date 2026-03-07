package com.mcmod.aiplayers.ai;

public record AIServiceResponse(String reply, String mode, String action, String source) {
    public boolean hasDirective() {
        return (this.mode != null && !this.mode.isBlank() && !"unchanged".equalsIgnoreCase(this.mode))
                || (this.action != null && !this.action.isBlank() && !"none".equalsIgnoreCase(this.action));
    }
}
package com.mcmod.aiplayers.mindcraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;

public final class MindcraftBotInfo {
    public String name = "";
    public boolean inGame;
    public int viewerPort;
    public boolean socketConnected;
    public String ownerName = "";
    public String ownerUuid = "";
    public String lastOutput = "";
    public String lastError = "";
    public int mindserverPort;
    public JsonObject state;

    public String displayStatus() {
        if (this.inGame) {
            return "在线";
        }
        return this.socketConnected ? "连接中" : "离线";
    }

    public String lastMessage() {
        return this.lastOutput == null || this.lastOutput.isBlank() ? "暂无回复" : this.lastOutput;
    }

    public String gameplaySummary() {
        if (this.state == null) {
            return "状态未知";
        }
        double x = readDouble(this.state, "gameplay", "position", "x");
        double y = readDouble(this.state, "gameplay", "position", "y");
        double z = readDouble(this.state, "gameplay", "position", "z");
        double health = readDouble(this.state, "gameplay", "health");
        double hunger = readDouble(this.state, "gameplay", "hunger");
        String biome = readString(this.state, "gameplay", "biome");
        return String.format(Locale.ROOT, "血量 %.0f | 饥饿 %.0f | 坐标 %.1f %.1f %.1f | 群系 %s", health, hunger, x, y, z, biome);
    }

    public String actionSummary() {
        if (this.state == null) {
            return "动作未知";
        }
        String current = readString(this.state, "action", "current");
        return current == null || current.isBlank() ? "动作未知" : current;
    }

    public String compactSummary() {
        return this.name + " | " + displayStatus() + " | 主人=" + (this.ownerName == null || this.ownerName.isBlank() ? "未绑定" : this.ownerName);
    }

    public String stateJson() {
        return this.state == null ? "" : this.state.toString();
    }

    private static String readString(JsonObject root, String... path) {
        JsonElement current = root;
        for (String part : path) {
            if (current == null || !current.isJsonObject()) {
                return "-";
            }
            current = current.getAsJsonObject().get(part);
        }
        if (current == null || current.isJsonNull()) {
            return "-";
        }
        try {
            return current.getAsString();
        } catch (RuntimeException ignored) {
            return current.toString();
        }
    }

    private static double readDouble(JsonObject root, String... path) {
        JsonElement current = root;
        for (String part : path) {
            if (current == null || !current.isJsonObject()) {
                return 0.0D;
            }
            current = current.getAsJsonObject().get(part);
        }
        if (current == null || current.isJsonNull()) {
            return 0.0D;
        }
        try {
            return current.getAsDouble();
        } catch (RuntimeException ignored) {
            return 0.0D;
        }
    }
}

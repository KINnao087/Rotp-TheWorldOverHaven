package com.inza.twoh.command.agent;

import com.google.gson.JsonObject;
import com.inza.twoh.command.agent.AgentStrings.Wire;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.vector.Vector3d;

/**
 * EN: Read a single online player's state on the server thread without selecting any entity.
 * 中文：在服务端主线程读取一个在线玩家的状态，不做实体选择或射线查询。
 */
public final class GetPlayerContextTool {
    private GetPlayerContextTool() {
    }

    public static JsonObject lookup(MinecraftServer server, String name) {
        ServerPlayerEntity player = server.getPlayerList().getPlayerByName(name);
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty(Wire.OK, player != null);
        if (player == null) {
            snapshot.addProperty(Wire.PLAYER_NAME, name);
            snapshot.addProperty(Wire.ERROR, Wire.OFFLINE);
            return snapshot;
        }

        snapshot.addProperty(Wire.PLAYER_NAME, player.getGameProfile().getName());
        snapshot.addProperty(Wire.DIMENSION, player.getLevel().dimension().location().toString());
        snapshot.add(Wire.POSITION, vector(player.position()));
        snapshot.add(Wire.EYE_POSITION, vector(player.getEyePosition(1.0F)));
        snapshot.add(Wire.LOOK_DIRECTION, vector(player.getViewVector(1.0F).normalize()));
        return snapshot;
    }

    private static JsonObject vector(Vector3d value) {
        JsonObject result = new JsonObject();
        result.addProperty(Wire.X, value.x);
        result.addProperty(Wire.Y, value.y);
        result.addProperty(Wire.Z, value.z);
        return result;
    }
}

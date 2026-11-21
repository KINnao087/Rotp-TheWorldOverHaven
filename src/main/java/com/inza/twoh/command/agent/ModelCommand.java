package com.inza.twoh.command.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.UUID;

/** 校验模型输出，只让执行器收到协议中明确支持的单条命令。 */
public final class ModelCommand {
    private static final int MAX_RESPONSE_LENGTH = 512;

    private ModelCommand() {
    }

    /** 返回 null 表示模型主动放弃执行；格式错误和未授权命令则抛出异常。 */
    public static String parse(String content, UUID aimedTarget) {
        if (content == null || content.length() > MAX_RESPONSE_LENGTH) {
            throw new IllegalArgumentException();
        }

        JsonObject response = new JsonParser().parse(content).getAsJsonObject();
        if (response.entrySet().size() != 1 || !response.has("command")) {
            throw new IllegalArgumentException();
        }

        JsonElement value = response.get("command");
        if (value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException();
        }

        String command = value.getAsString();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        switch (command) {
            case "time set day":
            case "time set night":
            case "weather clear":
            case "weather rain":
                return command;
            default:
                // 只有服务端在发话时捕获的 UUID 才有效，不接受模型另选目标。
                if (aimedTarget != null && command.equals("kill " + aimedTarget)) {
                    return command;
                }
                throw new IllegalArgumentException();
        }
    }
}

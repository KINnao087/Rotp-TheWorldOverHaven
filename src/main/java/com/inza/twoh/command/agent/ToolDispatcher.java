package com.inza.twoh.command.agent;

import com.google.gson.JsonObject;
import com.inza.twoh.command.agent.AgentStrings.Wire;

import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * EN: Resolve only registered tools and validate their arguments before accessing the world.
 * 中文：先检查工具名和参数，再交给已注册的工具读取世界。
 */
public final class ToolDispatcher {
    private static final Pattern PLAYER_NAME = Pattern.compile(Wire.USERNAME_PATTERN);

    private ToolDispatcher() {
    }

    public static JsonObject dispatch(String tool, JsonObject arguments, Function<String, JsonObject> lookup) {
        if (!Wire.CONTEXT_TOOL.equals(tool)) {
            throw new IllegalArgumentException();
        }
        ModelReplyParser.requireSize(arguments, 1);
        String name = ModelReplyParser.string(arguments, Wire.PLAYER_NAME);
        if (!PLAYER_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException();
        }
        return lookup.apply(name);
    }
}

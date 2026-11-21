package com.inza.twoh.command.agent.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.inza.twoh.command.agent.AgentStrings.Wire;

import java.nio.charset.StandardCharsets;

/** EN: Input accounting for text-only chat. 中文：纯文本聊天的输入预算。 */
public final class TokenBudget {
    public static final int RESERVE = 1000;
    public static final int DEFAULT_WINDOW = 8192;
    public static final int LUNA_CONTEXT = 1_050_000;

    private TokenBudget() {
    }

    public static int available(int contextTokens) {
        return contextTokens > RESERVE ? contextTokens - RESERVE : 0;
    }

    // EN: Compatible servers need not expose a tokenizer. Estimate conservatively, not one token per character.
    // 中文：兼容端点不一定提供分词器；按 UTF-8 字节和消息封装保守估算，不把一个汉字当成一个 token。
    public static int estimate(JsonArray messages) {
        long tokens = 8;
        for (JsonElement element : messages) {
            JsonObject message = element.getAsJsonObject();
            tokens += 16L + bytes(message.get(Wire.ROLE).getAsString()) + bytes(message.get(Wire.CONTENT).getAsString());
        }
        return (int) Math.min(Integer.MAX_VALUE, tokens);
    }

    private static int bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}

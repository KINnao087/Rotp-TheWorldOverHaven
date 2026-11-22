package com.inza.twoh.command.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inza.twoh.command.agent.AgentStrings.Wire;
import com.inza.twoh.command.agent.entitiy.ModelConfig;

/**
 * EN: Assemble the common Chat Completions body, then add only applicable sampling parameters.
 * 中文：先组装 Chat Completions 的公共请求体，再补入当前方案适用的采样参数。
 */
final class ChatRequestBuilder {
    private ChatRequestBuilder() {
    }

    static JsonObject build(ModelConfig config, JsonArray messages) {
        JsonObject body = new JsonObject();
        body.addProperty(Wire.MODEL, config.model);
        body.add(Wire.MESSAGES, messages);
        body.addProperty(Wire.STREAM, false);

        if (ModelCapabilities.supportsTemperature(config)) {
            body.addProperty(Wire.TEMPERATURE, 0);
        }
        return body;
    }
}

package com.inza.twoh.command.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inza.twoh.command.agent.AgentStrings.Api;
import com.inza.twoh.command.agent.AgentStrings.Wire;
import com.inza.twoh.command.agent.config.ConfigText;
import com.inza.twoh.command.agent.entitiy.ModelConfig;

import java.net.URI;

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

        if (useAgentTemperature(config)) {
            body.addProperty(Wire.TEMPERATURE, 0);
        }
        return body;
    }

    private static boolean useAgentTemperature(ModelConfig config) {
        if (!Api.GPT6_CHAT_MODEL.matcher(config.model).matches()) {
            return true;
        }

        // EN: We do not request reasoning_effort=none. Omit temperature for GPT-6 routes,
        //     but preserve the Ollama setting for a locally named model.
        // 中文：当前不会显式关闭推理，因此 GPT-6 请求不发 temperature；
        //      若是 Ollama 上的同名模型，则保留原有采样设置。
        String host = URI.create(config.baseUrl).getHost();
        return ConfigText.OLLAMA.equalsIgnoreCase(config.provider)
                && !Api.OPENAI_HOST.equalsIgnoreCase(host);
    }
}

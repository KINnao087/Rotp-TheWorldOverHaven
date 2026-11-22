package com.inza.twoh.command.agent;

import com.inza.twoh.command.agent.config.ConfigText;
import com.inza.twoh.command.agent.entitiy.ModelConfig;

import java.net.URI;
import java.util.regex.Pattern;

/** EN: Deployment-specific request options and default context limits.
 *  中文：按部署判断请求参数和默认上下文上限，不按代理的模型别名猜容量。 */
public final class ModelCapabilities {
    private static final int LUNA_CONTEXT = 1_050_000;
    private static final String OPENAI_HOST = "api.openai.com";
    private static final Pattern GPT6_CHAT_MODEL = Pattern.compile(
            "(?:openai/)?gpt-6(?:\\.\\d+)?-[^/]+"
    );

    private ModelCapabilities() {
    }

    public static boolean officialEndpoint(String baseUrl) {
        return ("https://" + OPENAI_HOST + "/v1").equalsIgnoreCase(baseUrl);
    }

    public static int contextLimit(ModelConfig config) {
        if (officialEndpoint(config.baseUrl) && "gpt-6-luna".equals(config.model)) {
            return config.contextTokens == 0 ? LUNA_CONTEXT : Math.min(config.contextTokens, LUNA_CONTEXT);
        }
        return config.contextTokens;
    }

    static boolean supportsTemperature(ModelConfig config) {
        if (!GPT6_CHAT_MODEL.matcher(config.model).matches()) {
            return true;
        }
        String host = URI.create(config.baseUrl).getHost();
        return ConfigText.OLLAMA.equalsIgnoreCase(config.provider) && !OPENAI_HOST.equalsIgnoreCase(host);
    }
}

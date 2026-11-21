package com.inza.twoh.command.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.inza.twoh.command.agent.AgentStrings.Api;
import com.inza.twoh.command.agent.AgentStrings.Text;
import com.inza.twoh.command.agent.AgentStrings.Wire;
import com.inza.twoh.command.agent.entitiy.ModelConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * EN: Server-side Chat Completions adapter. Handles the network protocol without accessing the game world.
 * 中文：服务端的 Chat Completions 适配器。此处只处理网络协议，不访问游戏世界。
 */
public final class ChatApi implements AgentRunner.Model {
    private static final int CONNECT_TIMEOUT_MS = 4_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_RESPONSE_BYTES = 65_536;

    private final ModelConfig config;
    private final String apiKey;

    public ChatApi(ModelConfig config, String apiKey) {
        this.config = config;
        this.apiKey = apiKey;
    }

    /**
     * EN: This blocking call belongs on a model worker. Its return value is model text, not authorization to execute.
     * 中文：阻塞调用，只能在模型工作线程上运行。返回模型文本，不代表该命令已获准执行。
     */
    @Override
    public String complete(JsonArray messages) throws IOException {
        URL endpoint = new URL(config.baseUrl + Api.COMPLETIONS_PATH);
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        try {
            // EN: Disable redirects so player requests and credentials are not forwarded to another endpoint.
            // 中文：禁止自动重定向，避免把玩家请求或凭据发送给另一个端点。
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod(Api.POST);
            connection.setDoOutput(true);
            connection.setRequestProperty(Api.CONTENT_TYPE, Api.JSON_UTF8);

            if (!apiKey.isEmpty()) {
                connection.setRequestProperty(Api.AUTHORIZATION, Api.BEARER_PREFIX + apiKey);
            }

            JsonObject payload = ChatRequestBuilder.build(config, messages);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException(Text.HTTP_ERROR_PREFIX + status);
            }
            return readContent(connection);
        } finally {
            connection.disconnect();
        }
    }

    private static String readContent(HttpURLConnection connection) throws IOException {
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int count;
            while ((count = input.read(buffer)) != -1) {
                // EN: Enforce the limit on bytes read rather than trusting the remote Content-Length header.
                // 中文：根据实际读取量限制大小，不依赖远端提供 Content-Length。
                if (output.size() + count > MAX_RESPONSE_BYTES) {
                    throw new IOException(Text.RESPONSE_TOO_LARGE);
                }
                output.write(buffer, 0, count);
            }

            JsonObject response = new JsonParser().parse(output.toString(StandardCharsets.UTF_8.name())).getAsJsonObject();
            JsonObject choice = response.getAsJsonArray(Wire.CHOICES).get(0).getAsJsonObject();
            return choice.getAsJsonObject(Wire.MESSAGE).get(Wire.CONTENT).getAsString();
        }
    }
}

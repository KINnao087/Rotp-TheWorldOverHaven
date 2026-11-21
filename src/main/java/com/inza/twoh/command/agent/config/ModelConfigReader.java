package com.inza.twoh.command.agent.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inza.twoh.command.agent.entitiy.ModelConfig;
import com.inza.twoh.command.agent.entitiy.ProfileCatalog;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EN: Reconstruct validated objects. An unreadable or damaged file is not an empty configuration.
 * 中文：重新构造并校验配置对象，不能将损坏或不可读的文件当作空配置覆盖。
 */
public final class ModelConfigReader {
    public ProfileCatalog read(Path directory) throws IOException {
        Path file = directory.resolve(ConfigText.FILE);
        if (Files.notExists(file)) {
            return new ProfileCatalog("", new LinkedHashMap<>());
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject data = new JsonParser().parse(reader).getAsJsonObject();
            if (data.get("version").getAsInt() != 1) {
                throw new IOException(ConfigText.STORAGE_ERROR);
            }
            Map<String, ModelConfig> profiles = new LinkedHashMap<>();
            for (JsonElement item : data.getAsJsonArray("profiles")) {
                JsonObject row = item.getAsJsonObject();
                ModelConfig config = new ModelConfig(row.get("id").getAsString(), row.get("provider").getAsString(),
                        row.get("baseUrl").getAsString(), row.get("model").getAsString(),
                        row.get("credentialRef").getAsString());
                if (profiles.put(config.id, config) != null || profiles.size() > ConfigText.MAX_PROFILES) {
                    throw new IOException(ConfigText.STORAGE_ERROR);
                }
            }
            String active = data.get("active").getAsString();
            if (!profiles.containsKey(active)) {
                throw new IOException(ConfigText.STORAGE_ERROR);
            }
            return new ProfileCatalog(active, profiles);
        } catch (RuntimeException invalid) {
            throw new IOException(ConfigText.STORAGE_ERROR, invalid);
        }
    }
}

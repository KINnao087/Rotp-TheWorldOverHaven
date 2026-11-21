package com.inza.twoh.command.agent.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inza.twoh.command.agent.AgentStrings.Api;
import com.inza.twoh.command.agent.entitiy.ModelConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * EN: Optional, explicit plaintext backend. Bind each immutable credential to its original destination.
 * 中文：明确选择后才启用的明文存储；每版凭据只允许发往绑定的地址与供应商。
 */
final class CredentialStore {
    private final Path directory;

    CredentialStore(Path directory) {
        this.directory = directory.resolve(ConfigText.VAULT);
    }

    String write(ModelConfig profile, String secret) throws IOException {
        String reference = UUID.randomUUID().toString();
        JsonObject record = new JsonObject();
        record.addProperty("baseUrl", profile.baseUrl);
        record.addProperty("provider", profile.provider);
        record.addProperty("secret", secret);
        ModelConfigPersister.writeAtomic(directory.resolve(reference), record.toString());
        return reference;
    }

    String resolve(ModelConfig profile) throws IOException {
        if (profile.credentialRef.isEmpty()) {
            return "";
        }
        if (ConfigText.ENV.equals(profile.credentialRef)) {
            ModelConfig origin = ModelRuntime.environment();
            if (!profile.sameDestination(origin)) {
                throw new IOException(ConfigText.REBIND);
            }
            String key = System.getenv(Api.KEY_ENV);
            if (key == null || key.isEmpty()) {
                throw new IOException(ConfigText.STORAGE_ERROR);
            }
            return key;
        }
        try {
            String json = new String(Files.readAllBytes(directory.resolve(profile.credentialRef)), StandardCharsets.UTF_8);
            JsonObject record = new JsonParser().parse(json).getAsJsonObject();
            if (!profile.baseUrl.equals(record.get("baseUrl").getAsString())
                    || !profile.provider.equals(record.get("provider").getAsString())) {
                throw new IOException(ConfigText.REBIND);
            }
            return record.get("secret").getAsString();
        } catch (RuntimeException invalid) {
            throw new IOException(ConfigText.STORAGE_ERROR, invalid);
        }
    }

    void forget(String reference) throws IOException {
        if (!reference.isEmpty() && !ConfigText.ENV.equals(reference)) {
            Files.deleteIfExists(directory.resolve(reference));
        }
    }
}

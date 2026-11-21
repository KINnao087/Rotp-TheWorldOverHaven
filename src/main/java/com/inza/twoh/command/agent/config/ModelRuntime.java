package com.inza.twoh.command.agent.config;

import com.inza.twoh.command.agent.AgentStrings.Api;
import com.inza.twoh.command.agent.ChatApi;
import com.inza.twoh.command.agent.entitiy.ConfigDraft;
import com.inza.twoh.command.agent.entitiy.ModelConfig;
import com.inza.twoh.command.agent.entitiy.ProfileCatalog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * EN: One server's configuration service. Publish immutable clients only after persistence succeeds.
 * 中文：每个服务端独立的配置服务；仅在写盘成功后发布新的不可变客户端。
 */
public final class ModelRuntime {
    private final Path directory;
    private final CredentialStore credentials;
    private final ModelConfigPersister persister = new ModelConfigPersister();
    private volatile ChatApi current;
    private ProfileCatalog catalog;
    private long revision;

    public ModelRuntime(Path directory) throws IOException {
        this.directory = directory;
        this.credentials = new CredentialStore(directory);
        catalog = new ModelConfigReader().read(directory);
        ModelConfig active = catalog.profiles.get(catalog.active);
        if (active == null) {
            active = environment();
            if (active != null) {
                catalog = catalog.select(active);
            }
        }
        if (active != null) {
            current = new ChatApi(active, credentials.resolve(active));
        }
    }

    static ModelConfig environment() {
        String base = System.getenv(Api.BASE_ENV);
        String model = System.getenv(Api.MODEL_ENV);
        if (base == null || base.trim().isEmpty() || model == null || model.trim().isEmpty()) {
            return null;
        }
        String key = System.getenv(Api.KEY_ENV);
        String reference = key == null || key.isEmpty() ? "" : ConfigText.ENV;
        return new ModelConfig(ConfigText.FALLBACK_ID, ConfigText.COMPATIBLE, base, model, reference);
    }

    public ChatApi client() {
        return current;
    }

    public synchronized ProfileCatalog catalog() {
        return catalog;
    }

    public synchronized long revision() {
        return revision;
    }

    private ModelConfig validate(ConfigDraft draft, long expected) {
        if (revision != expected) {
            throw new IllegalArgumentException(ConfigText.STALE);
        }
        if (draft.replacement.length() > 2048 || draft.replacement.chars().anyMatch(Character::isISOControl)
                || (!draft.replacement.isEmpty() && draft.clearKey)) {
            throw new IllegalArgumentException(ConfigText.SECRET_RULE);
        }
        ModelConfig candidate = new ModelConfig(draft.id, draft.provider, draft.baseUrl, draft.model, "", draft.contextTokens);
        ModelConfig old = catalog.profiles.get(candidate.id);
        if (old == null && catalog.profiles.size() >= ConfigText.MAX_PROFILES) {
            throw new IllegalArgumentException(ConfigText.INVALID);
        }
        if (old != null && !old.credentialRef.isEmpty() && draft.replacement.isEmpty() && !draft.clearKey) {
            if (!candidate.sameDestination(old)) {
                throw new IllegalArgumentException(ConfigText.REBIND);
            }
            return withReference(candidate, old.credentialRef);
        }
        return candidate;
    }

    public synchronized ChatApi forTest(ConfigDraft draft, long expected) throws IOException {
        ModelConfig profile = validate(draft, expected);
        String secret = draft.replacement.isEmpty() ? credentials.resolve(profile) : draft.replacement;
        return new ChatApi(profile, secret);
    }

    public synchronized void save(ConfigDraft draft, long expected) throws IOException {
        ModelConfig config = validate(draft, expected);
        ModelConfig old = catalog.profiles.get(config.id);
        String replacementRef = "";
        if (!draft.replacement.isEmpty()) {
            if (!draft.allowFile) {
                throw new IllegalArgumentException(ConfigText.SECRET_RULE);
            }
            replacementRef = credentials.write(config, draft.replacement);
            config = withReference(config, replacementRef);
        }

        try {
            ChatApi candidate = new ChatApi(config, credentials.resolve(config));
            ProfileCatalog next = catalog.select(config);
            persister.save(directory, next);
            catalog = next;
            current = candidate;
            revision++;
        } catch (IOException | RuntimeException failure) {
            cleanup(replacementRef);
            throw failure;
        }
        if (old != null && !old.credentialRef.equals(config.credentialRef)) {
            cleanup(old.credentialRef);
        }
    }

    private void cleanup(String reference) {
        try {
            credentials.forget(reference);
        } catch (IOException failure) {
            Logger.getLogger(ModelRuntime.class.getName()).warning("Unused credential file could not be removed.");
        }
    }

    private static ModelConfig withReference(ModelConfig profile, String reference) {
        return new ModelConfig(profile.id, profile.provider, profile.baseUrl, profile.model, reference, profile.contextTokens);
    }
}

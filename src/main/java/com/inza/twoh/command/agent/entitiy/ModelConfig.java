package com.inza.twoh.command.agent.entitiy;

import com.inza.twoh.command.agent.config.ConfigText;

import java.net.URI;
import java.util.Objects;

/**
 * EN: An immutable server profile. The reference is not the credential itself.
 * 中文：不可变服务端方案，只保存凭据引用，不包含真实密钥。
 */
public final class ModelConfig {
    public final String id;
    public final String provider;
    public final String baseUrl;
    public final String model;
    public final String credentialRef;

    public ModelConfig(String id, String provider, String baseUrl, String model, String reference) {
        this.id = text(id, 48);
        this.provider = text(provider, 48);
        this.model = text(model, 128);
        URI url = URI.create(text(baseUrl, 512).replaceAll("/+$", ""));
        String host = url.getHost();
        if (host == null || url.getUserInfo() != null || url.getFragment() != null || url.getQuery() != null) {
            throw new IllegalArgumentException(ConfigText.INVALID);
        }
        boolean local = host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("[::1]");
        if (!"https".equals(url.getScheme()) && !(local && "http".equals(url.getScheme()))) {
            throw new IllegalArgumentException(ConfigText.INVALID);
        }
        this.baseUrl = url.toASCIIString();
        this.credentialRef = Objects.requireNonNull(reference);
        if (!(reference.isEmpty() || reference.equals(ConfigText.ENV) || reference.matches("[a-f0-9-]{36}"))) {
            throw new IllegalArgumentException(ConfigText.INVALID);
        }
    }

    public boolean sameDestination(ModelConfig other) {
        return other != null && baseUrl.equals(other.baseUrl) && provider.equals(other.provider);
    }

    private static String text(String value, int max) {
        if (value == null || value.trim().isEmpty() || value.length() > max
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(ConfigText.INVALID);
        }
        return value.trim();
    }
}

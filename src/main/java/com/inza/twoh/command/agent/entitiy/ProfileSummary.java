package com.inza.twoh.command.agent.entitiy;

/**
 * EN: The only profile data returned to an administrator's screen; no secret or internal reference.
 * 中文：设置页只能收到这份摘要，既不含密钥，也不含服务端凭据引用。
 */
public final class ProfileSummary {
    public final String id;
    public final String provider;
    public final String baseUrl;
    public final String model;
    public final boolean hasKey;

    public ProfileSummary(String id, String provider, String baseUrl, String model, boolean hasKey) {
        this.id = id;
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.model = model;
        this.hasKey = hasKey;
    }
}

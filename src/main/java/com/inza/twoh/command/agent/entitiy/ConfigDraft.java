package com.inza.twoh.command.agent.entitiy;

/**
 * EN: Uncommitted input. Never serialize a draft to disk or send it back to a client.
 * 中文：未提交的输入，禁止将草稿整体写盘、记录日志或回传客户端。
 */
public final class ConfigDraft {
    public final String id;
    public final String provider;
    public final String baseUrl;
    public final String model;
    public final String replacement;
    public final boolean clearKey;
    public final boolean allowFile;

    public ConfigDraft(String id, String provider, String baseUrl, String model,
                       String replacement, boolean clearKey, boolean allowFile) {
        this.id = id;
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.model = model;
        this.replacement = replacement;
        this.clearKey = clearKey;
        this.allowFile = allowFile;
    }
}

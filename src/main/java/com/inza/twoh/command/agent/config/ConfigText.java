package com.inza.twoh.command.agent.config;

/**
 * EN: Shared settings labels, protocol literals and storage names.
 * 中文：集中管理设置文案、协议字面量和存储名称。
 */
public final class ConfigText {
    public static final String COMMAND = "config";
    public static final String ENV = "environment";
    public static final String DIRECTORY = "twoh-agent";
    public static final String FILE = "profiles.json";
    public static final String VAULT = "credentials";
    public static final String LOCAL = "local";
    public static final String OLLAMA = "ollama";
    public static final String LOCAL_URL = "http://127.0.0.1:11434/v1";
    public static final String FALLBACK_ID = "legacy-env";
    public static final String COMPATIBLE = "openai-compatible";
    public static final String INVALID = "配置无效：请检查方案、模型名称和地址（远端必须 HTTPS）。";
    public static final String SECRET_RULE = "密钥设置无效：新密钥需要明确同意保存；不可同时替换和清除。";
    public static final String REBIND = "供应商或地址已改变：请重新输入密钥，或明确选择清除。";
    public static final String STORAGE_ERROR = "无法读取或写入服务端配置。旧配置未被覆盖，请检查配置或文件权限。";
    public static final String STALE = "设置已被更新，请重新打开设置页。";
    public static final String SAVE_OK = "已保存并应用；新任务开始使用新配置。";
    public static final String TEST_OK = "模型接口连接测试成功（未保存）。";
    public static final String TEST_FAIL = "模型测试失败，请检查地址、模型名、密钥或超时。";
    public static final String TEST_BUSY = "已有连接测试在进行。";
    public static final String TITLE = "gui.twohaddon.config.title";
    public static final String[] LABELS = {
            "gui.twohaddon.config.profile",
            "gui.twohaddon.config.provider",
            "gui.twohaddon.config.url",
            "gui.twohaddon.config.model",
            "gui.twohaddon.config.key",
            "gui.twohaddon.config.context"
    };
    public static final String SELECT = "gui.twohaddon.config.select";
    public static final String NEW = "gui.twohaddon.config.new";
    public static final String SAVE = "gui.twohaddon.config.save";
    public static final String TEST = "gui.twohaddon.config.test";
    public static final String CLEAR = "gui.twohaddon.config.clear";
    public static final String CONSENT = "gui.twohaddon.config.consent";
    public static final String HAS_KEY = "gui.twohaddon.config.has_key";
    public static final String NO_KEY = "gui.twohaddon.config.no_key";
    public static final String ACTIVE = "gui.twohaddon.config.active";
    public static final String ON = "gui.twohaddon.config.on";
    public static final String OFF = "gui.twohaddon.config.off";
    public static final String PROBE = "Reply OK.";
    public static final int ADMIN_LEVEL = 4;
    public static final int MAX_PROFILES = 32;

    private ConfigText() {
    }
}

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
    public static final String TITLE = "TWOH · 服务端模型设置";
    public static final String[] LABELS = {"方案 ID", "供应商", "API 基础地址", "模型名", "新 Key（留空保留）", "上下文 token"};
    public static final String SELECT = "切换草稿";
    public static final String NEW = "新建";
    public static final String SAVE = "保存并应用";
    public static final String TEST = "测试连接";
    public static final String CLEAR = "清除密钥";
    public static final String CONSENT = "允许文件明文存储新 Key";
    public static final String HAS_KEY = "已设置密钥";
    public static final String NO_KEY = "无密钥";
    public static final String ACTIVE = "当前启用：";
    public static final String ON = "是";
    public static final String OFF = "否";
    public static final String PROBE = "Reply OK.";
    public static final int ADMIN_LEVEL = 4;
    public static final int MAX_PROFILES = 32;

    private ConfigText() {
    }
}

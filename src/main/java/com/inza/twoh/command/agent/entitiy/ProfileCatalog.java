package com.inza.twoh.command.agent.entitiy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EN: A snapshot of all saved profiles and the active selection.
 * 中文：全部已保存方案及当前选择的只读快照。
 */
public final class ProfileCatalog {
    public final String active;
    public final Map<String, ModelConfig> profiles;

    public ProfileCatalog(String active, Map<String, ModelConfig> profiles) {
        this.active = active;
        this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
    }

    public ProfileCatalog select(ModelConfig config) {
        Map<String, ModelConfig> next = new LinkedHashMap<>(profiles);
        next.put(config.id, config);
        return new ProfileCatalog(config.id, next);
    }
}

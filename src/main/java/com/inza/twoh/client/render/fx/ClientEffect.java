package com.inza.twoh.client.render.fx;

/** EN: One client-side effect in one world; never changes server gameplay. 中文：属于一个客户端世界的视觉实例，不修改服务端玩法。 */
public interface ClientEffect extends AutoCloseable {
    // EN: Return false to expire this instance at the end of the client tick.
    // 中文：返回 false 表示本次客户端 tick 结束时销毁该实例。
    default boolean tick() {
        return true;
    }

    void submit(EffectFrame frame, EffectQueue queue);

    @Override
    default void close() {
    }
}

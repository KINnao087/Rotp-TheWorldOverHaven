package com.inza.twoh.mixin.client;

import com.github.standobyte.jojo.capability.world.TimeStopInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * EN: Read the range replicated with this particular cast, not a local configuration default.
 * 中文：读取随本次时停实例同步的范围，而不是本机配置的默认值。
 */
@Mixin(value = TimeStopInstance.class, remap = false)
public interface TimeStopRangeAccessor {
    @Accessor("chunkRange")
    int twoh$getChunkRange();
}

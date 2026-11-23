package com.inza.twoh.mixin.client;

import com.github.standobyte.jojo.network.packets.fromserver.TimeStopInstancePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TimeStopInstancePacket.class, remap = false)
public interface TimeStopPacketId {
    @Accessor("instanceId")
    int twoh$stopId();
}

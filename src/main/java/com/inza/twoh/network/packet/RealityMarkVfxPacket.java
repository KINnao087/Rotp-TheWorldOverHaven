package com.inza.twoh.network.packet;

import com.inza.twoh.client.RealityMarkVfxRenderer;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class RealityMarkVfxPacket {
    private final int targetEntityId;
    private final int durationTicks;

    public RealityMarkVfxPacket(int targetEntityId, int durationTicks) {
        this.targetEntityId = targetEntityId;
        this.durationTicks = durationTicks;
    }

    public static void encode(RealityMarkVfxPacket packet, PacketBuffer buf) {
        buf.writeVarInt(packet.targetEntityId);
        buf.writeVarInt(packet.durationTicks);
    }

    public static RealityMarkVfxPacket decode(PacketBuffer buf) {
        return new RealityMarkVfxPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(RealityMarkVfxPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> RealityMarkVfxRenderer.show(packet.targetEntityId, packet.durationTicks)));
        context.setPacketHandled(true);
    }
}

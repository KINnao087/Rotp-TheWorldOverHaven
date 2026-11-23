package com.inza.twoh.network.packet;

import com.inza.twoh.client.render.timestop.CastScene;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class CastVisualPacket {
    public final int stopId;
    public final ResourceLocation dimension;
    public final UUID owner;
    public final Vector3d origin;
    public final long appearanceSeed;

    public CastVisualPacket(int stopId, ResourceLocation dimension, UUID owner, Vector3d origin, long appearanceSeed) {
        this.stopId = stopId;
        this.dimension = dimension;
        this.owner = owner;
        this.origin = origin;
        this.appearanceSeed = appearanceSeed;
    }

    public void write(PacketBuffer buf) {
        buf.writeVarInt(stopId);
        buf.writeResourceLocation(dimension);
        buf.writeUUID(owner);
        buf.writeDouble(origin.x);
        buf.writeDouble(origin.y);
        buf.writeDouble(origin.z);
        buf.writeLong(appearanceSeed);
    }

    public static CastVisualPacket read(PacketBuffer buf) {
        return new CastVisualPacket(buf.readVarInt(), buf.readResourceLocation(), buf.readUUID(),
                new Vector3d(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readLong());
    }

    public void receive(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CastScene.stage(this)));
        ctx.setPacketHandled(true);
    }
}

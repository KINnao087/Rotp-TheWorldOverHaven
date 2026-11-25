package com.inza.twoh.network;

import com.inza.twoh.ascension.HeavenAscension;
import com.inza.twoh.client.render.ascension.AscensionPrefetch;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class AscensionNetwork {
    private AscensionNetwork() {}

    static void register(SimpleChannel channel, int id, int readyId, int commitId) {
        channel.registerMessage(id, Cue.class, Cue::write, Cue::read, Cue::receive,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(readyId, Ready.class, (r, b) -> b.writeUUID(r.cast),
                b -> new Ready(b.readUUID()), Ready::receive, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(commitId, Commit.class, Commit::write, b -> new Commit(b.readUUID(),
                new Vector3d(b.readDouble(), b.readDouble(), b.readDouble())), Commit::receive,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void start(ServerPlayerEntity recipient, UUID cast, ResourceLocation source, ResourceLocation destination,
                             Vector3d origin, long startGameTick, ChunkPos destinationCenter) {
        send(recipient, new Cue(cast, source, destination, origin, startGameTick, destinationCenter));
    }

    public static void cancel(ServerPlayerEntity recipient, UUID cast) {
        send(recipient, new Cue(cast, null, null, null, 0, null));
    }

    public static void ready(UUID cast) {
        RealityMarkNetwork.CHANNEL.sendToServer(new Ready(cast));
    }

    public static void commit(ServerPlayerEntity recipient, UUID cast, Vector3d eye) {
        RealityMarkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> recipient), new Commit(cast, eye));
    }

    private static final class Commit {
        final UUID cast;
        final Vector3d eye;
        Commit(UUID cast, Vector3d eye) { this.cast = cast; this.eye = eye; }
        void write(PacketBuffer b) {
            b.writeUUID(cast);
            b.writeDouble(eye.x);
            b.writeDouble(eye.y);
            b.writeDouble(eye.z);
        }
        void receive(Supplier<NetworkEvent.Context> source) {
            NetworkEvent.Context ctx = source.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AscensionPrefetch.commit(cast, eye)));
            ctx.setPacketHandled(true);
        }
    }

    private static final class Ready {
        final UUID cast;
        Ready(UUID cast) { this.cast = cast; }
        void receive(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context ctx = supplier.get();
            ServerPlayerEntity sender = ctx.getSender();
            if (sender != null) ctx.enqueueWork(() -> HeavenAscension.ready(sender, cast));
            ctx.setPacketHandled(true);
        }
    }

    private static void send(ServerPlayerEntity recipient, Cue cue) {
        RealityMarkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> recipient), cue);
    }

    private static final class Cue {
        final UUID cast;
        final ResourceLocation source;
        final ResourceLocation destination;
        final Vector3d origin;
        final long start;

        final ChunkPos center;

        Cue(UUID cast, ResourceLocation source, ResourceLocation destination, Vector3d origin, long start, ChunkPos center) {
            this.cast = cast;
            this.source = source;
            this.destination = destination;
            this.origin = origin;
            this.start = start;
            this.center = center;
        }

        void write(PacketBuffer b) {
            b.writeUUID(cast);
            b.writeBoolean(source != null);
            if (source != null) {
                b.writeResourceLocation(source);
                b.writeResourceLocation(destination);
                b.writeDouble(origin.x);
                b.writeDouble(origin.y);
                b.writeDouble(origin.z);
                b.writeLong(start);
                b.writeLong(center.toLong());
            }
        }

        static Cue read(PacketBuffer b) {
            UUID cast = b.readUUID();
            if (!b.readBoolean()) return new Cue(cast, null, null, null, 0, null);
            return new Cue(cast, b.readResourceLocation(), b.readResourceLocation(),
                    new Vector3d(b.readDouble(), b.readDouble(), b.readDouble()), b.readLong(), new ChunkPos(b.readLong()));
        }

        void receive(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (source == null) AscensionPrefetch.cancel(cast);
                else AscensionPrefetch.start(cast, source, destination, origin, start, center);
            }));
            context.setPacketHandled(true);
        }
    }
}

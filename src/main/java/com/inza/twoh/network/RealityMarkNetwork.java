package com.inza.twoh.network;

import com.inza.twoh.AddonMain;
import com.inza.twoh.network.packet.RealityMarkVfxPacket;

import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public final class RealityMarkNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AddonMain.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int packetId;
    private static boolean registered;

    private RealityMarkNetwork() {}

    public static void register() {
        if (registered) {
            return;
        }

        CHANNEL.registerMessage(
                nextPacketId(),
                RealityMarkVfxPacket.class,
                RealityMarkVfxPacket::encode,
                RealityMarkVfxPacket::decode,
                RealityMarkVfxPacket::handle);

        registered = true;
    }

    public static void sendRealityMarkVfx(Entity target, int durationTicks) {
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new RealityMarkVfxPacket(target.getId(), durationTicks));
    }

    private static int nextPacketId() {
        return packetId++;
    }
}

package com.inza.twoh.client.render.ascension;

import com.inza.twoh.AddonMain;
import com.inza.twoh.network.AscensionNetwork;
import com.inza.twoh.portalcore.immersive_portals.ClientWorldLoader;
import com.inza.twoh.portalcore.immersive_portals.CGlobal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Acknowledges only received remote chunks; never constructs a remote world just to poll it. */
@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, value = Dist.CLIENT)
public final class AscensionPrefetch {
    private static UUID pending;
    private static UUID acknowledged;
    private static ChunkPos center;
    private static RegistryKey<World> target;

    private AscensionPrefetch() {}

    public static void start(UUID cast, ResourceLocation source, ResourceLocation destination, Vector3d origin,
                             long startTick, ChunkPos destinationCenter) {
        pending = cast;
        acknowledged = null;
        center = destinationCenter;
        target = RegistryKey.create(Registry.DIMENSION_REGISTRY, destination);
        AscensionTransition.start(cast, source, destination, origin, startTick);
    }

    public static void cancel(UUID cast) {
        if (cast.equals(pending)) pending = null;
        if (cast.equals(acknowledged)) acknowledged = null;
        AscensionTransition.cancel(cast);
    }

    public static void commit(UUID cast, Vector3d eye) {
        if (!cast.equals(acknowledged) || Minecraft.getInstance().player == null) return;
        acknowledged = null;
        CGlobal.clientTeleportationManager.acceptSynchronizationDataFromServer(target, eye, true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending == null || Minecraft.getInstance().level == null
                || !AscensionTransition.covered(pending) || !ClientWorldLoader.getIsInitialized()) return;
        for (ClientWorld level : ClientWorldLoader.getClientWorlds()) {
            if (!level.dimension().equals(target)) continue;
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    if (level.getChunkSource().getChunkNow(center.x + dx, center.z + dz) == null) return;
            // Allocate the remote world's own lightmap while Minecraft.level is still the source.
            // Lazy creation after the level switch would alias the outgoing world's active lightmap.
            ClientWorldLoader.getDimensionRenderHelper(target);
            AscensionNetwork.ready(pending);
            acknowledged = pending;
            pending = null;
            return;
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        pending = null;
        acknowledged = null;
        target = null;
        center = null;
    }
}

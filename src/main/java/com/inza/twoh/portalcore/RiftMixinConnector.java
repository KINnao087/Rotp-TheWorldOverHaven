package com.inza.twoh.portalcore;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

// Select optional hooks without resolving Minecraft client classes on the server.
public final class RiftMixinConnector implements IMixinConnector {
    @Override public void connect() {
        if (FMLEnvironment.dist == Dist.CLIENT &&
            getClass().getClassLoader().getResource("optifine/ZipResourceProvider.class") != null) {
            Mixins.addConfiguration("mixins.twoh-rift.optifine.json");
        }
    }
}

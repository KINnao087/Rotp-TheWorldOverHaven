package com.inza.twoh.client;

import com.inza.twoh.AddonMain;
import com.inza.twoh.portalcore.immersive_portals.ModMainClient;
import com.inza.twoh.client.render.GroundMistEffect;
import com.inza.twoh.client.render.fx.EffectRenderer;
import com.inza.twoh.client.render.rift.SpatialRiftRenderer;
import com.inza.twoh.init.InitEntities;
import com.inza.twoh.client.render.TheWorldOverHavenStandRenderer;
import com.inza.twoh.init.InitDimension;
import com.inza.twoh.init.InitStands;
import com.inza.twoh.portalcore.immersive_portals.render.PortalEntityRenderer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = AddonMain.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientInit {
    
    @SubscribeEvent
    public static void onFMLClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ModMainClient.init();
            EffectRenderer.registerReloadListener();
            EffectRenderer.registerDimension(InitDimension.OVER_HEAVEN, GroundMistEffect::new);
        });
        RenderingRegistry.registerEntityRenderingHandler(InitEntities.RIFT_WINDOW.get(),
                PortalEntityRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(InitEntities.SPATIAL_RIFT.get(), SpatialRiftRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(
                InitStands.STAND_EXAMPLE_STAND.getEntityType(), TheWorldOverHavenStandRenderer::new);
    }
}

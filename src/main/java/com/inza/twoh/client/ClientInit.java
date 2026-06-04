package com.inza.twoh.client;

import com.inza.twoh.AddonMain;
import com.inza.twoh.client.render.OverHeavenRenderTypes;
import com.inza.twoh.client.render.TheWorldOverHavenStandRenderer;
import com.inza.twoh.init.InitEntities;
import com.inza.twoh.init.InitBlock;
import com.inza.twoh.init.InitStands;

import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = AddonMain.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientInit {
    
    @SubscribeEvent
    public static void onFMLClientSetup(FMLClientSetupEvent event) {
        RenderingRegistry.registerEntityRenderingHandler(
                InitStands.STAND_EXAMPLE_STAND.getEntityType(), TheWorldOverHavenStandRenderer::new);
        RenderTypeLookup.setRenderLayer(InitBlock.OVER_HEAVEN_FLOOR.get(), OverHeavenRenderTypes::isOverHeavenFloor);
    }
}

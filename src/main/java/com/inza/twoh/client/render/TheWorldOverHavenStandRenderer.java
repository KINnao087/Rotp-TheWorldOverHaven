package com.inza.twoh.client.render;

import com.github.standobyte.jojo.client.render.entity.model.stand.StandEntityModel;
import com.github.standobyte.jojo.client.render.entity.model.stand.StandModelRegistry;
import com.github.standobyte.jojo.client.render.entity.renderer.stand.StandEntityRenderer;
import com.inza.twoh.AddonMain;
import com.inza.twoh.entity.TheWorldOverHavenStandEntity;

import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;

public class TheWorldOverHavenStandRenderer extends StandEntityRenderer<TheWorldOverHavenStandEntity, StandEntityModel<TheWorldOverHavenStandEntity>> {
    
    public TheWorldOverHavenStandRenderer(EntityRendererManager renderManager) {
        super(renderManager, 
                StandModelRegistry.registerModel(new ResourceLocation(AddonMain.MOD_ID, "the_world_over_haven"), TheWorldOverHavenStandModel::new),
                new ResourceLocation(AddonMain.MOD_ID, "textures/entity/stand/the_world_over_haven.png"), 0);
    }
}

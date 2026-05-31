package com.inza.twoh.client.render;

import com.inza.twoh.AddonMain;
import com.inza.twoh.entity.ExamplePickaxeEntity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;

public class ExamplePickaxeRenderer extends EntityRenderer<ExamplePickaxeEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            AddonMain.MOD_ID, "textures/entity/example_pickaxe.png");

    public ExamplePickaxeRenderer(EntityRendererManager renderManager) {
        super(renderManager);
    }

    @Override
    public ResourceLocation getTextureLocation(ExamplePickaxeEntity entity) {
        return TEXTURE;
    }
}

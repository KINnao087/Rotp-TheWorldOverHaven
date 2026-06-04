package com.inza.twoh.mixin.client;

import com.inza.twoh.client.render.OverHeavenRenderTypes;
import com.inza.twoh.client.render.OverHeavenPortalShader;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Shadow
    public abstract void renderChunkLayer(RenderType renderType, MatrixStack matrixStack, double cameraX, double cameraY, double cameraZ);

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void twohaddon$renderOverHeavenChunkLayer(MatrixStack matrixStack, float partialTicks, long finishTimeNano,
            boolean renderBlockOutline, ActiveRenderInfo activeRenderInfo, GameRenderer gameRenderer,
            LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        Vector3d cameraPos = activeRenderInfo.getPosition();
        OverHeavenPortalShader.setCamera(activeRenderInfo);
        for (RenderType renderType : OverHeavenRenderTypes.OVER_HEAVEN_FLOOR_LAYERS) {
            renderChunkLayer(renderType, matrixStack, cameraPos.x, cameraPos.y, cameraPos.z);
        }
    }
}

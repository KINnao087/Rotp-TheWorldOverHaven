package com.inza.twoh.mixin.client;

import com.inza.twoh.client.render.fx.RiftSceneColor;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.qouteall.immersive_portals.render.RendererUsingFrameBuffer;
import com.qouteall.immersive_portals.render.RendererUsingStencil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {RendererUsingStencil.class, RendererUsingFrameBuffer.class}, remap = false)
public abstract class PortalSceneCaptureMixin {
    @Inject(method = "onBeforeTranslucentRendering", at = @At("HEAD"))
    private void twohaddon$captureSource(MatrixStack matrices, CallbackInfo ci) {
        RiftSceneColor.capture();
    }
}

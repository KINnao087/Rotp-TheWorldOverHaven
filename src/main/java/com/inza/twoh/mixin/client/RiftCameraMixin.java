package com.inza.twoh.mixin.client;

import com.inza.twoh.portalcore.client.RiftViewScope;
import com.inza.twoh.portalcore.immersive_portals.render.context_management.RenderStates;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.math.vector.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class RiftCameraMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ActiveRenderInfo;setAnglesInternal(FF)V",
            remap = false, shift = At.Shift.AFTER))
    private void twohaddon$alignNestedView(float tick, long time, MatrixStack poses, CallbackInfo ci) {
        RiftViewScope.alignCamera(Minecraft.getInstance().gameRenderer.getMainCamera());
    }

    // Shader-based chunk renderers consume the argument rather than the legacy GL projection.
    @ModifyArg(method = "renderLevel", index = 7, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/WorldRenderer;renderLevel(Lcom/mojang/blaze3d/matrix/MatrixStack;FJZLnet/minecraft/client/renderer/ActiveRenderInfo;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/util/math/vector/Matrix4f;)V"))
    private Matrix4f twohaddon$matchParentProjection(Matrix4f projection) {
        return RiftViewScope.hasParentView() && RenderStates.projectionMatrix != null
                ? RenderStates.projectionMatrix : projection;
    }
}

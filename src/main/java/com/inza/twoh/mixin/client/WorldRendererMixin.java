package com.inza.twoh.mixin.client;

import com.inza.twoh.client.render.FloorSurface;
import com.inza.twoh.init.InitBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import com.inza.twoh.client.render.fx.EffectRenderer;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.math.vector.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "setBlockDirty(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;)V", at = @At("HEAD"))
    private void twohaddon$floorChanged(BlockPos pos, BlockState before, BlockState after, CallbackInfo ci) {
        if (before.is(InitBlock.OVER_HEAVEN_FLOOR.get()) || after.is(InitBlock.OVER_HEAVEN_FLOOR.get())) {
            FloorSurface.invalidate(pos);
        }
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void twohaddon$renderOverHeavenChunkLayer(MatrixStack matrixStack, float partialTicks, long finishTimeNano,
            boolean renderBlockOutline, ActiveRenderInfo activeRenderInfo, GameRenderer gameRenderer,
            LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        EffectRenderer.beginFrame(activeRenderInfo, partialTicks, matrixStack.last().pose(), projectionMatrix);
        EffectRenderer.finishPortalFrame();
    }
}

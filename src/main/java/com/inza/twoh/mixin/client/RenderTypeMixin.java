package com.inza.twoh.mixin.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.inza.twoh.client.render.OverHeavenRenderTypes;

import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderType.class)
public class RenderTypeMixin {
    @Inject(method = "chunkBufferLayers", at = @At("RETURN"), cancellable = true)
    private static void twohaddon$addOverHeavenChunkLayer(CallbackInfoReturnable<List<RenderType>> cir) {
        List<RenderType> layers = cir.getReturnValue();
        if (layers.containsAll(OverHeavenRenderTypes.OVER_HEAVEN_FLOOR_LAYERS)) {
            return;
        }

        List<RenderType> extendedLayers = new ArrayList<>(layers);
        for (RenderType renderType : OverHeavenRenderTypes.OVER_HEAVEN_FLOOR_LAYERS) {
            if (!extendedLayers.contains(renderType)) {
                extendedLayers.add(renderType);
            }
        }
        cir.setReturnValue(Collections.unmodifiableList(extendedLayers));
    }
}

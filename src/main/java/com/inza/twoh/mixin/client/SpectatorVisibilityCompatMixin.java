package com.inza.twoh.mixin.client;

import com.github.standobyte.jojo.util.mod.JojoModUtil;
import com.qouteall.immersive_portals.render.context_management.WorldRenderInfo;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** EN: Keep ROTP's normal visibility rule; a nested IP camera is not a spectator.
 * 中文：平时沿用 ROTP 的可见性判断；只有门户的嵌套镜头不按旁观者处理。 */
@Mixin(value = JojoModUtil.class, remap = false)
public abstract class SpectatorVisibilityCompatMixin {
    @Inject(method = "seesInvisibleAsSpectator", at = @At("HEAD"), cancellable = true)
    private static void twohaddon$portalCamera(PlayerEntity player, CallbackInfoReturnable<Boolean> result) {
        if (WorldRenderInfo.isRendering()) {
            result.setReturnValue(false);
        }
    }
}

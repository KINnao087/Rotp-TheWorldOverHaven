package com.inza.twoh.mixin.client;

import com.github.standobyte.jojo.util.mod.JojoModUtil;
import com.inza.twoh.portalcore.immersive_portals.render.context_management.WorldRenderInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * EN: Combine both spectator rules at the vanilla call site, not inside either mod.
 * 中文：只在原版调用点合并两个前置的旁观者规则，不修改前置的方法。
 */
@Mixin(value = Entity.class, priority = 1100)
public abstract class SpectatorVisibilityCompatMixin {
    @Redirect(
            method = "isInvisibleTo",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isSpectator()Z"),
            require = 1,
            allow = 1
    )
    private boolean twohaddon$canSeeInvisible(PlayerEntity viewer) {
        return !WorldRenderInfo.isRendering() && JojoModUtil.seesInvisibleAsSpectator(viewer);
    }
}

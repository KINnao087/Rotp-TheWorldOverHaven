package com.inza.twoh.mixin.client;

import com.github.standobyte.jojo.action.stand.TimeStop;
import com.github.standobyte.jojo.client.render.world.shader.ShaderEffectApplier;
import com.github.standobyte.jojo.network.packets.fromserver.TimeStopInstancePacket;
import com.inza.twoh.client.render.timestop.CastScene;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

@Mixin(value = TimeStopInstancePacket.Handler.class, remap = false)
public abstract class TimeStopPacketVisualMixin {
    // EN: Scope the descriptor to this START; native ROTP still decides if this cast is accepted.
    // 中文：只在处理对应 START 包时提供描述；是否采纳仍由 ROTP 自己决定。
    @Redirect(method = "handle(Lcom/github/standobyte/jojo/network/packets/fromserver/TimeStopInstancePacket;Ljava/util/function/Supplier;)V",
            at = @At(value = "INVOKE", target = "Lcom/github/standobyte/jojo/client/render/world/shader/ShaderEffectApplier;setTimeStopVisuals(Lnet/minecraft/entity/Entity;Lcom/github/standobyte/jojo/action/stand/TimeStop;)V"))
    private void twoh$applyVisual(ShaderEffectApplier shader, Entity caster, TimeStop action,
            TimeStopInstancePacket packet, Supplier<NetworkEvent.Context> context) {
        CastScene.selectStart(((TimeStopPacketId) packet).twoh$stopId());
        try {
            shader.setTimeStopVisuals(caster, action);
        } finally {
            CastScene.finishStart();
        }
    }
}

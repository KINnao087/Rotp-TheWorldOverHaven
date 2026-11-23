package com.inza.twoh.mixin.client;

import com.github.standobyte.jojo.action.stand.TimeStop;
import com.github.standobyte.jojo.client.render.world.shader.ShaderEffectApplier;
import com.inza.twoh.client.render.timestop.CastScene;
import net.minecraft.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShaderEffectApplier.class, remap = false)
public abstract class TimeStopOwnerMixin {
    // EN: Follow the accepted ROTP caster assignment, not the local viewer or a rejected second cast.
    // 中文：仅跟随 ROTP 实际采纳的施放者；不按本机玩家或被忽略的第二次请求判断。
    @Inject(method = "setTimeStopVisuals", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lcom/github/standobyte/jojo/client/render/world/shader/ShaderEffectApplier;timeStopper:Lnet/minecraft/entity/Entity;"))
    private void twoh$rememberCaster(Entity caster, TimeStop action, CallbackInfo ci) {
        CastScene.begin(action);
    }
}

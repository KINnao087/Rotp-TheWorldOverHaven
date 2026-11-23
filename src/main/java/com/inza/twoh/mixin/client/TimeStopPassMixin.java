package com.inza.twoh.mixin.client;

import com.github.standobyte.jojo.client.render.world.shader.CustomShaderGroup;
import com.github.standobyte.jojo.client.render.world.shader.TimeStopShader;
import com.inza.twoh.client.render.timestop.CastShaderPass;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;

@Mixin(value = CustomShaderGroup.class, remap = false)
public abstract class TimeStopPassMixin {
    // EN: Receive the duration and both buffers directly from the native pass factory.
    // 中文：直接接收原版通道的时长和输入输出缓冲，不另设时停计时器。
    @Redirect(method = "getCustomParametersShader", at = @At(value = "NEW",
            target = "com/github/standobyte/jojo/client/render/world/shader/TimeStopShader"))
    private TimeStopShader twoh$wrapActivation(IResourceManager resources, String name,
            Framebuffer input, Framebuffer output, float length) throws IOException {
        return new CastShaderPass(resources, name, input, output, length);
    }
}

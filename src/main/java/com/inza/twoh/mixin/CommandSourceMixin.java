package com.inza.twoh.mixin;

import com.inza.twoh.command.agent.CommandExecutor;
import net.minecraft.command.CommandSource;
import net.minecraft.command.ICommandSource;
import net.minecraft.util.text.ITextComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EN: Forge handles command exceptions internally and reports them through the command source.
 * 中文：Forge 会在内部捕获命令异常，并通过命令源报告错误。
 */
@Mixin(CommandSource.class)
public abstract class CommandSourceMixin {
    @Shadow
    @Final
    private ICommandSource source;

    @Inject(method = "sendFailure", at = @At("HEAD"))
    private void twohaddon$observeFailure(ITextComponent message, CallbackInfo callback) {
        CommandExecutor.recordFailure(source, message);
    }
}

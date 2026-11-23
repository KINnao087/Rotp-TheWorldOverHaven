package com.inza.twoh.mixin;

import com.github.standobyte.jojo.action.stand.TimeStop;
import com.github.standobyte.jojo.capability.world.TimeStopInstance;
import com.github.standobyte.jojo.init.power.stand.ModStandsInit;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.inza.twoh.init.InitStands;
import com.inza.twoh.network.RealityMarkNetwork;
import com.inza.twoh.network.packet.CastVisualPacket;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.PacketDistributor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(value = TimeStopInstance.class, remap = false)
public abstract class TimeStopVisualSyncMixin {
    // EN: One roll per instance, even when ROTP synchronizes the same stop again.
    // 中文：每个时停实例只抽一次，补发同步包也不会换色或换模式。
    @Unique private final long twoh$appearanceSeed = ThreadLocalRandom.current().nextLong();
    @Shadow @Final private LivingEntity user;
    @Shadow @Final public TimeStop action;
    @Shadow public abstract int getId();
    @Shadow public abstract int getTicksLeft();

    // EN: Send the visual identity to the same recipient immediately before ROTP's START packet.
    // 中文：在 ROTP 同步本次时停之前，把服务端确认的视觉身份发给同一名玩家。
    @Inject(method = "syncToClient", at = @At("HEAD"))
    private void twoh$syncVisual(ServerPlayerEntity recipient, CallbackInfo ci) {
        if (user == null || getTicksLeft() <= 0 || action != ModStandsInit.THE_WORLD_TIME_STOP.get()) return;
        boolean twoh = IStandPower.getStandPowerOptional(user)
                .map(power -> power.getType() == InitStands.STAND_EXAMPLE_STAND.getStandType()).orElse(false);
        if (twoh) {
            RealityMarkNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> recipient),
                    new CastVisualPacket(getId(), user.level.dimension().location(),
                            user.getUUID(), user.getEyePosition(1.0F), twoh$appearanceSeed));
        }
    }
}

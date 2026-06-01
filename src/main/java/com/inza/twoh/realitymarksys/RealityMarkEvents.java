package com.inza.twoh.realitymarksys;

import net.minecraft.entity.LivingEntity;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class RealityMarkEvents {
    private RealityMarkEvents() {}

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.world instanceof ServerWorld)) {
            return;
        }

        ServerWorld world = (ServerWorld) event.world;

//        if (world.getGameTime() % 20 != 0) {
//            return;
//        }

        RealityMarkManager.getRegistry(world).cleanupExpired(world.getGameTime());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntityLiving();

        if (!(entity.level instanceof ServerWorld)) {
            return;
        }

        ServerWorld world = (ServerWorld) entity.level;

        // 后期这里可以生成 SoulEntity
        // 第一版先清理标记
        RealityMarkManager.getRegistry(world).clearMarksOnTarget(entity.getUUID());
    }
}

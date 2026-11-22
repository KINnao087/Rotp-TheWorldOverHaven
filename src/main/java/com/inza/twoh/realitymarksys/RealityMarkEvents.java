package com.inza.twoh.realitymarksys;

import com.inza.twoh.AddonMain;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RealityMarkEvents {
    private static final int CLEANUP_INTERVAL_TICKS = 20;

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

        // EN: Lookups reject expired marks immediately; purge stored records once per second.
        // 中文：查询立即忽略过期标记，每秒再清理一次存档索引。
        if (world.getGameTime() % CLEANUP_INTERVAL_TICKS == 0) {
            RealityMarkManager.getRegistry(world).cleanupExpired(world.getGameTime());
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntityLiving();

        if (!(entity.level instanceof ServerWorld)) {
            return;
        }

        ServerWorld world = (ServerWorld) entity.level;

        RealityMarkManager.getRegistry(world).clearMarksOnTarget(entity.getUUID());
    }
}

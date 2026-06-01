package com.inza.twoh.realitymarksys;

import net.minecraft.entity.LivingEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import com.inza.twoh.network.RealityMarkNetwork;

import java.util.Optional;

public final class RealityMarkManager {
    private RealityMarkManager() {}

    public static RealityMark applyMark(LivingEntity owner, LivingEntity target, int durationTicks, int level) {
        if (!(owner.level instanceof ServerWorld)) {
            return null;
        }

        ServerWorld world = (ServerWorld) owner.level;
        RealityMarkRegistry registry = getRegistry(world);

        RealityMark mark = registry.apply(world, owner, target, durationTicks, level);

        applyVFX(target, durationTicks);
        return mark;
    }

    public static boolean isMarkedBy(LivingEntity owner, LivingEntity target) {
        if (!(owner.level instanceof ServerWorld)) {
            return false;
        }

        ServerWorld world = (ServerWorld) owner.level;
        RealityMarkRegistry registry = getRegistry(world);

        return registry.hasMark(world, owner, target);
    }

    public static Optional<RealityMark> getMark(LivingEntity owner, LivingEntity target) {
        if (!(owner.level instanceof ServerWorld)) {
            return Optional.empty();
        }

        ServerWorld world = (ServerWorld) owner.level;
        RealityMarkRegistry registry = getRegistry(world);

        return registry.getMark(owner.getUUID(), target.getUUID(), world.getGameTime());
    }

    public static void consumeMark(RealityMark mark) {
        ServerWorld world = getServerWorldByDimension(mark.getDimensionId());
        if (world == null) {
            return;
        }

        getRegistry(world).consume(mark.getMarkId());
    }

    public static RealityMarkRegistry getRegistry(ServerWorld world) {
        return world.getDataStorage().computeIfAbsent(RealityMarkRegistry::new, RealityMarkRegistry.DATA_NAME);
    }

    private static ServerWorld getServerWorldByDimension(ResourceLocation dimensionId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }

        RegistryKey<World> dimensionKey = RegistryKey.create(Registry.DIMENSION_REGISTRY, dimensionId);
        return server.getLevel(dimensionKey);
    }

    private static void applyVFX(LivingEntity target, int durationTicks) {
        if (!(target.level instanceof ServerWorld)) {
            return;
        }

        ServerWorld world = (ServerWorld) target.level;
        world.sendParticles(
                ParticleTypes.END_ROD,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.5D,
                target.getZ(),
                16,
                target.getBbWidth() * 0.45D,
                target.getBbHeight() * 0.35D,
                target.getBbWidth() * 0.45D,
                0.02D);
        RealityMarkNetwork.sendRealityMarkVfx(target, durationTicks);

    }
}

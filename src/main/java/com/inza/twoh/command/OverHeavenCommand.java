package com.inza.twoh.command;

import java.util.Collection;

import com.inza.twoh.AddonMain;
import com.inza.twoh.init.InitDimension;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.block.Blocks;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class OverHeavenCommand {
    private static final String ROOT = "twoh_over_heaven";
    private static final String RETURN_TAG = AddonMain.MOD_ID + "_over_heaven_return";
    private static final double ARRIVAL_X = 0.5D;
    private static final double ARRIVAL_Y = 65.0D;
    private static final double ARRIVAL_Z = 0.5D;
    private static final int PLATFORM_Y = 64;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal(ROOT)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("send")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> sendToOverHeaven(ctx.getSource(),
                                        EntityArgument.getEntities(ctx, "targets")))))
                .then(Commands.literal("back")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ctx -> returnFromOverHeaven(ctx.getSource(),
                                        EntityArgument.getEntities(ctx, "targets"))))));
    }

    private static int sendToOverHeaven(CommandSource source, Collection<? extends Entity> targets) throws CommandSyntaxException {
        ServerWorld overHeaven = source.getServer().getLevel(InitDimension.OVER_HEAVEN);
        if (overHeaven == null) {
            source.sendFailure(new StringTextComponent("Over Heaven dimension is not loaded: " + InitDimension.OVER_HEAVEN_ID));
            return 0;
        }

        ensureArrivalPlatform(overHeaven);
        int moved = 0;
        for (Entity target : targets) {
            if (!target.canChangeDimensions()) {
                continue;
            }

            CompoundNBT returnPoint = createReturnPoint(target);
            Entity movedEntity = moveEntity(target, overHeaven, ARRIVAL_X, ARRIVAL_Y, ARRIVAL_Z, target.yRot, target.xRot);
            if (movedEntity != null) {
                movedEntity.getPersistentData().put(RETURN_TAG, returnPoint);
                moved++;
            }
        }

        source.sendSuccess(new StringTextComponent("Sent " + moved + " entit" + (moved == 1 ? "y" : "ies")
                + " to " + InitDimension.OVER_HEAVEN_ID), true);
        return moved;
    }

    private static int returnFromOverHeaven(CommandSource source, Collection<? extends Entity> targets) throws CommandSyntaxException {
        int moved = 0;
        for (Entity target : targets) {
            CompoundNBT data = target.getPersistentData();
            if (!data.contains(RETURN_TAG)) {
                continue;
            }

            CompoundNBT returnPoint = data.getCompound(RETURN_TAG);
            ResourceLocation dimensionId = new ResourceLocation(returnPoint.getString("dimension"));
            RegistryKey<World> dimensionKey = RegistryKey.create(Registry.DIMENSION_REGISTRY, dimensionId);
            ServerWorld returnWorld = source.getServer().getLevel(dimensionKey);
            if (returnWorld == null) {
                source.sendFailure(new StringTextComponent("Return dimension is not loaded: " + dimensionId));
                continue;
            }

            Entity movedEntity = moveEntity(target, returnWorld,
                    returnPoint.getDouble("x"),
                    returnPoint.getDouble("y"),
                    returnPoint.getDouble("z"),
                    returnPoint.getFloat("yRot"),
                    returnPoint.getFloat("xRot"));
            if (movedEntity != null) {
                movedEntity.getPersistentData().remove(RETURN_TAG);
                moved++;
            }
        }

        source.sendSuccess(new StringTextComponent("Returned " + moved + " entit" + (moved == 1 ? "y" : "ies")
                + " from " + InitDimension.OVER_HEAVEN_ID), true);
        return moved;
    }

    private static CompoundNBT createReturnPoint(Entity entity) {
        CompoundNBT returnPoint = new CompoundNBT();
        returnPoint.putString("dimension", entity.level.dimension().location().toString());
        returnPoint.putDouble("x", entity.getX());
        returnPoint.putDouble("y", entity.getY());
        returnPoint.putDouble("z", entity.getZ());
        returnPoint.putFloat("yRot", entity.yRot);
        returnPoint.putFloat("xRot", entity.xRot);
        return returnPoint;
    }

    private static Entity moveEntity(Entity entity, ServerWorld targetWorld, double x, double y, double z, float yRot, float xRot) {
        entity.stopRiding();
        entity.ejectPassengers();

        Entity movedEntity = entity;
        if (entity instanceof ServerPlayerEntity) {
            ((ServerPlayerEntity) entity).teleportTo(targetWorld, x, y, z, yRot, xRot);
        }
        else if (entity.level != targetWorld) {
            movedEntity = entity.changeDimension(targetWorld);
            if (movedEntity != null) {
                movedEntity.teleportTo(x, y, z);
            }
        }
        else {
            entity.teleportTo(x, y, z);
        }

        if (movedEntity != null) {
            movedEntity.yRot = yRot;
            movedEntity.xRot = xRot;
            movedEntity.fallDistance = 0.0F;
        }
        return movedEntity;
    }

    private static void ensureArrivalPlatform(ServerWorld world) {
        BlockPos center = new BlockPos(0, PLATFORM_Y, 0);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.setBlock(center.offset(x, 0, z), Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
            }
        }

        for (int y = 1; y <= 3; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    world.setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }
}

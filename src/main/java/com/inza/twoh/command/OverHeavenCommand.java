package com.inza.twoh.command;

import java.util.Collection;

import com.inza.twoh.AddonMain;
import com.inza.twoh.init.InitDimension;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.inza.twoh.rift.RiftTravel;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class OverHeavenCommand {
    private static final String ROOT = "twoh_over_heaven";
    private static final String RETURN_TAG = AddonMain.MOD_ID + "_over_heaven_return";
    private static final Vector3d ARRIVAL_HINT = new Vector3d(0.5D, 65.0D, 0.5D);

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
            source.sendFailure(new TranslationTextComponent("command.twohaddon.missing_dimension", InitDimension.OVER_HEAVEN_ID));
            return 0;
        }

        Vector3d arrival = null;
        int moved = 0;
        for (Entity target : targets) {
            if (target.level == overHeaven || !target.canChangeDimensions()) {
                continue;
            }

            if (arrival == null) {
                arrival = RiftTravel.nearestGround(overHeaven, ARRIVAL_HINT, 0);
                if (arrival == null) {
                    source.sendFailure(new TranslationTextComponent("rift.twohaddon.no_landing"));
                    break;
                }
            }
            CompoundNBT returnPoint = createReturnPoint(target);
            Entity movedEntity = moveEntity(target, overHeaven, arrival.x, arrival.y, arrival.z, target.yRot, target.xRot);
            if (movedEntity != null) {
                movedEntity.getPersistentData().put(RETURN_TAG, returnPoint);
                moved++;
            }
        }

        source.sendSuccess(new TranslationTextComponent("command.twohaddon.sent", moved, InitDimension.OVER_HEAVEN_ID), true);
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
                source.sendFailure(new TranslationTextComponent("command.twohaddon.missing_dimension", dimensionId));
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

        source.sendSuccess(new TranslationTextComponent("command.twohaddon.returned", moved, InitDimension.OVER_HEAVEN_ID), true);
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

}

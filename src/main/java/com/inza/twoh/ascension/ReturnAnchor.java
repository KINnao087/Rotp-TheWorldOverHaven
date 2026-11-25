package com.inza.twoh.ascension;

import com.inza.twoh.AddonMain;
import com.inza.twoh.init.InitDimension;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;

/** A single-use return address. It travels with the actual entity, not with a server process or a cast. */
final class ReturnAnchor {
    private static final String TAG = AddonMain.MOD_ID + "_ascension_origin";
    final RegistryKey<World> dimension;
    final Vector3d feet;
    final float yaw, pitch;

    private ReturnAnchor(RegistryKey<World> dimension, Vector3d feet, float yaw, float pitch) {
        this.dimension = dimension;
        this.feet = feet;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    static ReturnAnchor at(Entity entity) {
        return new ReturnAnchor(entity.level.dimension(), entity.position(), entity.yRot, entity.xRot);
    }

    @Nullable
    static ReturnAnchor read(Entity entity) {
        CompoundNBT address = storage(entity).getCompound(TAG);
        if (!address.contains("dimension", Constants.NBT.TAG_STRING)) return null;
        ResourceLocation dim = ResourceLocation.tryParse(address.getString("dimension"));
        if (dim == null || dim.equals(InitDimension.OVER_HEAVEN_ID)
                || !address.contains("x", Constants.NBT.TAG_ANY_NUMERIC)
                || !address.contains("y", Constants.NBT.TAG_ANY_NUMERIC)
                || !address.contains("z", Constants.NBT.TAG_ANY_NUMERIC)) return null;
        double x = address.getDouble("x"), y = address.getDouble("y"), z = address.getDouble("z");
        float yaw = address.getFloat("yaw"), pitch = address.getFloat("pitch");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return null;
        return new ReturnAnchor(RegistryKey.create(Registry.DIMENSION_REGISTRY, dim), new Vector3d(x, y, z), yaw, pitch);
    }

    void save(Entity entity) {
        CompoundNBT address = new CompoundNBT();
        address.putString("dimension", dimension.location().toString());
        address.putDouble("x", feet.x);
        address.putDouble("y", feet.y);
        address.putDouble("z", feet.z);
        address.putFloat("yaw", yaw);
        address.putFloat("pitch", pitch);
        CompoundNBT root = storage(entity);
        root.put(TAG, address);
        // Forge clones this subtag on player respawn. getCompound returns a detached tag if absent.
        if (entity instanceof PlayerEntity) entity.getPersistentData().put(PlayerEntity.PERSISTED_NBT_TAG, root);
    }

    static void clear(Entity entity) {
        storage(entity).remove(TAG);
    }

    private static CompoundNBT storage(Entity entity) {
        CompoundNBT data = entity.getPersistentData();
        return entity instanceof PlayerEntity ? data.getCompound(PlayerEntity.PERSISTED_NBT_TAG) : data;
    }
}

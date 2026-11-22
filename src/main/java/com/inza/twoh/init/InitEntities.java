package com.inza.twoh.init;

import com.inza.twoh.AddonMain;
import com.inza.twoh.entity.RiftWindow;
import com.inza.twoh.entity.SpatialRiftEntity;

import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class InitEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
            ForgeRegistries.ENTITIES, AddonMain.MOD_ID);

    public static final RegistryObject<EntityType<RiftWindow>> RIFT_WINDOW = ENTITIES.register(
            "rift_window",
            () -> EntityType.Builder.<RiftWindow>of(RiftWindow::new, EntityClassification.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(8)
                    .noSave()
                    .build(AddonMain.MOD_ID + ":rift_window"));

    public static final RegistryObject<EntityType<SpatialRiftEntity>> SPATIAL_RIFT = ENTITIES.register(
            "spatial_rift",
            () -> EntityType.Builder.<SpatialRiftEntity>of(SpatialRiftEntity::new, EntityClassification.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(8)
                    .noSave()
                    .build(AddonMain.MOD_ID + ":spatial_rift"));
};

package com.inza.twoh.init;

import com.inza.twoh.AddonMain;

import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

public class InitDimension {
    public static final ResourceLocation OVER_HEAVEN_ID = new ResourceLocation(AddonMain.MOD_ID, "over_heaven");

    public static final RegistryKey<World> OVER_HEAVEN = RegistryKey.create(
        Registry.DIMENSION_REGISTRY,
        OVER_HEAVEN_ID
    );
}

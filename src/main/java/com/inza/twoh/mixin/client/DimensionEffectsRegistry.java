package com.inza.twoh.mixin.client;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.world.DimensionRenderInfo;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Forge 1.16 has sky handlers but no public registry event for a new dimension effects ID. */
@Mixin(DimensionRenderInfo.class)
public interface DimensionEffectsRegistry {
    @Accessor("EFFECTS")
    static Object2ObjectMap<ResourceLocation, DimensionRenderInfo> twoh$effects() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}

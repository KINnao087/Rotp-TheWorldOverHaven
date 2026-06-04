package com.inza.twoh.client.render;

import com.inza.twoh.AddonMain;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

public final class OverHeavenRenderTypes extends RenderState {
    private static final ResourceLocation OVER_HEAVEN_FLOOR_TEXTURE =
            new ResourceLocation(AddonMain.MOD_ID, "textures/block/over_heaven_floor.png");

    public static final RenderType OVER_HEAVEN_FLOOR = createOverHeavenPortalLayer();

    public static final List<RenderType> OVER_HEAVEN_FLOOR_LAYERS =
            Collections.singletonList(OVER_HEAVEN_FLOOR);

    public static boolean isOverHeavenFloor(RenderType renderType) {
        return OVER_HEAVEN_FLOOR_LAYERS.contains(renderType);
    }

    private static RenderType createOverHeavenPortalLayer() {
        return RenderType.create(
                AddonMain.MOD_ID + ":over_heaven_floor_portal",
                DefaultVertexFormats.BLOCK,
                7,
                2097152,
                false,
                false,
                RenderType.State.builder()
                        .setTextureState(new TextureState(OVER_HEAVEN_FLOOR_TEXTURE, false, false))
                        .setTexturingState(new TexturingState("over_heaven_floor_portal_shader",
                                () -> OverHeavenPortalShader.setup(OVER_HEAVEN_FLOOR_TEXTURE),
                                OverHeavenPortalShader::clear))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(NO_LIGHTMAP)
                        .createCompositeState(true));
    }

    private OverHeavenRenderTypes() {
        super("over_heaven_render_types", () -> {}, () -> {});
    }
}

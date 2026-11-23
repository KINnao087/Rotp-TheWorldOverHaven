package com.inza.twoh.portalcore.client;

import com.inza.twoh.AddonMain;
import com.inza.twoh.portalcore.immersive_portals.render.context_management.WorldRenderInfo;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraftforge.fml.ModList;

/** State belonging to the parent draw, not to the player entity or the exit block. */
public final class RiftViewScope {
    private static final Field PIPELINE = locatePipeline();
    private static ActiveRenderInfo parentCamera;

    private RiftViewScope() {}

    public static void render(Runnable draw, boolean translationOnly) {
        Minecraft mc = Minecraft.getInstance();
        WorldRenderer parent = mc.levelRenderer;
        ActiveRenderInfo previous = parentCamera;
        Object pipeline = read(parent);
        parentCamera = translationOnly ? mc.gameRenderer.getMainCamera() : null;
        try {
            draw.run();
        } finally {
            parentCamera = previous;
            if (pipeline != null) {
                try {
                    PIPELINE.set(parent, pipeline);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot restore parent Oculus render pass", e);
                }
            }
        }
    }

    public static void alignCamera(ActiveRenderInfo camera) {
        if (parentCamera != null) {
            WorldRenderInfo.adjustCameraPos(camera);
            camera.setAnglesInternal(parentCamera.getYRot(), parentCamera.getXRot());
        }
    }

    public static boolean hasParentView() {
        return parentCamera != null;
    }

    private static Object read(WorldRenderer renderer) {
        try {
            return PIPELINE == null ? null : PIPELINE.get(renderer);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read parent Oculus render pass", e);
        }
    }

    private static Field locatePipeline() {
        if (!ModList.get().isLoaded("oculus")) return null;
        for (Field f : WorldRenderer.class.getDeclaredFields()) {
            if (f.getType().getName().equals("net.coderbot.iris.pipeline.WorldRenderingPipeline")
                    && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                return f;
            }
        }
        AddonMain.LOGGER.warn("Oculus renderer has no recognized per-pass pipeline field");
        return null;
    }
}

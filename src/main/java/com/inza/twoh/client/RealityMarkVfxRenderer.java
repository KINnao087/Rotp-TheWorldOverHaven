package com.inza.twoh.client;

import com.github.standobyte.jojo.client.ClientUtil;
import com.inza.twoh.AddonMain;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, value = Dist.CLIENT)
public final class RealityMarkVfxRenderer {
    private static final Map<Integer, Long> ACTIVE_MARKERS = new HashMap<>();
    private static final long MILLIS_PER_TICK = 50L;
    private static final int MARKER_FILL_COLOR = 0xFFFFFF;
    private static final int MARKER_OUTLINE_COLOR = 0x8134B8;

    private RealityMarkVfxRenderer() {}

    public static void show(int entityId, int durationTicks) {
        ACTIVE_MARKERS.put(entityId, System.currentTimeMillis() + durationTicks * MILLIS_PER_TICK);
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        if (ACTIVE_MARKERS.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE_MARKERS.clear();
            return;
        }

        long now = System.currentTimeMillis();
        MatrixStack matrixStack = event.getMatrixStack();
        ActiveRenderInfo camera = mc.gameRenderer.getMainCamera();
        Vector3d cameraPos = camera.getPosition();
        float partialTicks = event.getPartialTicks();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        Iterator<Map.Entry<Integer, Long>> iterator = ACTIVE_MARKERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Long> entry = iterator.next();
            if (entry.getValue() <= now) {
                iterator.remove();
                continue;
            }

            Entity entity = mc.level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity)) {
                iterator.remove();
                continue;
            }

            renderMarker(matrixStack, mc, camera, cameraPos, (LivingEntity) entity, partialTicks);
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderMarker(MatrixStack matrixStack, Minecraft mc, ActiveRenderInfo camera, Vector3d cameraPos, LivingEntity entity, float partialTicks) {
        double x = MathHelper.lerp(partialTicks, entity.xOld, entity.getX()) - cameraPos.x;
        double y = MathHelper.lerp(partialTicks, entity.yOld, entity.getY()) - cameraPos.y + entity.getBbHeight() + 0.45D;
        double z = MathHelper.lerp(partialTicks, entity.zOld, entity.getZ()) - cameraPos.z;

        matrixStack.pushPose();
        matrixStack.translate(x, y, z);
        matrixStack.mulPose(camera.rotation());
        matrixStack.scale(-0.025F, -0.025F, 0.025F);

        RenderSystem.disableDepthTest();
        mc.getTextureManager().bind(ClientUtil.ADDITIONAL_UI);
        setColor(MARKER_FILL_COLOR);
        AbstractGui.blit(matrixStack, -16, -32, 0.0F, 0.0F, 32, 32, 256, 256);
        setColor(MARKER_OUTLINE_COLOR);
        AbstractGui.blit(matrixStack, -16, -32, 32.0F, 0.0F, 32, 32, 256, 256);
        RenderSystem.enableDepthTest();
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);

        matrixStack.popPose();
    }

    private static void setColor(int color) {
        RenderSystem.color4f(
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F,
                1.0F);
    }
}

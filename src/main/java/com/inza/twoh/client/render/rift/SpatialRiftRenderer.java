package com.inza.twoh.client.render.rift;

import com.inza.twoh.AddonMain;
import com.inza.twoh.client.render.fx.EffectRenderer;
import com.inza.twoh.client.render.fx.RiftSceneColor;
import com.inza.twoh.entity.SpatialRiftEntity;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.inza.twoh.portalcore.immersive_portals.render.context_management.PortalRendering;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** EN: Enrol a tracked visible anchor once; draw after the world color is available.
 *  中文：可见锚点只提交一次，在取得此岸场景颜色后由特效层完成绘制。 */
public final class SpatialRiftRenderer extends EntityRenderer<SpatialRiftEntity> {
    private static final ResourceLocation ICON =
            new ResourceLocation(AddonMain.MOD_ID, "textures/action/twoh_spatial_rift.png");
    private final Set<SpatialRiftEntity> submitted = Collections.newSetFromMap(new WeakHashMap<>());

    public SpatialRiftRenderer(EntityRendererManager manager) {
        super(manager);
    }

    @Override
    public void render(SpatialRiftEntity anchor, float yaw, float partial, MatrixStack matrix,
            IRenderTypeBuffer buffers, int light) {
        if (PortalRendering.isRendering()) {
            return;
        }
        // EN: The pen draws only an outline; it never samples a full-screen scene copy.
        // 中文：手绘阶段只画描边，不需要逐帧复制整张屏幕。
        if (!anchor.isDraft()) {
            RiftSceneColor.request(anchor.level);
        }
        if (submitted.add(anchor)) {
            try {
                // EN: IP can keep the anchor alive across travel, but its world-scoped effect is released.
                // 中文：跨维度后锚点可以保留；特效释放时要撤销登记，返回时才能重新绘制。
                SpatialRiftEffect effect = new SpatialRiftEffect(anchor, () -> submitted.remove(anchor));
                if (!EffectRenderer.add((ClientWorld) anchor.level, effect)) {
                    effect.close();
                }
            } catch (RuntimeException ex) {
                submitted.remove(anchor);
                AddonMain.LOGGER.error("Failed to load spatial rift geometry", ex);
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(SpatialRiftEntity anchor) {
        return ICON;
    }
}

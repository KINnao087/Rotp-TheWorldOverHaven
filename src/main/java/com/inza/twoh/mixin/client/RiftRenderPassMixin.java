package com.inza.twoh.mixin.client;

import com.inza.twoh.portalcore.client.RiftViewScope;
import com.inza.twoh.portalcore.immersive_portals.render.PortalRenderer;
import com.inza.twoh.portalcore.immersive_portals.render.context_management.PortalRendering;
import com.inza.twoh.portalcore.immersive_portals.render.context_management.WorldRenderInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.vector.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PortalRenderer.class, remap = false)
public abstract class RiftRenderPassMixin {
    @Redirect(method = "renderPortalContent", at = @At(value = "INVOKE",
            target = "Lcom/inza/twoh/portalcore/immersive_portals/render/context_management/PortalRendering;getRenderingCameraPos()Lnet/minecraft/util/math/vector/Vector3d;"))
    private Vector3d twohaddon$mapActiveView() {
        return PortalRendering.getRenderingPortal().transformPoint(
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
    }

    @Redirect(method = "renderPortalContent", at = @At(value = "INVOKE",
            target = "Lcom/inza/twoh/portalcore/immersive_portals/render/PortalRenderer;invokeWorldRendering(Lcom/inza/twoh/portalcore/immersive_portals/render/context_management/WorldRenderInfo;)V"))
    private void twohaddon$renderNested(PortalRenderer renderer, WorldRenderInfo info) {
        RiftViewScope.render(() -> renderer.invokeWorldRendering(info), info.cameraTransformation == null);
    }
}

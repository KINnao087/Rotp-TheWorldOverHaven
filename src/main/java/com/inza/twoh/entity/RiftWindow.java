package com.inza.twoh.entity;

import com.qouteall.immersive_portals.portal.Portal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

/** EN: A non-saving Immersive Portals surface. The rift anchor owns its lifetime.
 * 中文：由裂隙锚点管理的临时真实门户，不把半开的门写入存档。 */
public final class RiftWindow extends Portal {
    public RiftWindow(EntityType<?> type, World world) {
        super(type, world);
    }
    // EN: Looking through a rift must not render the reverse side of the same opening again.
    // 中文：从裂隙看过去时，不递归渲染同一裂隙的返回面；其他独立的门不受影响。
    @Override
    public boolean cannotRenderInMe(Portal other) {
        return (other instanceof RiftWindow && portalTag != null && portalTag.equals(other.portalTag))
                || super.cannotRenderInMe(other);
    }

    @Override
    public boolean canTeleportEntity(Entity entity) {
        return entity instanceof PlayerEntity && super.canTeleportEntity(entity);
    }
}

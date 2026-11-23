package com.inza.twoh.entity;

import com.inza.twoh.portalcore.immersive_portals.portal.Portal;
import com.inza.twoh.portalcore.immersive_portals.portal.GeometryPortalShape;
import com.inza.twoh.rift.portal.RiftAperture;
import com.inza.twoh.rift.portal.RiftCrossing;
import net.minecraft.util.math.vector.Vector3d;
import javax.annotation.Nullable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

/** EN: A non-saving Immersive Portals surface. The rift anchor owns its lifetime.
 * 中文：由裂隙锚点管理的临时真实门户，不把半开的门写入存档。 */
public final class RiftWindow extends Portal {
    private GeometryPortalShape lastWireShape;
    private RiftAperture aperture;

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

    @Override
    public boolean isPointInPortalProjection(Vector3d pos) {
        Vector3d offset = pos.subtract(position());
        double x = offset.dot(axisW);
        double y = offset.dot(axisH);
        if (specialShape == null) {
            return Math.abs(x) < width * 0.5 + 0.001 && Math.abs(y) < height * 0.5 + 0.001;
        }
        // EN: Replace the cached geometry when a new network mask arrives, not for every ray.
        // 中文：同步到新遮罩时重建本地几何，不为每条射线重复解码。
        if (specialShape != lastWireShape) {
            aperture = new RiftAperture();
            for (GeometryPortalShape.TriangleInPlane triangle : specialShape.triangles) {
                aperture.triangle(triangle.x1, triangle.y1, triangle.x2, triangle.y2, triangle.x3, triangle.y3);
            }
            lastWireShape = specialShape;
        }
        return aperture.contains(x, y);
    }

    @Nullable
    @Override
    public Vector3d rayTrace(Vector3d from, Vector3d to) {
        Vector3d hit = RiftCrossing.planeHit(from, to, position(), getNormal());
        return hit != null && isPointInPortalProjection(hit) ? hit : null;
    }
}

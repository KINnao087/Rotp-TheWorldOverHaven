package com.inza.twoh.rift;

import com.inza.twoh.entity.RiftWindow;
import com.inza.twoh.entity.SpatialRiftEntity;
import com.inza.twoh.init.InitEntities;
import com.qouteall.immersive_portals.api.PortalAPI;
import com.qouteall.immersive_portals.portal.GeometryPortalShape;
import com.qouteall.immersive_portals.portal.GeometryPortalShape.TriangleInPlane;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;

import java.util.BitSet;

/** EN: Two faces at one endpoint. The same triangle mask drives remote rendering and crossing.
 * 中文：每端各有正反两面；彼端世界渲染和穿越检测使用完全相同的三角形遮罩。 */
public final class RiftWindows {
    // EN: IP 0.17 rejects the entire mask if any triangle has area <= 0.001 square blocks.
    // 中文：在 IP 0.17 中，只要有一个三角形面积不超过 0.001 平方格，整张遮罩就会失效。
    private static final double MIN_TRIANGLE_AREA = 0.001D;

    private final SpatialRiftEntity anchor;
    private final ServerWorld destination;
    private final String pairTag;
    private Vector3d exit;
    private final RiftWindow[] windows = new RiftWindow[2];
    private final BitSet lifted = new BitSet();
    private RiftMotion motion;
    private boolean travel;
    private float slit = -1;

    public RiftWindows(SpatialRiftEntity anchor, ServerWorld destination, Vector3d exit) {
        this.anchor = anchor;
        this.destination = destination;
        this.exit = exit;
        this.pairTag = anchor.isDraft() ? anchor.getStringUUID() : Long.toHexString(anchor.layoutSeed());
    }

    public void setExit(Vector3d exit) {
        this.exit = exit;
    }

    public void preview(RiftStroke stroke) {
        update(RiftMesh.cut(stroke), null, 1, false);
    }

    public void animate(RiftMesh mesh, float age, int closeAt) {
        if (motion == null) {
            motion = new RiftMotion(mesh.plates.length);
        }
        boolean pass = closeAt < 0 && age >= 2.2F;
        if (pass && travel) {
            return;
        }
        motion.sample(mesh, age, closeAt * .05F, RiftMotion.phaseSalt(anchor.layoutSeed()));
        boolean dirty = pass != travel || motion.slit != slit;
        for (int i = 1; i < motion.poses.length; i++) {
            if (lifted.get(i) != motion.poses[i].lifted) {
                lifted.set(i, motion.poses[i].lifted);
                dirty = true;
            }
        }
        if (dirty) {
            update(mesh.slots, mesh.faces, motion.slit, pass);
        }
    }

    private void update(RiftMesh.Slot[] slots, RiftMesh.Face[] plates, float gap, boolean pass) {
        slit = gap;
        travel = pass;
        Mask mask = new Mask();
        for (RiftMesh.Slot slot : slots) {
            float low = (1 - gap) * .5F;
            float a = (low - slot.first) / (slot.last - slot.first);
            float b = (1 - low - slot.first) / (slot.last - slot.first);
            float lo = RiftMotion.clamp(Math.min(a, b));
            float hi = RiftMotion.clamp(Math.max(a, b));
            if (hi <= lo) {
                continue;
            }
            if (slot.horizontal) {
                mask.quad(slot.x + (slot.u - slot.x) * lo, slot.y,
                        slot.x + (slot.u - slot.x) * hi, slot.v);
            } else {
                mask.quad(slot.x, slot.y + (slot.v - slot.y) * lo,
                        slot.u, slot.y + (slot.v - slot.y) * hi);
            }
        }
        if (plates != null) {
            for (RiftMesh.Face face : plates) {
                if (lifted.get(face.id)) {
                    mask.quad(face.x, face.y, face.u, face.v);
                }
            }
        }
        if (mask.front.triangles.isEmpty()) {
            close();
            return;
        }
        for (int side = 0; side < windows.length; side++) {
            windows[side] = sync(windows[side], mask, side, pass);
        }
    }

    private RiftWindow sync(RiftWindow window, Mask mask, int side, boolean pass) {
        boolean fresh = window == null || !window.isAlive();
        if (fresh) {
            window = new RiftWindow(InitEntities.RIFT_WINDOW.get(), anchor.level);
            window.setDestinationDimension(destination.dimension());
            window.setInteractable(false);
            // EN: Equal transforms may share a world draw; each facet keeps its own collision mask.
            // 中文：相同变换的面片可共用一次彼端绘制；穿越仍使用各自的碰撞遮罩。
            window.renderingMergable = true;
            window.portalTag = pairTag;
            window.teleportChangesScale = false;
        }
        RiftPlane plane = anchor.stroke().plane;
        Vector3d origin = anchor.position().add(plane.center);
        Vector3d remote = exit.add(plane.center);
        Vector3d right = side == 0 ? plane.horizontal : plane.horizontal.scale(-1);
        GeometryPortalShape shape = side == 0 ? mask.front : mask.back;
        double width = 2 * mask.boundX + .02;
        double height = 2 * mask.boundY + .02;
        // EN: Sync only a changed mask, endpoint or travel state.
        // 中文：仅在遮罩、端点位置或通行状态变化时同步。
        if (!fresh && window.teleportable == pass && window.hasCrossPortalCollision == pass
                && window.position().equals(origin) && remote.equals(window.getDestPos())
                && right.equals(window.axisW) && plane.vertical.equals(window.axisH)
                && window.width == width && window.height == height && sameShape(window.specialShape, shape)) {
            return window;
        }
        window.setOriginPos(origin);
        window.setDestination(remote);
        window.setOrientationAndSize(right, plane.vertical, width, height);
        window.initCullableRange(0, 0, 0, 0);
        window.specialShape = shape;
        window.teleportable = pass;
        window.hasCrossPortalCollision = pass;
        if (fresh) {
            PortalAPI.spawnServerEntity(window);
        } else {
            window.reloadAndSyncToClient();
        }
        return window;
    }

    private static boolean sameShape(GeometryPortalShape old, GeometryPortalShape next) {
        if (old == null || old.triangles.size() != next.triangles.size()) {
            return false;
        }
        for (int i = 0; i < old.triangles.size(); i++) {
            TriangleInPlane a = old.triangles.get(i);
            TriangleInPlane b = next.triangles.get(i);
            if (a.x1 != b.x1 || a.y1 != b.y1 || a.x2 != b.x2 || a.y2 != b.y2
                    || a.x3 != b.x3 || a.y3 != b.y3) {
                return false;
            }
        }
        return true;
    }

    // EN: IP expects a 2D mask in each portal's own plane. Mirroring X produces its reverse face.
    // 中文：直接使用绘制平面的局部坐标；镜像横坐标得到反面的遮罩。
    private static final class Mask {
        final GeometryPortalShape front = new GeometryPortalShape();
        final GeometryPortalShape back = new GeometryPortalShape();
        double boundX, boundY;

        void quad(double x, double y, double u, double v) {
            triangle(x, y, u, y, u, v);
            triangle(u, v, x, v, x, y);
        }

        void triangle(double x, double y, double u, double v, double s, double t) {
            TriangleInPlane tri = new TriangleInPlane(x, y, u, v, s, t);
            if (tri.getArea() <= MIN_TRIANGLE_AREA) {
                return;
            }
            front.triangles.add(tri);
            back.triangles.add(new TriangleInPlane(-x, y, -u, v, -s, t));
            boundX = Math.max(boundX, Math.max(Math.abs(x), Math.max(Math.abs(u), Math.abs(s))));
            boundY = Math.max(boundY, Math.max(Math.abs(y), Math.max(Math.abs(v), Math.abs(t))));
        }
    }

    public void close() {
        for (int side = 0; side < windows.length; side++) {
            if (windows[side] != null) {
                windows[side].remove();
                windows[side] = null;
            }
        }
    }
}

package com.inza.twoh.rift;

import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.vector.Vector3d;

import java.util.Arrays;

/** EN: A pen on a world-fixed plane. Integer steps are the only source of aperture geometry.
 * 中文：在起笔时固定的平面上落笔，裂口只由实际画下的整数格步生成。 */
public final class RiftStroke {
    public static final float STEP = .4F;
    public static final int MAX_STEPS = 128;
    private static final int HORIZONTAL_LIMIT = 30;
    private static final int VERTICAL_LIMIT = 10;
    private final int[] points = new int[2 * (MAX_STEPS + 1)];
    private int size = 1;
    public final Vector3d origin;
    public final float yaw;
    public final RiftPlane plane;

    public RiftStroke(Vector3d origin, float yaw) {
        this.origin = origin;
        this.yaw = yaw;
        plane = new RiftPlane(yaw);
    }

    public static RiftStroke begin(LivingEntity user) {
        Vector3d eye = user.getEyePosition(1);
        Vector3d look = user.getLookAngle();
        Vector3d end = eye.add(look.scale(RiftPlane.PLACEMENT_REACH));
        // EN: On a miss, Minecraft returns the ray endpoint. Both paths use the same inset.
        // 中文：未碰到方块时，MC 返回射线终点；命中和未命中都沿视线回退相同距离。
        RayTraceContext ray = new RayTraceContext(eye, end, RayTraceContext.BlockMode.COLLIDER,
                RayTraceContext.FluidMode.NONE, user);
        Vector3d hit = user.level.clip(ray).getLocation();
        // EN: Aim places the first point; torso yaw fixes the plane for the entire stroke.
        // 中文：准星决定起笔点，起笔时的躯干朝向决定整笔的画布朝向。
        return new RiftStroke(hit.subtract(look.scale(RiftPlane.INSET)), user.yBodyRot);
    }

    public int size() {
        return size;
    }
    public int x(int i) {
        return points[2 * i];
    }
    public int y(int i) {
        return points[2 * i + 1];
    }
    public Vector3d midpointOffset() {
        int middle = size / 2;
        return plane.point(x(middle) * STEP, y(middle) * STEP);
    }

    public boolean sample(LivingEntity user) {
        return sample(user.getEyePosition(1), user.getLookAngle());
    }

    public boolean sample(Vector3d eye, Vector3d look) {
        return plane.trace(this, eye, look);
    }

    // EN: Cross one grid boundary at a time; the dead zone prevents jitter near a corner.
    // 中文：逐个跨越格边；滞回区防止准星在拐角附近抖出短刺。
    public boolean stepTo(double px, double py) {
        int start = size;
        px = Math.max(-HORIZONTAL_LIMIT, Math.min(HORIZONTAL_LIMIT, px));
        py = Math.max(-VERTICAL_LIMIT, Math.min(VERTICAL_LIMIT, py));
        int a = x(size - 1), b = y(size - 1);
        double dx = px - a, dy = py - b;
        int sx = (int) Math.signum(dx), sy = (int) Math.signum(dy);
        double tx = .69 / Math.abs(dx), ty = .69 / Math.abs(dy);
        while (Math.min(tx, ty) <= 1 && size <= MAX_STEPS) {
            if (tx <= ty) {
                a += sx;
                tx += 1 / Math.abs(dx);
            } else {
                b += sy;
                ty += 1 / Math.abs(dy);
            }
            points[2 * size] = a;
            points[2 * size++ + 1] = b;
        }
        return size != start;
    }

    public CompoundNBT pack() {
        CompoundNBT tag = new CompoundNBT();
        tag.putIntArray("steps", Arrays.copyOf(points, size * 2));
        return tag;
    }

    public static RiftStroke unpack(Vector3d origin, float yaw, CompoundNBT tag) {
        RiftStroke pen = new RiftStroke(origin, yaw);
        int[] trace = tag.getIntArray("steps");
        if (trace.length >= 2 && trace.length <= pen.points.length && trace.length % 2 == 0) {
            System.arraycopy(trace, 0, pen.points, 0, trace.length);
            pen.size = trace.length / 2;
        }
        return pen;
    }

}

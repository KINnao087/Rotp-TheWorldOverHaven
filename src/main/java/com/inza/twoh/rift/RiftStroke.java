package com.inza.twoh.rift;

import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.vector.Vector3d;

import java.util.Arrays;

/** EN: A pen on a world-fixed plane. Integer steps are the only source of aperture geometry.
 * 中文：在起笔时固定的平面上落笔，裂口只由实际画下的整数格步生成。 */
public final class RiftStroke {
    public static final float STEP = .4F;
    public static final int MAX_STEPS = 64;
    private final int[] points = new int[2 * (MAX_STEPS + 1)];
    private int size = 1;
    public final Vector3d origin;
    public final float yaw;
    public final float pitch;
    public final RiftPlane plane;

    public RiftStroke(Vector3d origin, float yaw) {
        this(origin, yaw, 0);
    }

    public RiftStroke(Vector3d origin, float yaw, float pitch) {
        this.origin = origin;
        this.yaw = yaw;
        this.pitch = pitch;
        plane = new RiftPlane(yaw, pitch);
    }

    public static RiftStroke begin(LivingEntity user) {
        return new RiftStroke(user.getEyePosition(1), user.yRot, user.xRot);
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
        tag.putFloat("pitch", pitch);
        tag.putIntArray("steps", Arrays.copyOf(points, size * 2));
        return tag;
    }

    public static RiftStroke unpack(Vector3d origin, float yaw, CompoundNBT tag) {
        RiftStroke pen = new RiftStroke(origin, yaw, tag.getFloat("pitch"));
        int[] trace = tag.getIntArray("steps");
        if (trace.length >= 2 && trace.length <= pen.points.length && trace.length % 2 == 0) {
            System.arraycopy(trace, 0, pen.points, 0, trace.length);
            pen.size = trace.length / 2;
        }
        return pen;
    }

}

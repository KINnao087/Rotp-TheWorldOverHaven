package com.inza.twoh.rift;

import net.minecraft.util.math.vector.Vector3d;

/**
 * EN: Immutable drawing frame, relative to the caster's eye position at the start of the gesture.
 * 中文：以起笔时的眼睛位置为原点；绘制平面的距离和朝向在本次施放中不再改变。
 */
public final class RiftPlane {
    public static final double DISTANCE = 4.0;
    private static final double PARALLEL_EPSILON = 1.0E-6;

    public final Vector3d center;
    public final Vector3d horizontal;
    public final Vector3d vertical;
    public final Vector3d inward;

    public RiftPlane(float yaw, float pitch) {
        double a = Math.toRadians(yaw);
        double b = Math.toRadians(pitch);
        Vector3d forward = new Vector3d(-Math.sin(a) * Math.cos(b), -Math.sin(b), Math.cos(a) * Math.cos(b));
        center = forward.scale(DISTANCE);
        inward = forward.scale(-1);
        horizontal = new Vector3d(-Math.cos(a), 0, -Math.sin(a));
        vertical = horizontal.cross(forward);
    }

    public Vector3d point(double x, double y) {
        return center.add(horizontal.scale(x)).add(vertical.scale(y));
    }

    boolean trace(RiftStroke stroke, Vector3d eye, Vector3d look) {
        Vector3d offset = eye.subtract(stroke.origin).subtract(center);
        double denominator = look.dot(inward);
        if (Math.abs(denominator) < PARALLEL_EPSILON) {
            return false;
        }
        double distance = -offset.dot(inward) / denominator;
        if (distance <= 0) {
            return false;
        }
        Vector3d hit = offset.add(look.scale(distance));
        double x = hit.dot(horizontal) / RiftStroke.STEP;
        double y = hit.dot(vertical) / RiftStroke.STEP;
        // EN: Ignore unreachable grazing hits instead of spending the whole stroke on a distant point.
        // 中文：掠过平面时若交点超出可画范围，则忽略该采样，不让它一次耗尽笔迹。
        if (Math.abs(x) + Math.abs(y) > RiftStroke.MAX_STEPS + 1) {
            return false;
        }
        return stroke.stepTo(x, y);
    }
}

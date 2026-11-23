package com.inza.twoh.rift;

import net.minecraft.util.math.vector.Vector3d;

/**
 * EN: A world-fixed vertical drawing frame whose origin is the initial ray hit, slightly inset toward the player.
 * 中文：以首次准星命中点朝玩家退回少许的位置为原点，画板固定在世界里，始终竖直。
 */
public final class RiftPlane {
    static final double PLACEMENT_REACH = 4.0;
    static final double INSET = .12;
    private static final double MIN_FORWARD_DOT = 1.0E-6;
    private static final double MAX_DRAW_DISTANCE = 20.0;

    public final Vector3d horizontal;
    public final Vector3d vertical;
    public final Vector3d inward;

    public RiftPlane(float yaw) {
        double a = Math.toRadians(yaw);
        inward = new Vector3d(Math.sin(a), 0, -Math.cos(a));
        horizontal = new Vector3d(-Math.cos(a), 0, -Math.sin(a));
        vertical = new Vector3d(0, 1, 0);
    }

    public Vector3d point(double x, double y) {
        return horizontal.scale(x).add(0, y, 0);
    }

    boolean trace(RiftStroke stroke, Vector3d eye, Vector3d look) {
        Vector3d offset = eye.subtract(stroke.origin);
        double forwardDot = -look.dot(inward);
        if (forwardDot <= MIN_FORWARD_DOT) {
            return false;
        }
        double distance = offset.dot(inward) / forwardDot;
        if (distance < INSET || distance > MAX_DRAW_DISTANCE) {
            return false;
        }
        Vector3d hit = offset.add(look.scale(distance));
        double x = hit.dot(horizontal) / RiftStroke.STEP;
        double y = hit.y / RiftStroke.STEP;
        return stroke.stepTo(x, y);
    }
}

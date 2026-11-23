package com.inza.twoh.rift;

import net.minecraft.util.math.vector.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * EN: Width of the aperture exposed by the flying shards. The held pen never uses this profile.
 * 中文：仅计算碎片炸开后露出的开口宽度，手绘细线不使用这个轮廓。
 */
final class RiftAperture {
    private static final float HALF_WIDTH_RATIO = .24F;
    private static final float TIP_RADIUS = .025F;
    private static final float EDGE_VARIATION = .06F;
    private static final double SOFT_LENGTH = 4 * Math.PI * RiftPlane.PLACEMENT_REACH;
    private static final float BLOCK_CELL = RiftStroke.STEP / 2;

    private final float[] distance;
    private final float peak;
    private final boolean closed;
    private final List<Run> runs = new ArrayList<>();

    RiftAperture(RiftStroke stroke) {
        int last = stroke.size() - 1;
        distance = new float[last + 1];
        Vector3d previous = stroke.plane.point(stroke.x(0) * RiftStroke.STEP, stroke.y(0) * RiftStroke.STEP);
        for (int i = 1; i <= last; i++) {
            Vector3d point = stroke.plane.point(stroke.x(i) * RiftStroke.STEP, stroke.y(i) * RiftStroke.STEP);
            distance[i] = distance[i - 1] + (float) point.distanceTo(previous);
            previous = point;
        }
        int start = 0;
        for (int i = 1; i <= last; i++) {
            if (i == last || stroke.x(i) - stroke.x(i - 1) != stroke.x(i + 1) - stroke.x(i)
                    || stroke.y(i) - stroke.y(i - 1) != stroke.y(i + 1) - stroke.y(i)) {
                runs.add(new Run(stroke, start, i));
                start = i;
            }
        }
        float length = distance[last];
        // EN: Width keeps growing even for a long sweep, but not fast enough to overwhelm the stroke.
        // 中文：长笔迹仍会继续变宽，但增长逐渐放缓，避免张口覆盖整条笔迹。
        peak = (float) (HALF_WIDTH_RATIO * length / (1 + length / SOFT_LENGTH));
        closed = last >= 4 && stroke.x(0) == stroke.x(last) && stroke.y(0) == stroke.y(last);
    }

    float cellSize() {
        // EN: A one-step stroke still needs room for shards on both sides of the original thin line.
        // 中文：最短笔迹也要在原有细线两侧留出可剥离的碎片。
        return peak < BLOCK_CELL ? BLOCK_CELL / 2 : BLOCK_CELL;
    }

    float maxRadius() {
        return TIP_RADIUS + peak * (1 + EDGE_VARIATION);
    }

    /**
     * EN: Union the tapered widths of every visit. A thin tip must not cut a notch into a later pass.
     * 中文：叠加每次经过该处的开口范围，先画的尖端不能把后来经过的宽口截断。
     */
    boolean covers(float x, float y, float phase) {
        float bound = maxRadius();
        for (Run run : runs) {
            float px = x - run.x;
            float py = y - run.y;
            float projection = (px * run.dx + py * run.dy) * run.inverseLengthSquared;
            float fraction = Math.max(0, Math.min(1, projection));
            float nx = px - fraction * run.dx;
            float ny = py - fraction * run.dy;
            float squared = nx * nx + ny * ny;
            if (squared <= bound * bound) {
                float radius = radius(run.first + fraction * run.span, phase);
                if (squared <= radius * radius) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class Run {
        final float x, y, dx, dy, inverseLengthSquared, first, span;

        Run(RiftStroke pen, int start, int end) {
            x = pen.x(start) * RiftStroke.STEP;
            y = pen.y(start) * RiftStroke.STEP;
            dx = pen.x(end) * RiftStroke.STEP - x;
            dy = pen.y(end) * RiftStroke.STEP - y;
            inverseLengthSquared = 1 / (dx * dx + dy * dy);
            first = start / (float) (pen.size() - 1);
            span = (end - start) / (float) (pen.size() - 1);
        }
    }

    private float radius(float stepProgress, float phase) {
        int last = distance.length - 1;
        float position = stepProgress * last;
        int index = Math.min((int) position, last - 1);
        float arc = distance[index] + (position - index) * (distance[index + 1] - distance[index]);
        float t = distance[last] > 0 ? arc / distance[last] : stepProgress;
        double belly = closed ? 1 : Math.sin(Math.PI * t);
        double variation = 1 + EDGE_VARIATION * Math.sin(phase + 2 * Math.PI * t);
        return (float) (TIP_RADIUS + peak * belly * variation);
    }
}

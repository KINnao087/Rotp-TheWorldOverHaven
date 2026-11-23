package com.inza.twoh.rift.portal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * EN: Endpoint-local shape, independent of a renderer or transport. Adapted from IP GeometryPortalShape.
 * 中文：端点局部坐标中的开口，不依赖渲染器或传送实现。改编自 IP GeometryPortalShape。
 * SPDX-License-Identifier: Apache-2.0
 * See META-INF/twoh-third-party/immersive-portals.txt.
 */
public final class RiftAperture {
    private static final double MIN_TRIANGLE_AREA = 0.001;
    private final List<Triangle> faces = new ArrayList<>();
    private final List<Triangle> readOnlyFaces = Collections.unmodifiableList(faces);
    private double extentX;
    private double extentY;

    public void quad(double x, double y, double u, double v) {
        triangle(x, y, u, y, u, v);
        triangle(u, v, x, v, x, y);
    }

    public void triangle(double x, double y, double u, double v, double s, double t) {
        if (Math.abs((u - x) * (t - y) - (v - y) * (s - x)) * 0.5 <= MIN_TRIANGLE_AREA) {
            return;
        }
        faces.add(new Triangle(x, y, u, v, s, t));
        extentX = Math.max(extentX, Math.max(Math.abs(x), Math.max(Math.abs(u), Math.abs(s))));
        extentY = Math.max(extentY, Math.max(Math.abs(y), Math.max(Math.abs(v), Math.abs(t))));
    }

    public boolean contains(double x, double y) {
        if (Math.abs(x) > extentX || Math.abs(y) > extentY) {
            return false;
        }
        for (Triangle face : faces) {
            if (face.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    public List<Triangle> triangles() {
        return readOnlyFaces;
    }

    public double width() {
        return extentX * 2 + 0.02;
    }

    public double height() {
        return extentY * 2 + 0.02;
    }

    public static final class Triangle {
        public final double ax, ay, bx, by, cx, cy;

        private Triangle(double ax, double ay, double bx, double by, double cx, double cy) {
            this.ax = ax;
            this.ay = ay;
            // EN: Keep one winding in local space, even when the stroke was drawn backwards.
            // 中文：即使玩家反向画线，也保持局部空间内的三角形绕序一致。
            boolean swap = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax) < 0;
            this.bx = swap ? cx : bx;
            this.by = swap ? cy : by;
            this.cx = swap ? bx : cx;
            this.cy = swap ? by : cy;
        }

        private boolean contains(double x, double y) {
            return left(ax, ay, bx, by, x, y) && left(bx, by, cx, cy, x, y)
                    && left(cx, cy, ax, ay, x, y);
        }

        private static boolean left(double x, double y, double u, double v, double px, double py) {
            return (u - x) * (py - y) >= (v - y) * (px - x);
        }
    }
}

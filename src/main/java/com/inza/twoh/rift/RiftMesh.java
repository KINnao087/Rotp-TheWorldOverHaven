package com.inza.twoh.rift;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

/** EN: The same deterministic cut mesh serves rendering and server collision.
 * 中文：客户端渲染和服务端碰撞共用同一个确定性的切割算法。 */
public final class RiftMesh {
    private static final int TICKS = 20;
    private static final float UNIT = RiftStroke.STEP / TICKS;
    private static final int[] DX = {-1, 1, 0, 0}, DY = {0, 0, -1, 1};
    public final Plate[] plates;
    public final Face[] faces;
    public final Edge[] edges;
    public final Slot[] slots;

    public boolean contains(double x, double y) {
        for (Rect rect : faces) {
            if (rect.contains(x, y)) {
                return true;
            }
        }
        for (Rect rect : slots) {
            if (rect.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    public static class Rect {
        public final float x, y, u, v;
        Rect(float x, float y, float u, float v) {
            this.x = x;
            this.y = y;
            this.u = u;
            this.v = v;
        }
        public boolean contains(double px, double py) {
            return px >= x && px <= u && py >= y && py <= v;
        }
    }
    public static final class Face extends Rect {
        public final int id;
        Face(int id, float x, float y, float u, float v) {
            super(x, y, u, v);
            this.id = id;
        }
    }
    public static final class Edge extends Rect {
        public final int a, b;
        public final float first, last;
        Edge(int a, int b, float x, float y, float u, float v, float first, float last) {
            super(x, y, u, v);
            this.a = a;
            this.b = b;
            this.first = first;
            this.last = last;
        }
    }
    public static final class Slot extends Rect {
        public final boolean horizontal;
        public final float first, last;
        Slot(float x, float y, float u, float v, boolean horizontal, float first, float last) {
            super(x, y, u, v);
            this.horizontal = horizontal;
            this.first = first;
            this.last = last;
        }
    }
    public static final class Plate {
        public final float x, y, release, landingX, landingY, key;
        public final int band;
        Plate(float x, float y, float release, float lx, float ly, float key, int band) {
            this.x = x;
            this.y = y;
            this.release = release;
            landingX = lx;
            landingY = ly;
            this.key = key;
            this.band = band;
        }
    }
    public static Slot[] cut(RiftStroke pen) {
        List<Slot> result = new ArrayList<>();
        int n = pen.size() - 1;
        for (int i = 0; i < n; i++) {
            float x = pen.x(i) * RiftStroke.STEP, y = pen.y(i) * RiftStroke.STEP;
            float u = pen.x(i + 1) * RiftStroke.STEP, v = pen.y(i + 1) * RiftStroke.STEP;
            boolean forward = x < u || y < v;
            result.add(new Slot(Math.min(x, u) - UNIT, Math.min(y, v) - UNIT, Math.max(x, u) + UNIT,
                    Math.max(y, v) + UNIT, y == v, (forward ? i : i + 1) / (float) n,
                    (forward ? i + 1 : i) / (float) n));
        }
        return result.toArray(new Slot[0]);
    }

    private static final class Near {
        float d = Float.MAX_VALUE, t, nx, ny;
    }
    private static Near nearest(RiftStroke pen, float x, float y) {
        Near near = new Near();
        int n = pen.size() - 1;
        for (int i = 0; i < n; i++) {
            float ax = pen.x(i) * RiftStroke.STEP, ay = pen.y(i) * RiftStroke.STEP;
            float dx = (pen.x(i + 1) - pen.x(i)) * RiftStroke.STEP,
                  dy = (pen.y(i + 1) - pen.y(i)) * RiftStroke.STEP;
            float f = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
            float nx = x - ax - f * dx, ny = y - ay - f * dy, d = nx * nx + ny * ny;
            if (d < near.d) {
                near.d = d;
                near.t = (i + f) / n;
                near.nx = nx;
                near.ny = ny;
            }
        }
        near.d = (float) Math.sqrt(near.d);
        return near;
    }

    private static float[] axis(RiftStroke pen, RiftAperture aperture, boolean horizontal) {
        TreeSet<Integer> ticks = new TreeSet<>();
        for (int i = 0; i < pen.size(); i++) {
            int c = TICKS * (horizontal ? pen.x(i) : pen.y(i));
            ticks.add(c - 1);
            ticks.add(c + 1);
        }
        int stride = Math.round(aperture.cellSize() / UNIT);
        int padding = (int) Math.ceil(aperture.maxRadius() / UNIT) + stride;
        int lo = Math.floorDiv(ticks.first() - padding, stride) * stride;
        int hi = ticks.last() + padding + stride;
        for (int c = lo; c <= hi; c += stride) {
            ticks.add(c);
        }
        float[] values = new float[ticks.size()];
        int i = 0;
        for (int c : ticks) {
            values[i++] = c * UNIT;
        }
        return values;
    }

    private static final class Site {
        final float x, y;
        Site(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
    private static final class Mass {
        float x, y, area, depth;
    }

    public RiftMesh(RiftStroke pen, long seed) {
        if (pen.size() < 2) {
            throw new IllegalArgumentException("A rift needs at least one drawn edge");
        }
        slots = cut(pen);
        RiftAperture aperture = new RiftAperture(pen);
        float[] xs = axis(pen, aperture, true);
        float[] ys = axis(pen, aperture, false);
        int cells = (xs.length - 1) * (ys.length - 1);
        int[] groups = new int[cells], labels = new int[cells];
        Random rng = new Random(seed);
        labelCells(pen, aperture, xs, ys, groups, labels, rng);
        List<Mass> masses = groupMasses(xs, ys, groups, labels);
        List<Face> fs = new ArrayList<>();
        List<Edge> es = new ArrayList<>();
        collectGeometry(pen, xs, ys, labels, fs, es);
        plates = buildPlates(pen, masses, es, rng);
        faces = fs.toArray(new Face[0]);
        edges = es.toArray(new Edge[0]);
    }

    private void labelCells(RiftStroke pen, RiftAperture aperture, float[] xs, float[] ys,
                            int[] groups, int[] labels, Random rng) {
        int w = xs.length - 1, h = ys.length - 1;
        float phase = rng.nextFloat() * 6.28F;
        List<Site> sites = new ArrayList<>();
        for (float x = xs[0] - .5F; x < xs[w] + .9F; x += .75F) {
            for (float y = ys[0] - .5F; y < ys[h] + .9F; y += .75F) {
                sites.add(new Site(x + rng.nextFloat() * .48F, y + rng.nextFloat() * .48F));
            }
        }
        float cell = aperture.cellSize();
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int k = j * w + i;
                float x = (xs[i] + xs[i + 1]) * .5F, y = (ys[j] + ys[j + 1]) * .5F;
                // EN: The coarse mask and ownership are piecewise constant on Minecraft-style square cells.
                // 中文：开口和归属按方格取值，不产生斜切边；主裂纹的细边另行精确切分。
                float cx = ((float) Math.floor(x / cell) + .5F) * cell;
                float cy = ((float) Math.floor(y / cell) + .5F) * cell;
                Near n = nearest(pen, cx, cy);
                float radius = aperture.radius(n.t, phase);
                boolean lip = false;
                for (Slot s : slots) {
                    if (s.contains(x, y)) {
                        lip = true;
                        break;
                    }
                }
                if (lip) {
                    labels[k] = -1;
                    continue;
                }
                if (n.d > radius) {
                    continue;
                }
                float best = Float.MAX_VALUE;
                for (int b = 0; b < sites.size(); b++) {
                    Site s = sites.get(b);
                    float dx = cx - s.x, dy = cy - s.y;
                    float d = dx * dx + dy * dy;
                    if (d < best) {
                        best = d;
                        groups[k] = b + 1;
                    }
                }
            }
        }
    }

    private static List<Mass> groupMasses(float[] xs, float[] ys, int[] groups, int[] labels) {
        int w = xs.length - 1, h = ys.length - 1;
        int[] queue = new int[groups.length];
        List<Mass> masses = new ArrayList<>();
        masses.add(new Mass());
        for (int k = 0; k < groups.length; k++) {
            if (groups[k] == 0 || labels[k] != 0) {
                continue;
            }
            int id = masses.size(), head = 0, tail = 0;
            Mass mass = new Mass();
            masses.add(mass);
            labels[k] = id;
            queue[tail++] = k;
            while (head < tail) {
                int c = queue[head++], i = c % w, j = c / w;
                float area = (xs[i + 1] - xs[i]) * (ys[j + 1] - ys[j]);
                mass.area += area;
                mass.x += (xs[i] + xs[i + 1]) * .5F * area;
                mass.y += (ys[j] + ys[j + 1]) * .5F * area;
                for (int d = 0; d < 4; d++) {
                    int a = i + DX[d], b = j + DY[d], v = b * w + a;
                    if (a < 0 || a >= w || b < 0 || b >= h || groups[v] != groups[k] || labels[v] != 0) {
                        continue;
                    }
                    labels[v] = id;
                    queue[tail++] = v;
                }
            }
            mass.x /= mass.area;
            mass.y /= mass.area;
        }
        return masses;
    }

    private static void collectGeometry(RiftStroke pen, float[] xs, float[] ys, int[] labels,
                                        List<Face> fs, List<Edge> es) {
        int w = xs.length - 1, h = ys.length - 1;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int id = labels[j * w + i];
                if (id <= 0) {
                    continue;
                }
                float x = xs[i], y = ys[j], u = xs[i + 1], v = ys[j + 1];
                // EN: Merge scanline faces; the shared edge of two cells on one shard is never drawn.
                // 中文：同行同片合并，只描外轮廓，不把内部格边画成碎片上的裂纹。
                if (i == 0 || labels[j * w + i - 1] != id) {
                    int end = i + 1;
                    while (end < w && labels[j * w + end] == id) {
                        end++;
                    }
                    fs.add(new Face(id, x, y, xs[end], v));
                }
                for (int d = 0; d < 4; d++) {
                    int a = i + DX[d], b = j + DY[d];
                    int other = a < 0 || a >= w || b < 0 || b >= h ? 0 : labels[b * w + a];
                    if (other == id || (other > 0 && other < id)) {
                        continue;
                    }
                    float ax = d == 1 ? u : x, ay = d == 3 ? v : y, bx = d == 0 ? x : u, by = d == 2 ? y : v;
                    Near start = nearest(pen, ax, ay), end = nearest(pen, bx, by);
                    float first = other < 0 ? start.t : .35F + start.t * 1.2F + start.d * 3;
                    float last = other < 0 ? end.t : .35F + end.t * 1.2F + end.d * 3;
                    es.add(new Edge(id, other, ax, ay, bx, by, first, last));
                }
            }
        }
    }

    private static Plate[] buildPlates(RiftStroke pen, List<Mass> masses, List<Edge> es, Random rng) {
        Plate[] plates = new Plate[masses.size()];
        plates[0] = new Plate(0, 0, 0, 0, 0, 0, 0);
        float deepest = .01F;
        for (int i = 1; i < masses.size(); i++) {
            Mass m = masses.get(i);
            float depth = Float.MAX_VALUE;
            for (Edge e : es) {
                if (e.b == 0) {
                    float dx = m.x - Math.max(e.x, Math.min(e.u, m.x)),
                          dy = m.y - Math.max(e.y, Math.min(e.v, m.y));
                    depth = Math.min(depth, dx * dx + dy * dy);
                }
            }
            m.depth = (float) Math.sqrt(depth);
            deepest = Math.max(deepest, m.depth);
        }
        for (int i = 1; i < plates.length; i++) {
            Mass m = masses.get(i);
            Near n = nearest(pen, m.x, m.y);
            double angle = n.d > .02 ? Math.atan2(n.ny, n.nx) : rng.nextDouble() * 2 * Math.PI;
            float kick = 1.12F + .65F * rng.nextFloat();
            plates[i] = new Plate(m.x, m.y, 1.12F + .42F * n.t + .17F * rng.nextFloat(),
                    m.x + (float) Math.cos(angle) * kick, m.y + (float) Math.sin(angle) * kick,
                    rng.nextFloat() * 100, Math.min(RiftMotion.BAND_COUNT - 1, (int) (RiftMotion.BAND_COUNT * m.depth / deepest)));
        }
        return plates;
    }
}

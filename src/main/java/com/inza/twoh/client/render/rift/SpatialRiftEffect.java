package com.inza.twoh.client.render.rift;

import com.inza.twoh.AddonMain;
import com.inza.twoh.client.render.fx.ClientEffect;
import com.inza.twoh.client.render.fx.EffectFrame;
import com.inza.twoh.client.render.fx.EffectQueue;
import com.inza.twoh.client.render.fx.RiftSceneColor;
import com.inza.twoh.client.render.fx.ShaderProgram;
import com.inza.twoh.entity.SpatialRiftEntity;
import com.inza.twoh.rift.RiftMesh;
import com.inza.twoh.rift.RiftMotion;
import com.inza.twoh.rift.RiftPlane;
import com.inza.twoh.rift.RiftStroke;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.opengl.GL11;

/** EN: Two independent scenes, a stepped aperture, and planar shards at independent depths.
 *  中文：两个独立场景、一个方格裂口，以及在不同深度运动的二维碎片。 */
final class SpatialRiftEffect implements ClientEffect {
    private final SpatialRiftEntity anchor;
    private final Runnable onClosed;
    private final RiftMesh mesh;
    private final RiftPlane plane;
    private final RiftMotion motion;
    private final float salt;
    private CompoundNBT lastTrace;
    private RiftMesh.Slot[] preview;
    private final ShaderProgram surface = program("surface");

    SpatialRiftEffect(SpatialRiftEntity anchor, Runnable onClosed) {
        this.anchor = anchor;
        this.onClosed = onClosed;
        long seed = anchor.layoutSeed();
        mesh = anchor.geometry();
        plane = anchor.stroke().plane;
        motion = mesh == null ? null : new RiftMotion(mesh.plates.length);
        salt = RiftMotion.phaseSalt(seed);
    }

    private static ShaderProgram program(String name) {
        return new ShaderProgram(new ResourceLocation(AddonMain.MOD_ID, "shaders/rift/surface.vsh"),
                new ResourceLocation(AddonMain.MOD_ID, "shaders/rift/" + name + ".fsh"));
    }

    @Override
    public boolean tick() {
        return !anchor.removed;
    }

    @Override
    public void submit(EffectFrame frame, EffectQueue queue) {
        if (anchor.removed || frame.eye.distanceToSqr(anchor.position()) > 128 * 128) {
            return;
        }
        float age = anchor.ageSeconds(frame.partialTick);
        int closeTick = anchor.closeAtAge();
        if (closeTick >= 0 && age - closeTick * .05F >= RiftMotion.MAX_CLOSE_SECONDS) {
            return;
        }
        if (anchor.isDraft()) {
            CompoundNBT trace = anchor.draftTrace();
            if (trace != lastTrace) {
                RiftStroke pen = RiftStroke.unpack(anchor.position(), anchor.stroke().yaw, trace);
                preview = RiftMesh.cut(pen);
                lastTrace = trace;
            }
            if (preview == null || preview.length == 0) {
                return;
            }
        } else {
            if (motion == null) {
                return;
            }
            motion.sample(mesh, age, closeTick * .05F, salt);
        }
        queue.world(20, this::draw);
    }

    private void draw(EffectFrame frame) {
        if (!surface.bind()) {
            return;
        }
        Vector3d delta = anchor.position().subtract(frame.eye);
        GL11.glTranslated(delta.x, delta.y, delta.z);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        surface.setInt("SceneB", 0);
        surface.setVec3("Viewport", frame.width, frame.height, 0);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        // EN: Immersive Portals already drew the live remote scene. Never cover it with a substitute.
        // 中文：此时裂口中已是真实彼端世界，不在它上面再铺一层模拟画面。
        if (anchor.isDraft()) {
            surface.setInt("Layer", 2);
            GL11.glColor3f(.85F, .33F, .85F);
            GL11.glBegin(GL11.GL_QUADS);
            for (RiftMesh.Slot s : preview) {
                if (s.horizontal) {
                    fixed(s.x, s.y - .008F, s.u, s.y);
                    fixed(s.x, s.v, s.u, s.v + .008F);
                } else {
                    fixed(s.x - .008F, s.y, s.x, s.v);
                    fixed(s.u, s.y, s.u + .008F, s.v);
                }
            }
            GL11.glEnd();
            return;
        }
        // EN: The first frame back may lack a source capture. Only the shard faces need it.
        // 中文：返回后的首帧可能还没有此岸纹理；只有碎片表面依赖它，描边照常绘制。
        if (RiftSceneColor.bind(anchor.level)) {
            drawShardFaces();
        }
        surface.setInt("Layer", 2);
        GL11.glBegin(GL11.GL_QUADS);
        for (RiftMesh.Edge edge : mesh.edges) {
            drawEdge(edge);
        }
        GL11.glEnd();
    }

    private void drawShardFaces() {
        surface.setInt("Layer", 1);
        GL11.glBegin(GL11.GL_QUADS);
        for (RiftMesh.Face f : mesh.faces) {
            if (!motion.poses[f.id].lifted) {
                continue;
            }
            GL11.glColor3f(motion.poses[f.id].loose, f.id / 64F, 0);
            quad(f.id, f.x, f.y, f.u, f.v, 0);
        }
        GL11.glEnd();
    }

    private void fixed(float x, float y, float u, float v) {
        quad(-1, x, y, u, v, .002F);
    }

    private void quad(int id, double x, double y, double u, double v, float bias) {
        vertex(id, x, y, bias);
        vertex(id, u, y, bias);
        vertex(id, u, v, bias);
        vertex(id, x, v, bias);
    }

    private void drawEdge(RiftMesh.Edge e) {
        if (e.b < 0) {
            if (motion.slit <= 0) {
                return;
            }
            float lo = (1 - motion.slit) * .5F, hi = 1 - lo;
            if (Math.max(e.first, e.last) < lo || Math.min(e.first, e.last) > hi) {
                return;
            }
            float from = 0, to = 1;
            if (e.first != e.last) {
                float a = (lo - e.first) / (e.last - e.first), b = (hi - e.first) / (e.last - e.first);
                from = RiftMotion.clamp(Math.min(a, b));
                to = RiftMotion.clamp(Math.max(a, b));
            }
            strip(e, e.a, from, to, .009F);
        } else if (motion.poses[e.a].lifted || motion.poses[e.b].lifted) {
            if (e.a != e.b) {
                strip(e, e.a, 0, 1, .007F);
                strip(e, e.b, 0, 1, .007F);
            }
        } else if (motion.branches > 0) {
            float length = Math.abs(e.u - e.x) + Math.abs(e.v - e.y);
            float end = RiftMotion.clamp((motion.front - e.first) / length);
            float start = RiftMotion.clamp(1 - (motion.front - e.last) / length);
            if (end >= start) {
                strip(e, 0, 0, 1, .012F * motion.branches);
            }
            else {
                strip(e, 0, 0, end, .012F * motion.branches);
                strip(e, 0, start, 1, .012F * motion.branches);
            }
        }
    }

    private void strip(RiftMesh.Edge e, int id, float from, float to, float r) {
        if (to <= from) {
            return;
        }
        float x = e.x + (e.u - e.x) * from, y = e.y + (e.v - e.y) * from;
        float u = e.x + (e.u - e.x) * to, v = e.y + (e.v - e.y) * to;
        float dx = e.x == e.u ? r : 0, dy = e.y == e.v ? r : 0;
        GL11.glColor3f(.86F, .43F, .90F);
        quad(id, x - dx, y - dy, u + dx, v + dy, .002F);
    }

    private void vertex(int id, double x, double y, float bias) {
        Vector3d rest = plane.point(x, y);
        GL11.glTexCoord3d(rest.x, rest.y, rest.z);
        Vector3d point = rest.add(plane.inward.scale(bias));
        if (id >= 0 && motion.poses[id].lifted) {
            // EN: Move the shard rigidly in its pivot's tangent frame, keeping its original texture sample.
            // 中文：碎片绕自身切平面的中心刚性运动；纹理始终采样碎裂前的位置。
            RiftMesh.Plate p = mesh.plates[id];
            RiftMotion.Pose m = motion.poses[id];
            Vector3d center = plane.point(p.x, p.y);
            Vector3d delta = point.subtract(center);
            double a = delta.dot(plane.horizontal), b = delta.dot(plane.vertical), c = delta.dot(plane.inward);
            double tilted = a * m.tiltCos + c * m.tiltSin;
            point = center.add(plane.horizontal.scale(m.x + m.cos * tilted - m.sin * b))
                    .add(plane.vertical.scale(m.y + m.sin * tilted + m.cos * b))
                    .add(plane.inward.scale(m.z - a * m.tiltSin + c * m.tiltCos));
        }
        GL11.glVertex3d(point.x, point.y, point.z);
    }

    @Override
    public void close() {
        try {
            surface.close();
        } finally {
            onClosed.run();
        }
    }
}

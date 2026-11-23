package com.inza.twoh.client.render;

import com.inza.twoh.client.render.fx.EffectFrame;
import com.inza.twoh.client.render.fx.EffectQueue;
import com.inza.twoh.init.InitBlock;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * EN: The ordinary solid block owns depth. Paint its visible faces independently of the terrain backend.
 * 中文：普通实体方块负责写入深度；单独为可见表面着色，不向区块渲染器注册新通道。
 */
public final class FloorSurface {
    private static final Map<ClientWorld, FloorSurface> WORLDS = new WeakHashMap<>();
    private static final int BUILD_BUDGET = 16;
    private final Map<Chunk, List<AxisAlignedBB>> meshes = new WeakHashMap<>();
    private final List<AxisAlignedBB> nearby = new ArrayList<>();
    private long tick = Long.MIN_VALUE;
    private int centerX, centerZ, range;

    private FloorSurface() {
    }

    public static void invalidate(BlockPos pos) {
        // EN: The vanilla notification has no world argument; nested cameras may have a different owner.
        // 中文：原版回调不提供世界参数，嵌套镜头不一定属于当前主世界。
        WORLDS.forEach((world, surface) -> {
            Chunk chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null && surface.meshes.remove(chunk) != null) {
                surface.tick = Long.MIN_VALUE;
            }
        });
    }

    public static void submit(EffectFrame frame, EffectQueue queue) {
        FloorSurface surface = WORLDS.computeIfAbsent(frame.world, world -> new FloorSurface());
        surface.refresh(frame);
        if (!surface.nearby.isEmpty()) {
            queue.world(-10, surface::draw);
        }
    }

    private void refresh(EffectFrame frame) {
        int cx = (int) Math.floor(frame.eye.x) >> 4;
        int cz = (int) Math.floor(frame.eye.z) >> 4;
        int radius = Minecraft.getInstance().options.renderDistance;
        long now = frame.world.getGameTime();
        if (cx == centerX && cz == centerZ && range == radius && tick == now) {
            return;
        }
        centerX = cx;
        centerZ = cz;
        range = radius;
        tick = now;
        nearby.clear();
        int budget = BUILD_BUDGET;
        // EN: Scan near chunks first. Until a chunk is ready its regular baked model stays visible.
        // 中文：优先处理近处区块，尚未生成着色网格的区块仍会显示普通方块模型。
        for (int ring = 0; ring <= radius; ring++) {
            for (int dz = -ring; dz <= ring; dz++) {
                int stride = Math.abs(dz) == ring ? 1 : Math.max(1, ring * 2);
                for (int dx = -ring; dx <= ring; dx += stride) {
                    Chunk chunk = frame.world.getChunkSource().getChunkNow(cx + dx, cz + dz);
                    if (chunk == null) {
                        continue;
                    }
                    List<AxisAlignedBB> boxes = meshes.get(chunk);
                    if (boxes == null && budget > 0) {
                        boxes = bake(chunk);
                        meshes.put(chunk, boxes);
                        budget--;
                    }
                    if (boxes != null) {
                        nearby.addAll(boxes);
                    }
                }
            }
        }
    }

    private static List<AxisAlignedBB> bake(Chunk chunk) {
        List<AxisAlignedBB> boxes = new ArrayList<>();
        Block floor = InitBlock.OVER_HEAVEN_FLOOR.get();
        for (ChunkSection section : chunk.getSections()) {
            if (ChunkSection.isEmpty(section) || !section.maybeHas(state -> state.is(floor))) {
                continue;
            }
            BitSet cells = new BitSet(4096);
            for (int i = 0; i < 4096; i++) {
                if (section.getBlockState(i & 15, i >> 8, (i >> 4) & 15).is(floor)) {
                    cells.set(i);
                }
            }
            // EN: Merge horizontal strips; an entire flat chunk costs one box rather than 256 blocks.
            // 中文：同层连续条带合并成面，整块平地只需一个包围盒。
            for (int i = cells.nextSetBit(0); i >= 0; i = cells.nextSetBit(i)) {
                int x = i & 15;
                int z = (i >> 4) & 15;
                int width = Math.min(16 - x, cells.nextClearBit(i) - i);
                int depth = 0;
                while (z + depth < 16) {
                    int start = i + depth * 16;
                    if (cells.nextClearBit(start) < start + width) {
                        break;
                    }
                    cells.clear(start, start + width);
                    depth++;
                }
                int x0 = chunk.getPos().getMinBlockX() + x;
                int y0 = section.bottomBlockY() + (i >> 8);
                int z0 = chunk.getPos().getMinBlockZ() + z;
                boxes.add(new AxisAlignedBB(x0, y0, z0, x0 + width, y0 + 1, z0 + depth));
            }
        }
        return boxes;
    }

    private void draw(EffectFrame frame) {
        if (!OverHeavenPortalShader.bindFloor(frame)) {
            return;
        }
        Vector3d eye = frame.eye;
        ClippingHelper frustum = new ClippingHelper(frame.viewMatrix(), frame.projectionMatrix());
        frustum.prepare(eye.x, eye.y, eye.z);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-1, -1);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glBegin(GL11.GL_QUADS);
        try {
            for (AxisAlignedBB box : nearby) {
                if (frustum.isVisible(box)) {
                    drawVisibleFaces(box, eye);
                }
            }
        } finally {
            GL11.glEnd();
        }
    }

    private static void drawVisibleFaces(AxisAlignedBB box, Vector3d eye) {
        double x = box.minX - eye.x, X = box.maxX - eye.x;
        double y = box.minY - eye.y, Y = box.maxY - eye.y;
        double z = box.minZ - eye.z, Z = box.maxZ - eye.z;
        if (y > 0 || Y < 0) {
            double h = y > 0 ? y : Y;
            GL11.glVertex3d(x, h, z);
            GL11.glVertex3d(X, h, z);
            GL11.glVertex3d(X, h, Z);
            GL11.glVertex3d(x, h, Z);
        }
        if (x > 0 || X < 0) {
            double h = x > 0 ? x : X;
            GL11.glVertex3d(h, y, z);
            GL11.glVertex3d(h, Y, z);
            GL11.glVertex3d(h, Y, Z);
            GL11.glVertex3d(h, y, Z);
        }
        if (z > 0 || Z < 0) {
            double h = z > 0 ? z : Z;
            GL11.glVertex3d(x, y, h);
            GL11.glVertex3d(X, y, h);
            GL11.glVertex3d(X, Y, h);
            GL11.glVertex3d(x, Y, h);
        }
    }
}

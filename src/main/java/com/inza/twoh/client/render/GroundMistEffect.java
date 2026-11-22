package com.inza.twoh.client.render;

import com.inza.twoh.client.render.fx.ClientEffect;
import com.inza.twoh.client.render.fx.EffectFrame;
import com.inza.twoh.client.render.fx.EffectQueue;
import com.inza.twoh.client.render.fx.VolumeBoxes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.Heightmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** EN: One ground-mist instance per Over Heaven world. 中文：每个超越天堂客户端世界只持有一份地雾实例。 */
public final class GroundMistEffect implements ClientEffect {
    private static final float MIST_HEIGHT = 1.0F;
    private final List<MistBank> banks = new ArrayList<>();
    private int lastX, lastZ, lastRange;
    private long nextScan;
    private float radius;

    @Override
    public void submit(EffectFrame frame, EffectQueue queue) {
        radius = Math.min(64F, Minecraft.getInstance().options.renderDistance * 16F);
        refresh(frame.world, (int) Math.floor(frame.eye.x) >> 4, (int) Math.floor(frame.eye.z) >> 4,
                (int) Math.ceil(radius / 16F));
        if (!banks.isEmpty()) {
            queue.world(0, this::draw);
        }
    }

    private void refresh(ClientWorld world, int cx, int cz, int range) {
        long now = world.getGameTime();
        if (cx == lastX && cz == lastZ && range == lastRange && now < nextScan && now >= nextScan - 15) {
            return;
        }
        lastX = cx;
        lastZ = cz;
        lastRange = range;
        nextScan = now + 15;
        banks.clear();
        int[] tops = new int[256];
        for (int z = cz - range; z <= cz + range; z++) {
            for (int x = cx - range; x <= cx + range; x++) {
                Chunk chunk = world.getChunkSource().getChunkNow(x, z);
                if (chunk != null) {
                    collect(world, chunk, x * 16, z * 16, tops);
                }
            }
        }
    }

    private void collect(ClientWorld world, Chunk chunk, int originX, int originZ, int[] tops) {
        // EN: Merge flat columns into rectangles rather than drawing one box per block.
        // 中文：把相邻的等高列合成矩形，不为每个方块单独绘制雾盒。
        for (int i = 0; i < tops.length; i++) {
            tops[i] = 1 + chunk.getHeight(Heightmap.Type.WORLD_SURFACE, i & 15, i >> 4);
        }
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int top = tops[z * 16 + x];
                if (top <= 0) {
                    continue;
                }
                int endX = x + 1;
                int endZ = z + 1;
                while (endX < 16 && tops[z * 16 + endX] == top) {
                    endX++;
                }
                while (endZ < 16 && matches(tops, endZ, x, endX, top)) {
                    endZ++;
                }
                int x0 = originX + x;
                int z0 = originZ + z;
                int x1 = originX + endX;
                int z1 = originZ + endZ;
                banks.add(new MistBank(x0, top, z0, x1, z1, exposedEdges(world, x0, z0, x1, z1, top)));
                for (int row = z; row < endZ; row++) {
                    Arrays.fill(tops, row * 16 + x, row * 16 + endX, 0);
                }
            }
        }
    }

    private static boolean matches(int[] tops, int row, int x0, int x1, int top) {
        for (int x = x0; x < x1; x++) {
            if (tops[row * 16 + x] != top) {
                return false;
            }
        }
        return true;
    }

    private static boolean lower(ClientWorld world, int x, int z, int height) {
        Chunk chunk = world.getChunkSource().getChunkNow(x >> 4, z >> 4);
        return chunk != null && chunk.getHeight(Heightmap.Type.WORLD_SURFACE, x & 15, z & 15) + 1 < height;
    }

    private static int exposedEdges(ClientWorld world, int x0, int z0, int x1, int z1, int height) {
        int mask = 15;
        for (int z = z0; z < z1 && (mask & 3) != 0; z++) {
            if (!lower(world, x0 - 1, z, height)) {
                mask &= ~1;
            }
            if (!lower(world, x1, z, height)) {
                mask &= ~2;
            }
        }
        for (int x = x0; x < x1 && (mask & 12) != 0; x++) {
            if (!lower(world, x, z0 - 1, height)) {
                mask &= ~4;
            }
            if (!lower(world, x, z1, height)) {
                mask &= ~8;
            }
        }
        return mask;
    }

    private static final class MistBank {
        final int minX, y, minZ, maxX, maxZ, edges;

        MistBank(int minX, int y, int minZ, int maxX, int maxZ, int edges) {
            this.minX = minX;
            this.y = y;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
            this.edges = edges;
        }
    }

    private void draw(EffectFrame frame) {
        if (!OverHeavenPortalShader.bindMist(frame, radius)) {
            return;
        }
        Vector3d eye = frame.eye;
        VolumeBoxes.draw(batch -> {
            for (MistBank box : banks) {
                if (Math.abs(box.y - eye.y) <= radius + 1) {
                    float y = (float) (box.y - eye.y);
                    batch.box((float) (box.minX - eye.x), y + 0.002F, (float) (box.minZ - eye.z),
                            (float) (box.maxX - eye.x), y + MIST_HEIGHT, (float) (box.maxZ - eye.z), box.edges);
                }
            }
        });
    }
}

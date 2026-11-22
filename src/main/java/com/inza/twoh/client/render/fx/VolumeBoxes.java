package com.inza.twoh.client.render.fx;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.function.Consumer;

/**
 * EN: Batch translucent exit faces. TEX0.xyz/w carries the low corner and edge mask; TEX1.xyz the high corner.
 * 中文：批量绘制半透明体积的背面。TEX0 的 xyz/w 传下界和边缘标记，TEX1 的 xyz 传上界。
 */
public final class VolumeBoxes {
    private static final VolumeBoxes WRITER = new VolumeBoxes();
    private static final int[][] FACES = {
            {0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4},
            {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}
    };

    private VolumeBoxes() {
    }

    // EN: Invoke inside queue.world; the queue restores blend, depth and culling after the pass.
    // 中文：在 queue.world 的绘制回调内使用，队列负责恢复混合、深度及剔除状态。
    public static void draw(Consumer<VolumeBoxes> boxes) {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_FRONT);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glBegin(GL11.GL_QUADS);
        try {
            boxes.accept(WRITER);
        } finally {
            GL11.glEnd();
        }
    }

    public void box(float x, float y, float z, float endX, float endY, float endZ, int exposed) {
        GL13.glMultiTexCoord4f(GL13.GL_TEXTURE0, x, y, z, exposed);
        GL13.glMultiTexCoord3f(GL13.GL_TEXTURE1, endX, endY, endZ);
        for (int[] face : FACES) {
            for (int corner : face) {
                GL11.glVertex3f((corner & 1) == 0 ? x : endX, (corner & 2) == 0 ? y : endY,
                        (corner & 4) == 0 ? z : endZ);
            }
        }
    }
}

package com.inza.twoh.client.render.fx;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** EN: Lazy GPU copy of the current viewport; never sample the attachment being drawn to.
 *  中文：需要时才在显存中复制当前视口，不从正在写入的颜色附件采样。 */
final class SceneColor implements AutoCloseable {
    private final IntBuffer viewport = BufferUtils.createIntBuffer(4);
    private int texture;
    private int width;
    private int height;

    void bindCopy() {
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int w = viewport.get(2);
        int h = viewport.get(3);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        if (texture == 0) {
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        } else {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        }
        if (width != w || height != h) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            width = w;
            height = h;
        }
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, viewport.get(0), viewport.get(1), w, h);
    }

    boolean bindExisting() {
        if (texture == 0) {
            return false;
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        return true;
    }

    static void drawQuad() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glColorMask(true, true, true, true);
        GL11.glBegin(GL11.GL_QUADS);
        try {
            corner(-1, -1, 0, 0);
            corner(1, -1, 1, 0);
            corner(1, 1, 1, 1);
            corner(-1, 1, 0, 1);
        } finally {
            GL11.glEnd();
        }
    }

    private static void corner(float x, float y, float u, float v) {
        GL11.glTexCoord2f(u, v);
        GL11.glVertex2f(x, y);
    }

    @Override
    public void close() {
        if (texture != 0) {
            GL11.glDeleteTextures(texture);
            texture = 0;
            width = 0;
            height = 0;
        }
    }
}

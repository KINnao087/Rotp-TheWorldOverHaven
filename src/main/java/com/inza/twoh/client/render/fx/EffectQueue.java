package com.inza.twoh.client.render.fx;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** EN: Submit geometry first, then ordered color passes. Equal priorities keep submission order.
 *  中文：先画世界几何，再按顺序执行屏幕颜色处理；同优先级按提交顺序执行。 */
public final class EffectQueue {
    private final List<Draw> world = new ArrayList<>();
    private final List<Draw> screen = new ArrayList<>();
    private final SceneColor color = new SceneColor();

    EffectQueue() {
    }

    public void world(int order, Consumer<EffectFrame> geometry) {
        world.add(new Draw(order, geometry));
    }

    // EN: The fragment shader samples SceneColor on unit 0. The supplied program must output the whole viewport.
    // 中文：片元着色器从 0 号纹理单元的 SceneColor 采样，程序应输出整个视口。
    public void screen(int order, ShaderProgram shader, BiConsumer<ShaderProgram, EffectFrame> uniforms) {
        screen.add(new Draw(order, frame -> {
            if (shader.bind()) {
                color.bindCopy();
                shader.setInt("SceneColor", 0);
                uniforms.accept(shader, frame);
                SceneColor.drawQuad();
            }
        }));
    }

    void flush(EffectFrame frame) {
        run(world, frame, false);
        run(screen, frame, true);
    }

    void clearTasks() {
        world.clear();
        screen.clear();
    }

    void releaseWorld() {
        clearTasks();
        color.close();
    }

    private static void run(List<Draw> draws, EffectFrame frame, boolean fullscreen) {
        if (draws.size() > 1) {
            draws.sort(Comparator.comparingInt(draw -> draw.order));
        }
        for (Draw draw : draws) {
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            int textureUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            if (fullscreen) {
                GL11.glLoadIdentity();
            }
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            try {
                if (fullscreen) {
                    GL11.glLoadIdentity();
                } else {
                    frame.applyView();
                }
                draw.render.accept(frame);
            } finally {
                // EN: Raw compatibility GL is scoped to this callback. The caller keeps its framebuffer and state.
                // 中文：回调只在当前目标上用原生 GL 绘制，返回时恢复原来的程序、矩阵和纹理状态。
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glPopAttrib();
                GL11.glMatrixMode(matrixMode);
                GL13.glActiveTexture(textureUnit);
                GL20.glUseProgram(program);
            }
        }
    }

    private static final class Draw {
        final int order;
        final Consumer<EffectFrame> render;

        Draw(int order, Consumer<EffectFrame> render) {
            this.order = order;
            this.render = render;
        }
    }
}

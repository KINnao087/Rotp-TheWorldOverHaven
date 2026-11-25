package com.inza.twoh.client.render.ascension;

import com.inza.twoh.AddonMain;
import com.inza.twoh.client.render.fx.EffectFrame;
import com.inza.twoh.client.render.fx.ShaderProgram;
import com.inza.twoh.client.render.heaven.HeavenEnvironment;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

final class AscensionComposite {
    private static final ShaderProgram WORLD = new ShaderProgram(asset("screen.vsh"), asset("transition.fsh"), HeavenEnvironment.COMMON);
    private static final Capture COLOR = new Capture(GL11.GL_RGBA8);
    private static final Capture DEPTH = new Capture(GL14.GL_DEPTH_COMPONENT24);

    private AscensionComposite() {}

    private static ResourceLocation asset(String file) {
        return new ResourceLocation(AddonMain.MOD_ID, "shaders/ascension/" + file);
    }

    /** The hand, GUI and other dimensions are never captured. No feedback into our own source textures. */
    static boolean render(EffectFrame frame, float time, boolean arrived, boolean returning,
                          Vector3d center, Vector3d virtualEye) {
        Matrix4f vp = frame.projectionMatrix();
        vp.multiply(frame.viewMatrix());
        Matrix4f inverse = vp.copy();
        if (!inverse.invert()) return false;
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        if (viewport[2] == 0 || viewport[3] == 0) return false;
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int activeUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            if (!WORLD.bind()) return false;
            try (HeavenEnvironment.Binding sky = HeavenEnvironment.bind(WORLD, 2, 3, virtualEye, frame.partialTick)) {
                if (sky == null) return false;
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, draw);
                GL13.glActiveTexture(GL13.GL_TEXTURE0); COLOR.copy(viewport);
                GL13.glActiveTexture(GL13.GL_TEXTURE1); DEPTH.copy(viewport);
                GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(false);
                GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST);
                GL11.glColorMask(true, true, true, true);
                camera(WORLD, vp, inverse);
                vector(WORLD, "centerRelative", center.subtract(frame.eye));
                WORLD.setFloat("t", time);
                WORLD.setInt("destination", arrived ? 1 : 0);
                WORLD.setInt("returning", returning ? 1 : 0);
                GL11.glBegin(GL11.GL_TRIANGLES);
                GL11.glVertex2f(-1,-1); GL11.glVertex2f(3,-1); GL11.glVertex2f(-1,3);
                GL11.glEnd();
                return true;
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL11.glPopAttrib(); GL13.glActiveTexture(activeUnit); GL20.glUseProgram(program);
        }
    }

    private static void camera(ShaderProgram p, Matrix4f vp, Matrix4f inverse) {
        p.setMatrix("vp",vp); p.setMatrix("invertedVP",inverse);
        p.setInt("SceneColor",0); p.setInt("SceneDepth",1);
    }

    private static void vector(ShaderProgram p, String uniform, Vector3d vec) {
        p.setVec3(uniform,(float)vec.x,(float)vec.y,(float)vec.z);
    }

    static void release() { COLOR.close(); DEPTH.close(); }

    private static final class Capture {
        final int format;
        int texture, width, height;
        Capture(int format) { this.format=format; }
        void copy(int[] vp) {
            if(texture==0) {
                texture=GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
            } else GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            if(width!=vp[2] || height!=vp[3]) {
                width=vp[2]; height=vp[3];
                GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D,0,format,vp[0],vp[1],width,height,0);
            } else GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,vp[0],vp[1],width,height);
        }
        void close() {
            if (texture!=0) GL11.glDeleteTextures(texture);
            texture=width=height=0;
        }
    }
}

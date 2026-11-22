package com.inza.twoh.client.render;

import com.inza.twoh.AddonMain;
import com.inza.twoh.client.render.fx.EffectFrame;
import com.inza.twoh.client.render.fx.EffectRenderer;
import com.inza.twoh.client.render.fx.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** EN: Shared material, not a second renderer for floor blocks. 中文：方块与地雾共用的材质，不重复绘制区块。 */
public final class OverHeavenPortalShader {
    private static final ShaderProgram PROGRAM = new ShaderProgram(
            new ResourceLocation(AddonMain.MOD_ID, "shaders/over_heaven_portal.vsh"),
            new ResourceLocation(AddonMain.MOD_ID, "shaders/over_heaven_portal.fsh"));
    private static int previousFloorProgram;

    private OverHeavenPortalShader() {
    }

    public static void setup(ResourceLocation fallback) {
        previousFloorProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (!PROGRAM.bind()) {
            Minecraft.getInstance().getTextureManager().bind(fallback);
            return;
        }
        EffectFrame frame = EffectRenderer.frame();
        PROGRAM.setInt("MistPass", 0);
        PROGRAM.setFloat("FlowTime", frame == null ? 0 : frame.seconds());
        if (frame == null) {
            PROGRAM.setVec3("CameraForward", 0, 0, -1);
            PROGRAM.setVec3("CameraRight", 1, 0, 0);
            PROGRAM.setVec3("CameraUp", 0, 1, 0);
        } else {
            direction("CameraForward", frame.forward);
            direction("CameraRight", frame.right);
            direction("CameraUp", frame.up);
        }
    }

    public static void clear() {
        GL20.glUseProgram(previousFloorProgram);
    }

    static boolean bindMist(EffectFrame frame, float radius) {
        if (!PROGRAM.bind()) {
            return false;
        }
        PROGRAM.setInt("MistPass", 1);
        PROGRAM.setFloat("FlowTime", frame.seconds());
        PROGRAM.setVec3("MistOrigin", wrap(frame.eye.x), 0, wrap(frame.eye.z));
        PROGRAM.setFloat("MistRadius", radius);
        return true;
    }

    private static void direction(String uniform, Vector3d value) {
        PROGRAM.setVec3(uniform, (float) value.x, (float) value.y, (float) value.z);
    }

    private static float wrap(double position) {
        return (float) (position - 8192.0 * Math.floor(position / 8192.0));
    }
}

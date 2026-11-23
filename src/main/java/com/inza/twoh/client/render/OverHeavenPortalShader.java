package com.inza.twoh.client.render;

import com.inza.twoh.AddonMain;
import com.inza.twoh.client.render.fx.EffectFrame;
import com.inza.twoh.client.render.fx.ShaderProgram;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;

/** EN: Portal floor and ground mist share a shader program. 中文：天堂地板与贴地雾共用着色器程序。 */
public final class OverHeavenPortalShader {
    private static final ShaderProgram PROGRAM = new ShaderProgram(
            new ResourceLocation(AddonMain.MOD_ID, "shaders/over_heaven_portal.vsh"),
            new ResourceLocation(AddonMain.MOD_ID, "shaders/over_heaven_portal.fsh"));

    private OverHeavenPortalShader() {
    }

    static boolean bindFloor(EffectFrame frame) {
        if (!PROGRAM.bind()) {
            return false;
        }
        PROGRAM.setInt("MistPass", 0);
        PROGRAM.setFloat("FlowTime", frame.seconds());
        direction("CameraForward", frame.forward);
        direction("CameraRight", frame.right);
        direction("CameraUp", frame.up);
        return true;
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

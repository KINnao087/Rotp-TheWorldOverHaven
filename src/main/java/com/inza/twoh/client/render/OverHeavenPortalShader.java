package com.inza.twoh.client.render;

import com.inza.twoh.AddonMain;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public final class OverHeavenPortalShader {
    private static final String VERTEX_SHADER =
            "#version 120\n" +
            "varying vec3 ViewDirection;\n" +
            "void main() {\n" +
            "    vec4 viewPosition = gl_ModelViewMatrix * gl_Vertex;\n" +
            "    ViewDirection = normalize(viewPosition.xyz);\n" +
            "    gl_Position = ftransform();\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 120\n" +
            "uniform sampler2D DiffuseSampler;\n" +
            "uniform vec3 CameraForward;\n" +
            "uniform vec3 CameraRight;\n" +
            "uniform vec3 CameraUp;\n" +
            "varying vec3 ViewDirection;\n" +
            "const float PI = 3.14159265358979323846;\n" +
            "void main() {\n" +
            "    vec3 viewRay = normalize(ViewDirection);\n" +
            "    vec3 ray = normalize(CameraRight * viewRay.x + CameraUp * viewRay.y - CameraForward * viewRay.z);\n" +
            "    float u = atan(ray.z, ray.x) / (PI * 2.0) + 0.5;\n" +
            "    float v = 0.5 - asin(clamp(ray.y, -1.0, 1.0)) / PI;\n" +
            "    vec2 uv = vec2(fract(u), clamp(v, 0.0, 1.0));\n" +
            "    vec4 color = texture2D(DiffuseSampler, uv);\n" +
            "    gl_FragColor = vec4(color.rgb, 1.0);\n" +
            "}\n";

    private static float forwardX = 0.0F;
    private static float forwardY = 0.0F;
    private static float forwardZ = -1.0F;
    private static float rightX = 1.0F;
    private static float rightY = 0.0F;
    private static float rightZ = 0.0F;
    private static float upX = 0.0F;
    private static float upY = 1.0F;
    private static float upZ = 0.0F;

    private static int program;
    private static boolean failed;

    private OverHeavenPortalShader() {
    }

    public static void setCamera(ActiveRenderInfo camera) {
        Vector3f forward = camera.getLookVector();
        Vector3f up = camera.getUpVector();

        forwardX = forward.x();
        forwardY = forward.y();
        forwardZ = forward.z();
        upX = up.x();
        upY = up.y();
        upZ = up.z();

        rightX = forwardY * upZ - forwardZ * upY;
        rightY = forwardZ * upX - forwardX * upZ;
        rightZ = forwardX * upY - forwardY * upX;
        normalizeRightVector();
    }

    public static void setup(ResourceLocation texture) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getTextureManager().bind(texture);

        ensureProgram();
        if (program == 0) {
            return;
        }

        RenderSystem.enableTexture();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "DiffuseSampler"), 0);
        GL20.glUniform3f(GL20.glGetUniformLocation(program, "CameraForward"), forwardX, forwardY, forwardZ);
        GL20.glUniform3f(GL20.glGetUniformLocation(program, "CameraRight"), rightX, rightY, rightZ);
        GL20.glUniform3f(GL20.glGetUniformLocation(program, "CameraUp"), upX, upY, upZ);
    }

    public static void clear() {
        GL20.glUseProgram(0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void normalizeRightVector() {
        float length = (float) Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        if (length <= 0.0001F) {
            rightX = 1.0F;
            rightY = 0.0F;
            rightZ = 0.0F;
            return;
        }

        rightX /= length;
        rightY /= length;
        rightZ /= length;
    }

    private static void ensureProgram() {
        if (program != 0 || failed) {
            return;
        }

        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        if (vertexShader == 0 || fragmentShader == 0) {
            failed = true;
            return;
        }

        int linkedProgram = GL20.glCreateProgram();
        GL20.glAttachShader(linkedProgram, vertexShader);
        GL20.glAttachShader(linkedProgram, fragmentShader);
        GL20.glLinkProgram(linkedProgram);

        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        if (GL20.glGetProgrami(linkedProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            AddonMain.LOGGER.error("Failed to link Over Heaven floor portal shader: {}",
                    GL20.glGetProgramInfoLog(linkedProgram));
            GL20.glDeleteProgram(linkedProgram);
            failed = true;
            return;
        }

        program = linkedProgram;
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            AddonMain.LOGGER.error("Failed to compile Over Heaven floor portal shader: {}",
                    GL20.glGetShaderInfoLog(shader));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }
}

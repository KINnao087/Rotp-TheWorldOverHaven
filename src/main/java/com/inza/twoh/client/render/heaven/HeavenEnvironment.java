package com.inza.twoh.client.render.heaven;

import com.inza.twoh.AddonMain;
import com.inza.twoh.client.render.fx.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.resources.IResource;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;

/** One resource-pack-aware cubemap and noise field for both sides of the transition. Render thread only. */
public final class HeavenEnvironment {
    public static final ResourceLocation COMMON = asset("shaders/heaven/environment.glsl");
    private static final String[] FACES = {"right", "left", "top", "bottom", "back", "front"};
    private static final int SKY_SIDE = 768;
    private static final long EPOCH = Util.getMillis();
    private static int cube, grain;
    private static boolean failed;

    private HeavenEnvironment() {}

    static ResourceLocation asset(String path) {
        return new ResourceLocation(AddonMain.MOD_ID, path);
    }

    static void reload() {
        if (cube != 0) GL11.glDeleteTextures(cube);
        if (grain != 0) GL11.glDeleteTextures(grain);
        cube = grain = 0;
        failed = false;
    }

    private static void filters(int target, int wrap) {
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_S, wrap);
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_T, wrap);
    }

    private static void fillCube(IResourceManager resources) throws IOException {
        filters(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        ByteBuffer bytes = MemoryUtil.memAlloc(SKY_SIDE * SKY_SIDE * 4);
        try {
            for (int face = 0; face < FACES.length; face++) {
                ResourceLocation file = asset("textures/environment/over_heaven/" + FACES[face] + ".png");
                try (IResource res = resources.getResource(file); NativeImage source = NativeImage.read(res.getInputStream());
                     NativeImage scaled = new NativeImage(SKY_SIDE, SKY_SIDE, false)) {
                    source.resizeSubRectTo(0, 0, source.getWidth(), source.getHeight(), scaled);
                    bytes.clear();
                    for (int y = 0; y < SKY_SIDE; y++) for (int x = 0; x < SKY_SIDE; x++) {
                        // np.rot90 rotations from the native preview: top +90, bottom -90.
                        int sx = face == 2 ? SKY_SIDE-1-y : face == 3 ? y : x;
                        int sy = face == 2 ? x : face == 3 ? SKY_SIDE-1-x : y;
                        bytes.putInt(scaled.getPixelRGBA(sx, sy));
                    }
                    bytes.flip();
                    GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, 0, GL11.GL_RGBA8, SKY_SIDE, SKY_SIDE, 0,
                            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
                }
            }
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }

    private static void fillGrain(IResourceManager resources) throws IOException {
        filters(GL11.GL_TEXTURE_2D, GL11.GL_REPEAT);
        try (IResource res = resources.getResource(asset("textures/environment/heaven_grain.png"));
             NativeImage img = NativeImage.read(res.getInputStream())) {
            if (img.getWidth() != 256 || img.getHeight() != 256) throw new IOException("heaven_grain.png must be 256 x 256");
            ByteBuffer bytes = MemoryUtil.memAlloc(256 * 256);
            try {
                for (int y=0; y<256; y++) for (int x=0; x<256; x++) bytes.put((byte)img.getPixelRGBA(x,y));
                bytes.flip();
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_LUMINANCE8, 256, 256, 0, GL11.GL_LUMINANCE,
                        GL11.GL_UNSIGNED_BYTE, bytes);
            } finally {
                MemoryUtil.memFree(bytes);
            }
        }
    }

    /** Call with the shader active. Restore texture units/bindings before returning to Minecraft. */
    @Nullable
    public static Binding bind(ShaderProgram program, int cubeUnit, int noiseUnit, Vector3d eye, float partialTick) {
        Binding textures = bindTextures(cubeUnit, noiseUnit);
        if (textures != null) {
            program.setInt("heavenSky", cubeUnit);
            program.setInt("grain", noiseUnit);
            program.setVec3("heavenEye", (float) eye.x, (float) eye.y, (float) eye.z);
            program.setFloat("heavenTime", (Util.getMillis() - EPOCH) * .001F);
        }
        return textures;
    }

    static void prewarm() {
        if (cube != 0 || failed) return;
        Binding textures = bindTextures(0, 1);
        if (textures != null) textures.close();
    }

    @Nullable
    private static Binding bindTextures(int cubeUnit, int noiseUnit) {
        if (failed) return null;
        Binding scope = new Binding(cubeUnit, noiseUnit);
        boolean init = cube == 0;
        if (init) {
            GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        }
        try {
            IResourceManager resources = Minecraft.getInstance().getResourceManager();
            if (init) {
                cube = GL11.glGenTextures();
                grain = GL11.glGenTextures();
            }
            GL13.glActiveTexture(scope.cubeUnit);
            GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cube);
            if (init) fillCube(resources);
            GL13.glActiveTexture(scope.noiseUnit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, grain);
            if (init) fillGrain(resources);
            if (scope.supportsSeamless && !scope.wasSeamless) GL11.glEnable(GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS);
            GL13.glActiveTexture(scope.active);
            return scope;
        } catch (IOException | RuntimeException e) {
            scope.close();
            reload();
            failed = true;
            AddonMain.LOGGER.error("Cannot initialize the Over Heaven environment", e);
            return null;
        } finally {
            if (init) GL11.glPopClientAttrib();
        }
    }

    public static final class Binding implements AutoCloseable {
        private final int active, cubeUnit, noiseUnit, oldCube, oldNoise;
        private final boolean supportsSeamless, wasSeamless;

        private Binding(int cube, int noise) {
            active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            cubeUnit = GL13.GL_TEXTURE0 + cube;
            noiseUnit = GL13.GL_TEXTURE0 + noise;
            GL13.glActiveTexture(cubeUnit);
            oldCube = GL11.glGetInteger(GL13.GL_TEXTURE_BINDING_CUBE_MAP);
            GL13.glActiveTexture(noiseUnit);
            oldNoise = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            supportsSeamless = GL.getCapabilities().OpenGL32 || GL.getCapabilities().GL_ARB_seamless_cube_map;
            wasSeamless = supportsSeamless && GL11.glIsEnabled(GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS);
        }

        @Override public void close() {
            if (supportsSeamless && !wasSeamless) GL11.glDisable(GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS);
            GL13.glActiveTexture(cubeUnit); GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, oldCube);
            GL13.glActiveTexture(noiseUnit); GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldNoise);
            GL13.glActiveTexture(active);
        }
    }
}

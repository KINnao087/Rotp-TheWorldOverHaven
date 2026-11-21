package com.inza.twoh.client.render;

import com.inza.twoh.AddonMain;
import com.inza.twoh.init.InitDimension;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.resources.IReloadableResourceManager;
import net.minecraft.resources.IResource;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.resource.ISelectiveResourceReloadListener;
import net.minecraftforge.resource.VanillaResourceType;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, value = Dist.CLIENT)
public final class OverHeavenPortalShader {
    private static final ResourceLocation VERTEX_SHADER =
            new ResourceLocation(AddonMain.MOD_ID, "shaders/over_heaven_portal.vsh");

    private static final ResourceLocation FRAGMENT_SHADER =
            new ResourceLocation(AddonMain.MOD_ID, "shaders/over_heaven_portal.fsh");

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
    private static volatile boolean reloadPending = true;
    private static int uForward, uRight, uUp, uTime, uPass, uOrigin, uRadius;
    private static int previousFloorProgram;
    private static long startTick = Long.MIN_VALUE;
    // Merge adjacent height-map columns so an ordinary flat chunk costs one fog box.
    private static final java.util.List<int[]> banks = new java.util.ArrayList<>();
    private static java.lang.ref.WeakReference<net.minecraft.client.world.ClientWorld> bankWorld = new java.lang.ref.WeakReference<>(null);
    private static int lastCX, lastCZ, lastRange;
    private static long nextScan;
    private static void refreshBanks(net.minecraft.client.world.ClientWorld world, int cx, int cz, int range) {
        long now = world.getGameTime();
        if (world == bankWorld.get() && cx == lastCX && cz == lastCZ && range == lastRange && now < nextScan && now >= nextScan-15) return;
        bankWorld = new java.lang.ref.WeakReference<>(world); lastCX=cx; lastCZ=cz; lastRange=range; nextScan=now+15;
        banks.clear();
        int[] tops = new int[256];
        for (int zc=cz-range; zc<=cz+range; ++zc) for (int xc=cx-range; xc<=cx+range; ++xc) {
            net.minecraft.world.chunk.Chunk chunk=world.getChunkSource().getChunkNow(xc,zc);
            if (chunk==null) continue;
            for (int k=0;k<256;++k) tops[k]=1+chunk.getHeight(net.minecraft.world.gen.Heightmap.Type.WORLD_SURFACE,k&15,k>>4);
            for (int z=0;z<16;++z) for (int x=0;x<16;++x) {
                int top=tops[z*16+x]; if (top<=0) continue;
                int x1=x+1, z1=z+1;
                while (x1<16 && tops[z*16+x1]==top) ++x1;
                outer: while(z1<16) {
                    for(int i=x;i<x1;++i) if(tops[z1*16+i]!=top) break outer;
                    ++z1;
                }
                int wx=xc*16+x, wz=zc*16+z, wx1=xc*16+x1, wz1=zc*16+z1;
                banks.add(new int[]{wx,top,wz,wx1,wz1,exposedEdges(world,wx,wz,wx1,wz1,top)});
                for(int j=z;j<z1;++j) java.util.Arrays.fill(tops,j*16+x,j*16+x1,0);
            }
        }
    }

    // A rectangle side is exposed only if every neighbouring column is lower.
    private static boolean lowerSurface(net.minecraft.client.world.ClientWorld world, int x, int z, int y) {
        net.minecraft.world.chunk.Chunk chunk = world.getChunkSource().getChunkNow(x >> 4, z >> 4);
        return chunk != null && chunk.getHeight(net.minecraft.world.gen.Heightmap.Type.WORLD_SURFACE, x & 15, z & 15) + 1 < y;
    }
    private static int exposedEdges(net.minecraft.client.world.ClientWorld world, int x, int z, int X, int Z, int y) {
        int mask=15;
        for (int j=z;j<Z && (mask&3)!=0;j++) {
            if (!lowerSurface(world,x-1,j,y)) mask &= ~1;
            if (!lowerSurface(world,X,j,y)) mask &= ~2;
        }
        for (int i=x;i<X && (mask&12)!=0;i++) {
            if (!lowerSurface(world,i,z-1,y)) mask &= ~4;
            if (!lowerSurface(world,i,Z,y)) mask &= ~8;
        }
        return mask;
    }
    private static final float MIST_HEIGHT = 1.0F;

    private static final int[][] BOX_FACES = {{0,4,6,2},{1,3,7,5},{0,1,5,4},{2,6,7,3},{0,2,3,1},{4,5,7,6}};
    private static void fogBox(float x, float y, float z, float X, float Z, int exposed) {
        float Y=y+MIST_HEIGHT;
        y+=.002F; // Keep the exit face just clear of the solid surface.
        org.lwjgl.opengl.GL13.glMultiTexCoord4f(org.lwjgl.opengl.GL13.GL_TEXTURE0,x,y,z,exposed);
        org.lwjgl.opengl.GL13.glMultiTexCoord3f(org.lwjgl.opengl.GL13.GL_TEXTURE1,X,Y,Z);
        for (int[] face:BOX_FACES) for(int v:face)
            GL11.glVertex3f((v&1)==0?x:X,(v&2)==0?y:Y,(v&4)==0?z:Z);
    }


    private OverHeavenPortalShader() {
    }

    public static void registerReloadListener() {
        IReloadableResourceManager manager = (IReloadableResourceManager) Minecraft.getInstance().getResourceManager();
        ISelectiveResourceReloadListener listener = (resources, types) -> {
            // EN: Defer OpenGL work until the next draw on the render thread.
            // 中文：重载只做标记；下一次绘制时才在渲染线程操作 OpenGL。
            if (types.test(VanillaResourceType.SHADERS)) {
                reloadPending = true;
            }
        };
        manager.registerReloadListener(listener);
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
        previousFloorProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        ensureProgram();
        if (program == 0) {
            // Keep the old material visible on drivers without compatibility shaders.
            Minecraft.getInstance().getTextureManager().bind(texture);
            return;
        }
        GL20.glUseProgram(program);
        GL20.glUniform1i(uPass, 0);
        GL20.glUniform1f(uTime, seconds(Minecraft.getInstance().getFrameTime()));
        GL20.glUniform3f(uForward, forwardX, forwardY, forwardZ);
        GL20.glUniform3f(uRight, rightX, rightY, rightZ);
        GL20.glUniform3f(uUp, upX, upY, upZ);
    }

    public static void clear() {
        GL20.glUseProgram(previousFloorProgram);
    }

    private static float seconds(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        long tick = mc.level.getGameTime();
        if (startTick == Long.MIN_VALUE || tick < startTick) startTick = tick;
        return (tick - startTick + partialTick) * .05F;
    }

    @SubscribeEvent
    public static void renderGroundMist(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !InitDimension.OVER_HEAVEN.equals(mc.level.dimension())) return;

        Vector3d eye = mc.gameRenderer.getMainCamera().getPosition();
        float radius = Math.min(64F, mc.options.renderDistance * 16F);
        refreshBanks(mc.level, ((int)Math.floor(eye.x)) >> 4, ((int)Math.floor(eye.z)) >> 4, (int)Math.ceil(radius / 16F));
        if (banks.isEmpty()) return;
        ensureProgram();
        if (program == 0) return;

        int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        // Isolate raw compatibility-GL state; do not invalidate Minecraft's state cache.
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_POLYGON_BIT | GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);
        RenderSystem.pushMatrix();
        try {
            RenderSystem.multMatrix(event.getMatrixStack().last().pose());
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_FRONT);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL20.glUseProgram(program);
            GL20.glUniform1i(uPass, 1);
            GL20.glUniform1f(uTime, seconds(event.getPartialTicks()));
            GL20.glUniform3f(uOrigin, wrapPosition(eye.x), 0, wrapPosition(eye.z));
            GL20.glUniform1f(uRadius, radius);
            // Draw exit faces only; apply Beer-Lambert absorption once per intersected bank.
            GL11.glBegin(GL11.GL_QUADS);
            for (int[] b : banks) {
                if (Math.abs(b[1] - eye.y) > radius+1) continue;
                fogBox((float)(b[0]-eye.x), (float)(b[1]-eye.y), (float)(b[2]-eye.z),
                        (float)(b[3]-eye.x), (float)(b[4]-eye.z), b[5]);
            }
            GL11.glEnd();
        } finally {
            GL20.glUseProgram(previous);
            RenderSystem.popMatrix();
            GL11.glPopAttrib();
        }
    }

    private static float wrapPosition(double coordinate) {
        return (float) (coordinate - 8192.0 * Math.floor(coordinate / 8192.0));
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
        if (!reloadPending) {
            return;
        }
        reloadPending = false;

        IResourceManager resources = Minecraft.getInstance().getResourceManager();
        String vertexSource;
        String fragmentSource;
        try {
            vertexSource = readShader(resources, VERTEX_SHADER);
            fragmentSource = readShader(resources, FRAGMENT_SHADER);
        } catch (IOException failure) {
            AddonMain.LOGGER.error("Cannot load Over Heaven shader resources", failure);
            return;
        }

        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return;
        }
        int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER, fragmentSource);
        if (fragmentShader == 0) {
            GL20.glDeleteShader(vertexShader);
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
            return;
        }

        // EN: Replace the fallback only after both stages link; broken resource packs must not blank the floor.
        // 中文：两个阶段链接成功后才替换旧程序；资源包写错时继续显示上一次有效的材质。
        int previousProgram = program;
        program = linkedProgram;
        uForward = GL20.glGetUniformLocation(program, "CameraForward");
        uRight = GL20.glGetUniformLocation(program, "CameraRight");
        uUp = GL20.glGetUniformLocation(program, "CameraUp");
        uTime = GL20.glGetUniformLocation(program, "FlowTime");
        uPass = GL20.glGetUniformLocation(program, "MistPass");
        uOrigin = GL20.glGetUniformLocation(program, "MistOrigin");
        uRadius = GL20.glGetUniformLocation(program, "MistRadius");
        if (previousProgram != 0) {
            GL20.glDeleteProgram(previousProgram);
        }
    }

    private static String readShader(IResourceManager resources, ResourceLocation location) throws IOException {
        try (IResource resource = resources.getResource(location)) {
            return IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
        }
    }

    private static int compileShader(int type, ResourceLocation location, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            AddonMain.LOGGER.error("Failed to compile {}: {}", location, GL20.glGetShaderInfoLog(shader));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }
}


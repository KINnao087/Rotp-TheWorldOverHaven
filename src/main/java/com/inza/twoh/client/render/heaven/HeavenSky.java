package com.inza.twoh.client.render.heaven;

import com.inza.twoh.AddonMain;
import com.inza.twoh.client.render.fx.ShaderProgram;
import com.inza.twoh.init.InitDimension;
import com.inza.twoh.mixin.client.DimensionEffectsRegistry;
import com.inza.twoh.portalcore.immersive_portals.render.context_management.PortalRendering;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.world.DimensionRenderInfo;
import net.minecraft.resources.IReloadableResourceManager;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.resource.ISelectiveResourceReloadListener;
import net.minecraftforge.resource.VanillaResourceType;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.BooleanSupplier;

/** Heaven-only sky, drawn in the normal sky pass or against untouched far depth after a deferred pack. */
@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, value = Dist.CLIENT)
public final class HeavenSky {
    private static final ShaderProgram SKY = new ShaderProgram(HeavenEnvironment.asset("shaders/heaven/sky.vsh"),
            HeavenEnvironment.asset("shaders/heaven/sky.fsh"), HeavenEnvironment.COMMON);
    // Each group of four corners is a cube face. Position is also the texture direction.
    private static final int[] FACES = {0,2,6,4, 1,5,7,3, 0,4,5,1, 2,3,7,6, 0,1,3,2, 4,6,7,5};

    private HeavenSky() {}

    public static void register() {
        DimensionRenderInfo effects = new DimensionRenderInfo.End();
        effects.setSkyRenderHandler((ticks, partial, pose, world, mc) -> {
            if (!ShaderPack.enabled.getAsBoolean() || (PortalRendering.isRendering() && !ShaderPack.shadow.getAsBoolean())) {
                draw(partial, pose, mc, null);
            }
        });
        DimensionEffectsRegistry.twoh$effects().put(InitDimension.OVER_HEAVEN_ID, effects);
        IReloadableResourceManager resources = (IReloadableResourceManager) Minecraft.getInstance().getResourceManager();
        resources.registerReloadListener((ISelectiveResourceReloadListener) (manager, types) -> {
            if (types.test(VanillaResourceType.TEXTURES)) {
                HeavenEnvironment.reload();
                if (Minecraft.getInstance().level != null) HeavenEnvironment.prewarm();
            }
        });
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggedInEvent event) {
        // Upload the six faces at login, never in the middle of a player's first crossing.
        HeavenEnvironment.prewarm();
    }

    // Deferred packs can replace the entire background after the normal dimension sky callback.
    // Draw only at far depth after their composite, ahead of our world effects. No global pack toggle.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void afterShaderPack(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientWorld world = mc.level;
        if (world != null && world.dimension().equals(InitDimension.OVER_HEAVEN)
                && !PortalRendering.isRendering() && ShaderPack.enabled.getAsBoolean() && !ShaderPack.shadow.getAsBoolean()) {
            draw(event.getPartialTicks(), event.getMatrixStack(), mc, event.getProjectionMatrix());
        }
    }

    // Only the optional, public Iris v0 API is queried, never its private fields or global configuration.
    private static final class ShaderPack {
        static final BooleanSupplier enabled = lookup("isShaderPackInUse");
        static final BooleanSupplier shadow = lookup("isRenderingShadowPass");
        private static BooleanSupplier lookup(String name) {
            if (ModList.get().isLoaded("oculus")) try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                MethodHandles.Lookup pub = MethodHandles.publicLookup();
                MethodType flag = MethodType.methodType(boolean.class);
                MethodHandle handle = pub.findVirtual(api, name, flag).bindTo(instance);
                return new BooleanSupplier() {
                    boolean failed;
                    public boolean getAsBoolean() {
                        if (failed) return false;
                        try { return (boolean) handle.invokeExact(); }
                        catch (Throwable failure) {
                            if (failure instanceof Error) throw (Error) failure;
                            failed = true;
                            AddonMain.LOGGER.warn("Oculus sky query unavailable: " + name, failure);
                            return false;
                        }
                    }
                };
            } catch (ReflectiveOperationException | LinkageError unsupported) {
                AddonMain.LOGGER.warn("Oculus sky API unavailable; using the normal dimension sky pass", unsupported);
            }
            return () -> false;
        }
    }

    private static void draw(float partial, MatrixStack pose, Minecraft minecraft, Matrix4f lateProjection) {
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (!SKY.bind()) return;
        try (HeavenEnvironment.Binding textures = HeavenEnvironment.bind(SKY, 0, 1,
                minecraft.gameRenderer.getMainCamera().getPosition(), partial)) {
            if (textures == null) return;
            SKY.setMatrix("SkyView", pose.last().pose());
            SKY.setInt("LateSky", lateProjection != null ? 1 : 0);
            if (lateProjection != null) SKY.setMatrix("SkyProjection", lateProjection);
            // GL_DEPTH_RANGE belongs to the viewport attribute group, not the depth-buffer group.
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT
                    | (lateProjection != null ? GL11.GL_VIEWPORT_BIT : 0));
            try {
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDepthMask(false);
                // Decouple clip-space from window depth. A cube exactly on its clip plane can
                // lose a face or skip scanlines on GL_EQUAL due to projection/rounding.
                if (lateProjection != null) GL11.glDepthRange(1.0, 1.0);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(lateProjection == null ? GL11.GL_LEQUAL : GL11.GL_EQUAL);
                GL11.glBegin(GL11.GL_QUADS);
                for (int i : FACES) {
                    GL11.glVertex3f((i & 1) == 0 ? -1 : 1, (i & 2) == 0 ? -1 : 1, (i & 4) == 0 ? -1 : 1);
                }
                GL11.glEnd();
            } finally {
                GL11.glPopAttrib();
            }
        } finally {
            GL20.glUseProgram(previousProgram);
        }
    }
}

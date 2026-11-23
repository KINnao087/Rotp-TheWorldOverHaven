package com.inza.twoh.client.render.timestop;

import com.github.standobyte.jojo.action.stand.TimeStop;
import com.github.standobyte.jojo.client.ClientModSettings;
import com.github.standobyte.jojo.client.ClientTimeStopHandler;
import com.github.standobyte.jojo.init.power.stand.ModStandsInit;
import com.inza.twoh.AddonMain;
import com.inza.twoh.client.render.fx.EffectFrame;
import com.inza.twoh.client.render.fx.EffectRenderer;
import com.inza.twoh.network.packet.CastVisualPacket;
import com.inza.twoh.portalcore.immersive_portals.render.context_management.PortalRendering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;


@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, value = Dist.CLIENT)
public final class CastScene {
    private static CastVisualPacket pending;
    private static CastVisualPacket selected;
    private static CastVisualPacket active;
    private static Vector3d origin;
    static Framebuffer snapshot;
    static Matrix4f inverse;
    static Matrix4f viewProjection;
    static Vector3d center;
    static float nativeLength;
    static boolean casterView;
    static CastAppearance appearance;
    static float initialViewDistance;
    static boolean ready;

    private CastScene() {}

    public static void stage(CastVisualPacket descriptor) {
        pending = descriptor;
    }

    public static void selectStart(int stopId) {
        selected = pending != null && pending.stopId == stopId ? pending : null;
        pending = null;
    }

    public static void finishStart() {
        selected = null;
    }

    public static void begin(TimeStop action) {
        Minecraft mc = Minecraft.getInstance();
        active = null;
        ready = false;
        if (selected == null || mc.level == null || mc.player == null
                || action != ModStandsInit.THE_WORLD_TIME_STOP.get()
                || !selected.dimension.equals(mc.level.dimension().location())) {
            return;
        }
        // EN: Rift travel can replace remote entities. Use the server's cast snapshot, not their capabilities.
        // 中文：裂隙往返可能重建远端实体；按服务端施放快照选视角，不再依赖远端实体的能力数据。
        active = selected;
        appearance = new CastAppearance(active.appearanceSeed);
        casterView = active.owner.equals(mc.player.getUUID());
        origin = active.origin;
        initialViewDistance = (float) mc.gameRenderer.getMainCamera().getPosition().distanceTo(origin);
    }

    static boolean eligible() {
        ClientTimeStopHandler clock = ClientTimeStopHandler.getInstance();
        // EN: Stock jojo:time_stop skips the opening circle for stops shorter than 100 ticks.
        // 中文：原版在短于 100 tick 的时停中跳过释放圆环，此处沿用相同条件。
        Minecraft mc = Minecraft.getInstance();
        return active != null && mc.level != null && active.dimension.equals(mc.level.dimension().location())
                && clock.isTimeStopped()
                && clock.canSeeInStoppedTime() && clock.getTimeStopLength() >= 100
                && ClientModSettings.getSettingsReadOnly().timeStopAnimation;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void capture(RenderWorldLastEvent event) {
        if (PortalRendering.isRendering()) return;
        ready = false;
        if (!eligible() || (nativeLength > 0 && ClientTimeStopHandler.getInstance().getTimeStopTicks() >= nativeLength)) return;
        EffectFrame frame = EffectRenderer.frame();
        if (frame == null || frame.world != Minecraft.getInstance().level) return;
        Matrix4f transform = frame.projectionMatrix();
        transform.multiply(frame.viewMatrix());
        viewProjection = transform.copy();
        if (!transform.invert()) return;

        // EN: Capture before first-person hand rendering clears the world depth.
        // 中文：在第一人称手部清除世界深度前，保存同一帧的世界颜色和深度。
        Framebuffer main = Minecraft.getInstance().getMainRenderTarget();
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            if (snapshot == null) snapshot = new Framebuffer(main.width, main.height, true, Minecraft.ON_OSX);
            if (main.isStencilEnabled() && !snapshot.isStencilEnabled()) snapshot.enableStencil();
            if (snapshot.width != main.width || snapshot.height != main.height) {
                snapshot.resize(main.width, main.height, Minecraft.ON_OSX);
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height,
                    GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            inverse = transform;
            center = origin.subtract(frame.eye);
            ready = true;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        }
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload event) {
        if (!event.getWorld().isClientSide() || event.getWorld() != Minecraft.getInstance().level) return;
        ready = false;
        if (snapshot != null) {
            snapshot.destroyBuffers();
            snapshot = null;
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        pending = null;
        selected = null;
        active = null;
        ready = false;
    }
}

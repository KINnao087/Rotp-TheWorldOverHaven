package com.inza.twoh.client.render.fx;

import com.inza.twoh.AddonMain;
import com.qouteall.immersive_portals.render.context_management.PortalRendering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.resources.IReloadableResourceManager;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.resource.ISelectiveResourceReloadListener;
import net.minecraftforge.resource.VanillaResourceType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** EN: Client-thread owner of world-scoped effects and the pre-HUD render queue.
 *  中文：在客户端线程管理当前世界的特效实例，并在 HUD 之前执行绘制队列。 */
@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, value = Dist.CLIENT)
public final class EffectRenderer {
    private static final Map<RegistryKey<World>, List<Supplier<? extends ClientEffect>>> AMBIENT = new LinkedHashMap<>();
    private static final List<ClientEffect> active = new ArrayList<>();
    private static final List<ClientEffect> pending = new ArrayList<>();
    private static final EffectQueue queue = new EffectQueue();
    private static ClientWorld owner;
    private static EffectFrame frame;
    private static EffectFrame portalFrame;
    private static final EffectQueue portalQueue = new EffectQueue();
    private static final Map<ClientWorld, List<ClientEffect>> portalAmbient = new WeakHashMap<>();
    private static long startTick = Long.MIN_VALUE;

    private EffectRenderer() {
    }

    public static void registerReloadListener() {
        IReloadableResourceManager resources = (IReloadableResourceManager) Minecraft.getInstance().getResourceManager();
        ISelectiveResourceReloadListener listener = (manager, types) -> {
            if (types.test(VanillaResourceType.SHADERS)) {
                ShaderProgram.invalidateResources();
            }
        };
        resources.registerReloadListener(listener);
    }

    public static void registerDimension(RegistryKey<World> dimension, Supplier<? extends ClientEffect> factory) {
        AMBIENT.computeIfAbsent(dimension, key -> new ArrayList<>()).add(factory);
    }

    // EN: Call from the client thread. On rejection the caller still owns the instance.
    // 中文：仅在客户端线程调用；若返回 false，实例仍由调用方负责释放。
    public static boolean add(ClientWorld world, ClientEffect effect) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || world == null || world != mc.level) {
            return false;
        }
        select(world);
        pending.add(effect);
        return true;
    }

    private static void select(ClientWorld world) {
        if (owner == world) {
            return;
        }
        for (ClientEffect effect : active) {
            effect.close();
        }
        for (ClientEffect effect : pending) {
            effect.close();
        }
        active.clear();
        pending.clear();
        queue.releaseWorld();
        frame = null;
        owner = world;
        if (world != null) {
            List<Supplier<? extends ClientEffect>> factories = AMBIENT.get(world.dimension());
            if (factories != null) {
                for (Supplier<? extends ClientEffect> factory : factories) {
                    pending.add(factory.get());
                }
            }
        }
    }

    private static void activatePending() {
        if (!pending.isEmpty()) {
            active.addAll(pending);
            pending.clear();
        }
    }

    public static void beginFrame(ActiveRenderInfo camera, float partialTick, Matrix4f view, Matrix4f projection) {
        if (PortalRendering.isRendering()) {
            portalFrame = new EffectFrame(Minecraft.getInstance().level,camera,partialTick,view,projection);
            return;
        }
        select(Minecraft.getInstance().level);
        frame = owner == null ? null : new EffectFrame(owner, camera, partialTick, view, projection);
    }

    public static EffectFrame frame() {
        return PortalRendering.isRendering() ? portalFrame : frame;
    }

    // EN: Render the peer dimension's ambient effects in its own camera pass, without switching owners.
    // 中文：在彼端自己的镜头下画维度环境特效，不切走主世界的实例和渲染队列。
    public static void finishPortalFrame() {
        if (!PortalRendering.isRendering()
                || portalFrame == null) {
            return;
        }
        EffectFrame remote = portalFrame;
        try {
            List<Supplier<? extends ClientEffect>> factories = AMBIENT.get(remote.world.dimension());
            if (factories == null) {
                return;
            }
            List<ClientEffect> effects = portalAmbient.computeIfAbsent(remote.world, world -> {
                List<ClientEffect> result = new ArrayList<>();
                for (Supplier<? extends ClientEffect> factory : factories) {
                    result.add(factory.get());
                }
                return result;
            });
            for (ClientEffect effect : effects) {
                effect.submit(remote, portalQueue);
            }
            portalQueue.flush(remote);
        } finally {
            portalQueue.clearTasks();
            portalFrame = null;
        }
    }

    static float seconds(long tick, float partialTick) {
        if (startTick == Long.MIN_VALUE || tick < startTick) {
            startTick = tick;
        }
        return (tick - startTick + partialTick) * 0.05F;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            select(mc.level);
            if (owner != null && !mc.isPaused()) {
                activatePending();
                active.removeIf(effect -> {
                    if (effect.tick()) {
                        return false;
                    }
                    effect.close();
                    return true;
                });
            }
        }
    }

    @SubscribeEvent
    public static void render(RenderWorldLastEvent event) {
        if (PortalRendering.isRendering()) {
            return;
        }
        if (frame == null || frame.world != Minecraft.getInstance().level) {
            return;
        }
        activatePending();
        try {
            for (ClientEffect effect : active) {
                effect.submit(frame, queue);
            }
            queue.flush(frame);
        } finally {
            queue.clearTasks();
        }
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload event) {
        if (event.getWorld() instanceof ClientWorld) {
            List<ClientEffect> remote = portalAmbient.remove(event.getWorld());
            if (remote != null) {
                remote.forEach(ClientEffect::close);
            }
        }
        if (event.getWorld() instanceof ClientWorld && event.getWorld() == owner) {
            RiftSceneColor.release(owner);
            select(null);
        }
    }
}

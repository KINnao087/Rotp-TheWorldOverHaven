package com.inza.twoh.client.render.ascension;

import com.inza.twoh.AddonMain;
import com.inza.twoh.client.render.fx.EffectFrame;
import com.inza.twoh.client.render.fx.EffectRenderer;
import com.inza.twoh.init.InitDimension;
import com.inza.twoh.portalcore.immersive_portals.render.context_management.PortalRendering;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Only server-selected passengers run this pre-hand, pre-HUD transition. Persists across the intended level swap. */
@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID, value = Dist.CLIENT)
public final class AscensionTransition {
    private static final int VOID_TICK=116, END_TICK=191;
    private static Session session;
    private AscensionTransition() {}

    public static void start(UUID cast, ResourceLocation source, ResourceLocation destination, Vector3d origin, long startGameTick) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null || !source.equals(mc.level.dimension().location())) return;
        if(session!=null && session.id.equals(cast)) return;
        int elapsed=(int)Math.max(0L, Math.min(VOID_TICK, mc.level.getGameTime()-startGameTick));
        // The nebula is sampled in Heaven coordinates: mapped on entry, already local on return.
        double scale = mc.level.dimensionType().coordinateScale();
        Vector3d nebulaOrigin=source.equals(InitDimension.OVER_HEAVEN.location()) ? origin
                : new Vector3d(origin.x*scale,1.,origin.z*scale);
        session=new Session(cast,source,destination,origin,nebulaOrigin,elapsed);
        AxisAlignedBB range=new AxisAlignedBB(origin,origin).inflate(8.);
        for(LivingEntity actor:mc.level.getEntitiesOfClass(LivingEntity.class,range)) {
            if(actor.distanceToSqr(origin)<=64.) session.passengers.add(actor.getUUID());
        }
        session.passengers.add(mc.player.getUUID());
    }

    public static boolean covered(UUID cast) {
        return session!=null && session.id.equals(cast) && session.covered;
    }
    public static void cancel(UUID cast) {
        if(session!=null && session.id.equals(cast)) clear();
    }

    public static IRenderTypeBuffer passengerBuffer(Entity entity, IRenderTypeBuffer vanilla) {
        Session s=session;
        if(s==null || s.ticks<65 || PortalRendering.isRendering() ||
                entity.level!=Minecraft.getInstance().level || !s.passengers.contains(entity.getUUID())) return vanilla;
        return s.geometry.wrap(vanilla);
    }

    // Do not clear on WorldEvent.Unload: that event can be dispatched for the source during crossing.
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggedOutEvent event) { clear(); }

    private static void clear() {
        session=null;
        AscensionComposite.release();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || session==null) return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || mc.isPaused()) return;
        Session s=session;
        if(mc.player==null || !mc.player.isAlive() || ++s.watchdog>1200){clear();return;}
        boolean target=s.destination.equals(mc.level.dimension().location());
        if(!target && (!mc.level.dimension().location().equals(s.source) || s.arrived)){clear();return;}
        if(target && !s.arrived) {
            s.ticks=VOID_TICK;
            s.arrived=true;
        }
        int cx=mc.player.blockPosition().getX()>>4,cz=mc.player.blockPosition().getZ()>>4;
        boolean loaded=target && mc.level.getChunkSource().getChunkNow(cx,cz)!=null;
        if(s.ticks<VOID_TICK || loaded) ++s.ticks;
        if(s.ticks>=END_TICK) clear();
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void draw(RenderWorldLastEvent event) {
        if(session==null || PortalRendering.isRendering()) return;
        Session pass=session;
        try { composite(); }
        finally { pass.geometry.flush(); }
    }

    private static void composite() {
        Minecraft mc=Minecraft.getInstance();
        EffectFrame frame=EffectRenderer.frame();
        if(mc.player==null || frame==null || frame.world!=mc.level) return;
        Session s=session;
        boolean target=s.destination.equals(frame.world.dimension().location());
        if(!target && !frame.world.dimension().location().equals(s.source)) return;
        float t=Math.min(END_TICK, s.ticks+frame.partialTick)/20.F;
        if(t<.45F) return;
        if(target && s.arrivalOrigin==null) {
            // The camera cached on the dimension-change tick can still be hundreds of blocks
            // above this landing point. Anchor only against the first rendered target view.
            s.cameraCorrection=s.lastVirtualEye==null ? Vector3d.ZERO : s.lastVirtualEye.subtract(frame.eye);
            s.arrivalOrigin=s.lastSourceEye==null ? mc.player.position() : frame.eye.subtract(s.lastSourceEye.subtract(s.origin));
        }
        // Also cover the very first target frame if the teleport packet lands between tick and render.
        if(!target || !s.arrived) t=Math.min(t,VOID_TICK/20.F);
        Vector3d center=target && s.arrivalOrigin!=null ? s.arrivalOrigin : s.origin;
        Vector3d virtualEye;
        if(!target) {
            s.lastSourceEye=frame.eye;
            virtualEye=s.predicted.add(frame.eye.subtract(s.origin));
            s.lastVirtualEye=virtualEye;
        } else if(!s.arrived && s.lastVirtualEye!=null) {
            virtualEye=s.lastVirtualEye;
        } else {
            float u=MathHelper.clamp((t-6.08F)/2.62F,0.F,1.F);
            virtualEye=frame.eye.add(s.cameraCorrection.scale(1.-u*u*(3.-2.*u)));
        }
        boolean returning=!InitDimension.OVER_HEAVEN.location().equals(s.destination);
        if(AscensionComposite.render(frame,t,target,returning,center,virtualEye) && !target && t>=5.63F) s.covered=true;
    }

    private static final class Session {
        final UUID id;
        final ResourceLocation source,destination;
        final Vector3d origin,predicted;
        final Set<UUID> passengers=new HashSet<>();
        final PassengerBuffers geometry=new PassengerBuffers();
        int ticks,watchdog;
        boolean covered,arrived;
        Vector3d lastSourceEye,lastVirtualEye,arrivalOrigin;
        Vector3d cameraCorrection=Vector3d.ZERO;
        Session(UUID id, ResourceLocation source, ResourceLocation destination, Vector3d origin, Vector3d predicted, int elapsed) {
            this.id=id;this.source=source;this.destination=destination;this.origin=origin;this.predicted=predicted;this.ticks=elapsed;
        }
    }
}

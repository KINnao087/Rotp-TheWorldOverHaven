package com.inza.twoh.ascension;

import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.inza.twoh.AddonMain;
import com.inza.twoh.init.InitDimension;
import com.inza.twoh.network.AscensionNetwork;
import com.inza.twoh.portalcore.immersive_portals.Global;
import com.inza.twoh.portalcore.immersive_portals.api.PortalAPI;
import com.inza.twoh.portalcore.immersive_portals.chunk_loading.ChunkLoader;
import com.inza.twoh.portalcore.immersive_portals.chunk_loading.DimensionalChunkPos;
import com.inza.twoh.rift.RiftTravel;
import com.inza.twoh.travel.PortalLandingSite;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.particles.RedstoneParticleData;
import net.minecraft.util.Util;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.server.TicketType;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A cast locks its targets once. Only the authoritative server decides who crosses. */
@Mod.EventBusSubscriber(modid = AddonMain.MOD_ID)
public final class HeavenAscension {
    public static final int RADIUS = 8;
    private static final int CUTOVER = 116; // 5.8 s, inside the demo's fully exposed void.
    private static final int RELEASE_AFTER = 76; // let normal chunk tracking take over during the reveal
    private static final int TIMEOUT = 400;
    private static final int LOAD_RADIUS = 2;
    private static final int EMBER_TARGET_BUDGET = 12;
    private static final int SEARCH_COLUMNS_PER_TICK = 16;
    private static final RedstoneParticleData EMBER = new RedstoneParticleData(204/255f,48/255f,226/255f,.8f);
    private static final TicketType<UUID> TICKET =
            TicketType.create("twoh_ascension", UUID::compareTo, TIMEOUT + RELEASE_AFTER + 20);
    private static final Map<UUID, Passage> ACTIVE = new HashMap<>();

    private HeavenAscension() {}

    public static void cast(ServerWorld source, LivingEntity caster, boolean follow) {
        ServerWorld heaven = source.getServer().getLevel(InitDimension.OVER_HEAVEN);
        if (heaven == null) {
            tell(caster, "rift.twohaddon.no_destination");
            return;
        }
        for (Passage other : ACTIVE.values()) {
            if (other.owner.equals(caster.getUUID()) && other.hasPending()) return;
        }
        Vector3d origin = caster.position();
        boolean returning = source == heaven;
        Vector3d mappedOrigin = returning ? origin : RiftTravel.corresponding(source, heaven, origin);
        Map<DimensionalChunkPos, Passage> groups = new HashMap<>();
        String skipped = "ascension.twohaddon.empty";
        String casterFailure = null;
        AxisAlignedBB area = new AxisAlignedBB(origin, origin).inflate(RADIUS);
        for (LivingEntity e : source.getEntitiesOfClass(LivingEntity.class, area)) {
            if ((e == caster && !returning && !follow) || !e.isAlive() || e.isSpectator() || !e.canChangeDimensions()
                    || e instanceof StandEntity || e.distanceToSqr(origin) > RADIUS * RADIUS
                    || ACTIVE.values().stream().anyMatch(other -> other.pending(e))) continue;
            ReturnAnchor anchor = returning ? ReturnAnchor.read(e) : null;
            ServerWorld target = heaven;
            Vector3d hint = mappedOrigin.add(e.position().subtract(origin));
            String failure = null;
            if (returning) {
                if (anchor == null) failure = "ascension.twohaddon.no_return";
                else {
                    target = source.getServer().getLevel(anchor.dimension);
                    hint = anchor.feet;
                    if (target == null) failure = "rift.twohaddon.no_destination";
                }
            }
            if (failure != null) {
                skipped = failure;
                if (e == caster) casterFailure = failure;
                continue;
            }
            // Different travelers may have come from different dimensions, or distant chunks in one dimension.
            hint = AscensionLanding.insideBorder(target, hint);
            // The remote client acknowledges this chunk and its eight neighbors. This covers
            // all possible feet positions in the 16-block fallback, even at a chunk corner.
            DimensionalChunkPos route = new DimensionalChunkPos(target.dimension(), new ChunkPos(new BlockPos(hint)));
            Passage p = groups.get(route);
            if (p == null) {
                p = new Passage(source, target, caster, route);
                groups.put(route, p);
            }
            p.members.add(new Traveler(e, hint, anchor));
        }
        if (groups.isEmpty()) {
            tell(caster, casterFailure != null ? casterFailure : skipped);
            return;
        }
        if (casterFailure != null) tell(caster, casterFailure);
        for (Passage p : groups.values()) {
            ACTIVE.put(p.id, p);
            p.target.getChunkSource().addRegionTicket(TICKET, p.center, LOAD_RADIUS, p.id);
            for (Traveler m : p.members) {
                if (m.player != null) {
                    PortalAPI.addChunkLoaderForPlayer(m.player, p.loader);
                    AscensionNetwork.start(m.player, p.id, source.dimension().location(),
                            p.target.dimension().location(), origin, p.start, p.center);
                }
            }
        }
    }

    /** Acknowledges receipt of this cast's destination chunks; cannot add or teleport a target. */
    public static void ready(ServerPlayerEntity sender, UUID id) {
        Passage p = ACTIVE.get(id);
        if (p != null && sender.level == p.source) {
            for (Traveler m : p.members) if (m.player == sender && !m.done) m.ready = true;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.world instanceof ServerWorld)) return;
        Iterator<Passage> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Passage p = it.next();
            if (p.source == ev.world && p.advance()) {
                p.close();
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload ev) {
        if (!(ev.getWorld() instanceof ServerWorld)) return;
        Iterator<Passage> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Passage p = it.next();
            if (p.source == ev.getWorld() || p.target == ev.getWorld()) {
                for (Traveler m : p.members) if (!m.done) p.cancel(m, null);
                p.close();
                it.remove();
            }
        }
    }

    private static void tell(LivingEntity entity, String key) {
        if (entity instanceof ServerPlayerEntity)
            entity.sendMessage(new TranslationTextComponent(key), Util.NIL_UUID);
    }

    private static final class Traveler {
        final UUID id;
        final Vector3d hint;
        @Nullable final ReturnAnchor returnTo;
        @Nullable final ServerPlayerEntity player;
        @Nullable PortalLandingSite.Search search;
        int checkedSites;
        boolean ready, done;
        Traveler(LivingEntity e, Vector3d hint, @Nullable ReturnAnchor returnTo) {
            id = e.getUUID();
            this.hint = hint;
            this.returnTo = returnTo;
            player = e instanceof ServerPlayerEntity ? (ServerPlayerEntity) e : null;
            ready = player == null;
        }
    }

    private static final class Passage {
        final UUID id = UUID.randomUUID();
        final UUID owner;
        final ServerWorld source, target;
        final long start;
        final ChunkPos center;
        final ChunkLoader loader;
        final List<Traveler> members = new ArrayList<>();
        final List<Vector3d> fallbackSites = new ArrayList<>();
        long lastArrival = Long.MIN_VALUE;
        Passage(ServerWorld source, ServerWorld target, LivingEntity owner, DimensionalChunkPos route) {
            this.source = source;
            this.target = target;
            this.owner = owner.getUUID();
            start = source.getGameTime();
            center = route.getChunkPos();
            loader = new ChunkLoader(route, LOAD_RADIUS);
        }
        boolean hasPending() { return members.stream().anyMatch(m -> !m.done); }
        boolean pending(Entity e) { return members.stream().anyMatch(m -> !m.done && m.id.equals(e.getUUID())); }
        boolean advance() {
            long tick = source.getGameTime(), elapsed = tick - start;
            if (elapsed >= CUTOVER - 16 && elapsed <= CUTOVER && (elapsed - CUTOVER) % 4 == 0) {
                int remaining = EMBER_TARGET_BUDGET;
                for (Traveler m : members) {
                    Entity e = source.getEntity(m.id);
                    if (!m.done && e != null && e.isAlive()) {
                        source.sendParticles(EMBER,e.getX(),e.getY()+.5*e.getBbHeight(),e.getZ(),
                                4,.35*e.getBbWidth(),.4*e.getBbHeight(),.35*e.getBbWidth(),0);
                        if (--remaining == 0) break;
                    }
                }
            }
            if (elapsed < CUTOVER) return false;
            // Keep companions in the same shot when a client's remote view is slow to load.
            boolean waiting = false;
            for (Traveler m : members) {
                if (m.done) continue;
                Entity e = source.getEntity(m.id);
                if (!(e instanceof LivingEntity) || !e.isAlive() || (m.player != null && m.player != e)) {
                    cancel(m, null);
                }
                else if (!m.ready || !AscensionLanding.columnsLoaded(target, (LivingEntity)e, m.hint)) {
                    if (elapsed >= TIMEOUT) cancel(m, "ascension.twohaddon.not_ready");
                    else waiting = true;
                }
            }
            if (waiting) return false;
            int columnsLeft = SEARCH_COLUMNS_PER_TICK;
            for (Traveler m : members) {
                if (m.done) continue;
                Entity e = source.getEntity(m.id);
                if (!(e instanceof LivingEntity) || !e.isAlive()) {
                    cancel(m, null);
                    continue;
                }
                LivingEntity traveler = (LivingEntity)e;
                Vector3d feet = m.search == null
                        ? AscensionLanding.find(target, traveler, m.hint, m.returnTo != null) : null;
                // An earlier member may already have found a clearing or built a platform.
                // Validate another unoccupied spot on it, rather than building once per entity.
                while (feet == null && m.checkedSites < fallbackSites.size()) {
                    Vector3d site = fallbackSites.get(m.checkedSites++);
                    if (!withinSearch(m.hint, site) || !AscensionLanding.columnsLoaded(target, traveler, site)) continue;
                    Vector3d spare = AscensionLanding.find(target, traveler, site, true);
                    if (spare != null && withinSearch(m.hint, spare)) feet = spare;
                }
                boolean useSearch = feet == null;
                if (useSearch) {
                    if (elapsed >= TIMEOUT) {
                        cancel(m, "rift.twohaddon.no_landing");
                        continue;
                    }
                    if (m.search == null)
                        m.search = new PortalLandingSite.Search(target, m.hint, traveler, 0, pos -> true);
                    if (columnsLeft == 0) continue;
                    ChunkPos cold = m.search.waitingFor();
                    if (cold != null && target.getChunkSource().getChunkNow(cold.x, cold.z) == null) continue;
                    boolean complete = m.search.advance(columnsLeft);
                    columnsLeft = 0;
                    if (!complete) continue;
                    if (m.search.feet() == null) {
                        cancel(m, "rift.twohaddon.no_landing");
                        continue;
                    }
                }
                if (!e.canChangeDimensions() || !ForgeHooks.onTravelToDimension(e, target.dimension())) {
                    cancel(m, null);
                    continue;
                }
                if (useSearch) {
                    // The search is read-only until this point. Placing blocks is attributed to
                    // the caster, not the mob being transported. Never build for an absent caster.
                    Entity actor = source.getEntity(owner);
                    if (actor == null) actor = source.getServer().getPlayerList().getPlayer(owner);
                    if (actor == null) actor = target.getEntity(owner);
                    if (m.search.needsPlatform() && (actor == null || !actor.isAlive())) {
                        cancel(m, "rift.twohaddon.no_landing");
                        continue;
                    }
                    feet = m.search.commit(actor);
                    if (feet == null) {
                        cancel(m, "rift.twohaddon.no_landing");
                        continue;
                    }
                    fallbackSites.add(feet);
                }
                // Record the actual departure point, not where a moving passenger was when the animation began.
                ReturnAnchor departure = ReturnAnchor.at(e);
                e.stopRiding();
                e.ejectPassengers(); // do not drag an unselected rider or mount across dimensions
                Vector3d eye = feet.add(0, e.getEyeHeight(), 0);
                Entity arrived;
                if (m.player != null) {
                    Global.serverTeleportationManager.teleportPlayer(m.player, target.dimension(), eye);
                    arrived = m.player;
                } else {
                    arrived = Global.serverTeleportationManager.changeEntityDimension(e, target.dimension(), eye, true);
                }
                if (arrived == null || arrived.level != target || arrived.removed) {
                    cancel(m, null);
                    continue;
                }
                float yaw = m.returnTo != null ? m.returnTo.yaw : departure.yaw;
                float pitch = m.returnTo != null ? m.returnTo.pitch : departure.pitch;
                arrived.fallDistance = 0;
                arrived.setDeltaMovement(Vector3d.ZERO);
                if (m.player != null) {
                    // Both core world swaps take EYE; vanilla commits the validated FEET and final rotation.
                    AscensionNetwork.commit(m.player, id, eye);
                    m.player.connection.teleport(feet.x, feet.y, feet.z, yaw, pitch);
                } else {
                    // A recreated mob's eye height can differ from its departure pose. Pin the validated feet.
                    arrived.moveTo(feet.x, feet.y, feet.z, yaw, pitch);
                    if (m.returnTo != null) arrived.setYHeadRot(yaw);
                }
                if (m.returnTo != null) ReturnAnchor.clear(arrived);
                else departure.save(arrived); // write on the destination instance, not a discarded mob
                lastArrival = tick;
                m.done = true;
            }
            return !hasPending() && (lastArrival == Long.MIN_VALUE || tick - lastArrival >= RELEASE_AFTER);
        }
        void cancel(Traveler m, @Nullable String reason) {
            m.done = true;
            if (m.player != null && source.getServer().getPlayerList().getPlayer(m.id) == m.player) {
                AscensionNetwork.cancel(m.player, id);
                if (reason != null) tell(m.player, reason);
            }
        }
        void close() {
            for (Traveler m : members) if (m.player != null) PortalAPI.removeChunkLoaderForPlayer(m.player, loader);
            target.getChunkSource().removeRegionTicket(TICKET, center, LOAD_RADIUS, id);
        }
        private static boolean withinSearch(Vector3d hint, Vector3d feet) {
            return Math.abs(Math.floor(feet.x) - Math.floor(hint.x)) <= PortalLandingSite.RADIUS
                    && Math.abs(Math.floor(feet.z) - Math.floor(hint.z)) <= PortalLandingSite.RADIUS;
        }
    }
}

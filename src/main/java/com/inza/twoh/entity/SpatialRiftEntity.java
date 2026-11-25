package com.inza.twoh.entity;

import com.inza.twoh.AddonMain;
import com.inza.twoh.network.RiftDestinations;
import com.inza.twoh.init.InitEntities;
import com.inza.twoh.rift.RiftMesh;
import com.inza.twoh.rift.RiftMotion;
import com.inza.twoh.rift.RiftTravel.LandingSearch;
import com.inza.twoh.rift.RiftStroke;
import com.inza.twoh.rift.RiftWindows;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.server.TicketType;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.NetworkHooks;

import java.util.UUID;

/** EN: Stationary visual anchor. Vanilla tracking sends its position and both animation clocks to observers.
 *  中文：静止的特效锚点。原版实体追踪负责向观察者同步位置和开合时钟。 */
public final class SpatialRiftEntity extends Entity implements IEntityAdditionalSpawnData {
    private static final String RETURN_RIFT = "twohaddon.return_spatial_rift";
    private static final TicketType<UUID> TICKET = TicketType.create("twoh_spatial_rift", UUID::compareTo, 160);
    private RiftWindows windows;
    private RegistryKey<World> peerWorld;
    private UUID peerId;
    private LandingSearch landing;
    private Vector3d landingOrigin;
    private boolean released;
    private Vector3d safeFeet;
    private long seed;
    private RiftMesh shape;
    private RiftStroke stroke;
    private boolean draft;
    private long lastSample;
    private static final String DRAFT_RIFT = "twohaddon.draft_spatial_rift";
    private static final DataParameter<CompoundNBT> TRACE =
            EntityDataManager.defineId(SpatialRiftEntity.class, DataSerializers.COMPOUND_TAG);
    private static final String ACTIVE_RIFT = "twohaddon.active_spatial_rift";
    private static final DataParameter<Integer> OPENED =
            EntityDataManager.defineId(SpatialRiftEntity.class, DataSerializers.INT);
    private static final DataParameter<Integer> CLOSE_AT_AGE =
            EntityDataManager.defineId(SpatialRiftEntity.class, DataSerializers.INT);

    private UUID caster;
    private static final String CLOSE_INTENT = "twohaddon.close_rift_intent";

    public SpatialRiftEntity(EntityType<? extends SpatialRiftEntity> type, World world) {
        super(type, world);
        noPhysics = true;
        setNoGravity(true);
    }

    private static SpatialRiftEntity active(ServerWorld world, LivingEntity user) {
        CompoundNBT data = user.getPersistentData();
        for (String key : new String[] {ACTIVE_RIFT, RETURN_RIFT}) {
            if (!data.hasUUID(key)) {
                continue;
            }
            Entity old = world.getEntity(data.getUUID(key));
            if (old instanceof SpatialRiftEntity && old.isAlive()) {
                return (SpatialRiftEntity) old;
            }
        }
        return null;
    }

    private static SpatialRiftEntity draftOf(ServerWorld world, LivingEntity user) {
        CompoundNBT data = user.getPersistentData();
        Entity entity = data.hasUUID(DRAFT_RIFT) ? world.getEntity(data.getUUID(DRAFT_RIFT)) : null;
        return entity instanceof SpatialRiftEntity && ((SpatialRiftEntity) entity).draft && entity.isAlive()
                ? (SpatialRiftEntity) entity
                : null;
    }

    public static void startPen(ServerWorld world, LivingEntity user) {
        SpatialRiftEntity existing = active(world, user);
        if (existing != null) {
            user.getPersistentData().putUUID(CLOSE_INTENT, existing.getUUID());
            return;
        }
        user.getPersistentData().remove(CLOSE_INTENT);
        if (draftOf(world, user) != null) {
            return;
        }
        ServerWorld destination = world.getServer().getLevel(RiftDestinations.selected(user));
        if (destination == null) {
            user.sendMessage(new TranslationTextComponent("rift.twohaddon.no_destination"), user.getUUID());
            return;
        }
        RiftStroke pen = RiftStroke.begin(user);
        SpatialRiftEntity draft = new SpatialRiftEntity(InitEntities.SPATIAL_RIFT.get(), world);
        draft.draft = true;
        draft.stroke = pen;
        draft.entityData.set(TRACE, pen.pack());
        draft.landingOrigin = user.position();
        draft.landing = new LandingSearch(world, destination, draft.landingOrigin, draft.getUUID(), user, pen.plane.inward);
        draft.peerWorld = destination.dimension();
        draft.caster = user.getUUID();
        draft.lastSample = world.getGameTime();
        draft.moveTo(pen.origin.x, pen.origin.y, pen.origin.z, pen.yaw, 0);
        if (world.addFreshEntity(draft)) {
            user.getPersistentData().putUUID(DRAFT_RIFT, draft.getUUID());
            draft.advanceLanding(world, user);
        }
    }

    public static void samplePen(ServerWorld world, LivingEntity user) {
        SpatialRiftEntity draft = draftOf(world, user);
        if (draft != null && !draft.released) {
            draft.lastSample = world.getGameTime();
            if (draft.stroke.sample(user)) {
                draft.entityData.set(TRACE, draft.stroke.pack());
                if (draft.windows != null) {
                    draft.windows.setExit(draft.remoteOrigin(user));
                    draft.windows.preview(draft.stroke);
                }
            }
        }
    }

    public static void releasePen(ServerWorld world, LivingEntity user, boolean commit) {
        SpatialRiftEntity draft = draftOf(world, user);
        if (draft == null) {
            SpatialRiftEntity existing = active(world, user);
            boolean closing = user.getPersistentData().hasUUID(CLOSE_INTENT) && existing != null
                    && existing.getUUID().equals(user.getPersistentData().getUUID(CLOSE_INTENT));
            user.getPersistentData().remove(CLOSE_INTENT);
            if (commit && closing) {
                existing.closePair();
            }
            return;
        }
        if (draft.released) {
            return;
        }
        draft.stroke.sample(user);
        if (!commit || draft.stroke.size() < 2) {
            draft.discardDraft(user);
            return;
        }
        ServerWorld destination = world.getServer().getLevel(draft.peerWorld);
        if (destination == null) {
            user.sendMessage(new TranslationTextComponent("rift.twohaddon.no_destination"), user.getUUID());
            draft.discardDraft(user);
            return;
        }
        draft.released = true;
        draft.entityData.set(TRACE, draft.stroke.pack());
        if (draft.safeFeet != null && !draft.landing.stillSafe()) {
            draft.landing.close();
            draft.landing = new LandingSearch(world, destination, draft.landingOrigin, draft.getUUID(), user, draft.stroke.plane.inward);
            draft.safeFeet = null;
            if (draft.windows != null) {
                draft.windows.close();
                draft.windows = null;
            }
        }
        draft.advanceLanding(world, user);
    }

    private Vector3d remoteOrigin(LivingEntity user) {
        return safeFeet.add(0, user.getEyeHeight(), 0).subtract(stroke.midpointOffset());
    }

    private void discardDraft(LivingEntity user) {
        user.getPersistentData().remove(DRAFT_RIFT);
        remove();
    }

    // EN: A quick release keeps its final stroke while terrain loads. Never block the server or lose the gesture.
    // 中文：松手时目标区块若未就绪，保留最后一笔，等地形就绪再开门，不阻塞服务器。
    private void advanceLanding(ServerWorld world, LivingEntity user) {
        if (!landing.poll(world.getGameTime())) {
            return;
        }
        safeFeet = landing.feet();
        ServerWorld destination = world.getServer().getLevel(peerWorld);
        if (destination == null || safeFeet == null) {
            String key = destination == null ? "rift.twohaddon.no_destination" : landing.timedOut()
                    ? "rift.twohaddon.load_timeout" : "rift.twohaddon.no_landing";
            user.sendMessage(new TranslationTextComponent(key), user.getUUID());
            discardDraft(user);
        } else if (released) {
            try {
                Vector3d arrival = landing.commit(user);
                if (arrival != null) open(world, user, stroke, destination, arrival);
                else user.sendMessage(new TranslationTextComponent("rift.twohaddon.no_landing"), user.getUUID());
            } finally {
                discardDraft(user);
            }
        } else if (windows == null) {
            windows = new RiftWindows(this, destination, remoteOrigin(user));
            windows.preview(stroke);
        }
    }

    private static void open(ServerWorld world, LivingEntity user, RiftStroke pen, ServerWorld destination, Vector3d arrival) {
        CompoundNBT data = user.getPersistentData();
        SpatialRiftEntity rift = new SpatialRiftEntity(InitEntities.SPATIAL_RIFT.get(), world);
        SpatialRiftEntity back = new SpatialRiftEntity(InitEntities.SPATIAL_RIFT.get(), destination);
        Vector3d point = pen.origin;
        Vector3d remote = arrival.add(0, user.getEyeHeight(), 0).subtract(pen.midpointOffset());
        rift.moveTo(point.x, point.y, point.z, pen.yaw, 0);
        back.moveTo(remote.x, remote.y, remote.z, pen.yaw, 0);
        rift.stroke = back.stroke = pen;
        rift.seed = back.seed = rift.getUUID().getLeastSignificantBits();
        rift.shape = back.shape = new RiftMesh(pen, rift.seed);
        rift.link(back, user);
        back.link(rift, user);
        rift.ticket(true);
        back.ticket(true);
        rift.forcedLoading = back.forcedLoading = true;
        boolean sourceAdded = world.addFreshEntity(rift);
        boolean exitAdded = sourceAdded && destination.addFreshEntity(back);
        if (exitAdded) {
            rift.windows.animate(rift.shape, 0, -1);
            back.windows.animate(back.shape, 0, -1);
            data.putUUID(ACTIVE_RIFT, rift.getUUID());
            data.putUUID(RETURN_RIFT, back.getUUID());
            AddonMain.LOGGER.debug(
                    "Opened spatial rift pair {} -> {}", rift.getUUID(), back.getUUID());
        } else {
            AddonMain.LOGGER.warn("Could not attach spatial rift: source={}, exit={}", sourceAdded, exitAdded);
            rift.ticket(false);
            back.ticket(false);
            rift.remove();
            back.remove();
        }
    }

    private void link(SpatialRiftEntity peer, LivingEntity user) {
        caster = user.getUUID();
        peerWorld = peer.level.dimension();
        peerId = peer.getUUID();
        entityData.set(OPENED, (int) level.getGameTime());
        windows = new RiftWindows(this, (ServerWorld) peer.level, peer.position());
    }

    private SpatialRiftEntity peer() {
        if (peerWorld == null) {
            return null;
        }
        ServerWorld target = ((ServerWorld) level).getServer().getLevel(peerWorld);
        Entity other = target == null ? null : target.getEntity(peerId);
        return other instanceof SpatialRiftEntity && other.isAlive() ? (SpatialRiftEntity) other : null;
    }

    private void ticket(boolean keep) {
        ServerWorld world = (ServerWorld) level;
        ChunkPos pos = new ChunkPos(blockPosition());
        if (keep) {
            world.getChunkSource().addRegionTicket(TICKET, pos, 2, getUUID());
        }
        else {
            world.getChunkSource().removeRegionTicket(TICKET, pos, 2, getUUID());
        }
    }

    public boolean isDraft() {
        return draft;
    }
    public CompoundNBT draftTrace() {
        return entityData.get(TRACE);
    }
    public RiftStroke stroke() {
        return stroke;
    }

    public RiftMesh geometry() {
        return shape;
    }

    public long layoutSeed() {
        return seed;
    }

    @Override
    public void writeSpawnData(PacketBuffer buffer) {
        buffer.writeLong(seed);
        buffer.writeBoolean(draft);
        buffer.writeFloat(yRot);
        buffer.writeNbt(stroke.pack());
    }

    @Override
    public void readSpawnData(PacketBuffer buffer) {
        seed = buffer.readLong();
        draft = buffer.readBoolean();
        yRot = buffer.readFloat();
        CompoundNBT trace = buffer.readNbt();
        stroke = RiftStroke.unpack(position(), yRot, trace);
        if (draft) {
            entityData.set(TRACE, trace);
        }
        else {
            shape = new RiftMesh(stroke, seed);
        }
    }

    private void closePair() {
        beginClosing();
        SpatialRiftEntity other = peer();
        if (other != null) {
            other.beginClosing();
        }
    }

    private int ageTicks() {
        return Math.max(0, (int) level.getGameTime() - entityData.get(OPENED));
    }

    public float ageSeconds(float partial) {
        return (ageTicks() + partial) / 20.0F;
    }

    public int closeAtAge() {
        return entityData.get(CLOSE_AT_AGE);
    }

    private void beginClosing() {
        if (closeAtAge() < 0) {
            AddonMain.LOGGER.debug("Closing spatial rift {} at age {}", getUUID(), ageTicks());
            entityData.set(CLOSE_AT_AGE, ageTicks());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level instanceof ServerWorld) {
            ServerWorld world = (ServerWorld) level;
            ServerPlayerEntity owner = caster == null ? null : world.getServer().getPlayerList().getPlayer(caster);
            if (draft) {
                if (owner == null || owner.level != world || !owner.isAlive()
                        || (!released && world.getGameTime() - lastSample > 12)) {
                    remove();
                } else if (released || safeFeet == null) {
                    advanceLanding(world, owner);
                }
                return;
            }
            SpatialRiftEntity other = peer();
            if (owner == null || !owner.isAlive() || other == null
                    || (!owner.level.dimension().equals(level.dimension())
                            && !owner.level.dimension().equals(peerWorld))) {
                closePair();
            }
            if (other != null && closeAtAge() < 0) {
                if (tickCount % 40 == 0) {
                    ticket(true);
                }
            }
            if (windows != null) {
                windows.animate(shape, ageSeconds(0), closeAtAge());
            }
            if (closeAtAge() >= 0 && ageTicks() - closeAtAge() >= RiftMotion.CLOSE_LIFETIME) {
                if (owner != null && owner.getPersistentData().hasUUID(ACTIVE_RIFT)
                        && getUUID().equals(owner.getPersistentData().getUUID(ACTIVE_RIFT))) {
                    owner.getPersistentData().remove(ACTIVE_RIFT);
                }
                if (owner != null && owner.getPersistentData().hasUUID(RETURN_RIFT)
                        && getUUID().equals(owner.getPersistentData().getUUID(RETURN_RIFT))) {
                    owner.getPersistentData().remove(RETURN_RIFT);
                }
                ticket(false);
                remove();
            }
        }
    }

    @Override
    public void remove() {
        if (landing != null) {
            landing.close();
        }
        if (windows != null) {
            windows.close();
        }
        super.remove();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TRACE, new CompoundNBT());
        entityData.define(OPENED, 0);
        entityData.define(CLOSE_AT_AGE, -1);
    }

    // EN: Endpoints are transient; a short-lived ticket keeps the paired exit ticking only while needed.
    // 中文：裂隙不写入存档；仅在开启期间用短期区块票保持两端运转。
    @Override
    protected void readAdditionalSaveData(CompoundNBT tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundNBT tag) {}

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance <= 128 * 128;
    }

    @Override
    public AxisAlignedBB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(RiftStroke.MAX_STEPS * RiftStroke.STEP + 9.0);
    }

    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

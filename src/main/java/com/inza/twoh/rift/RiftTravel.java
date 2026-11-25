package com.inza.twoh.rift;

import com.inza.twoh.AddonMain;
import com.inza.twoh.entity.SpatialRiftEntity;
import com.inza.twoh.travel.PortalLandingSite;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Pose;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.server.TicketType;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** EN: Find safe portal anchors before opening. Immersive Portals owns crossing.
 *  中文：开启前检查锚点周围的安全空间；穿越本身交由 Immersive Portals。 */
public final class RiftTravel {
    private static final int SEARCH_RADIUS = 16;
    private static final int SAME_WORLD_CLEARANCE = 12;

    private RiftTravel() {}

    // EN: Scale horizontal coordinates only. Terrain determines a usable exit height.
    // 中文：只缩放水平坐标，出口高度由目标地形决定。
    public static Vector3d corresponding(ServerWorld source, ServerWorld target, Vector3d position) {
        double scale = DimensionType.getTeleportationScale(source.dimensionType(), target.dimensionType());
        return new Vector3d(position.x * scale, position.y, position.z * scale);
    }

    @Nullable
    public static Vector3d findExit(ServerWorld source, ServerWorld target, Vector3d position) {
        Vector3d mapped = corresponding(source, target, position);
        return nearestGround(target, mapped, source == target ? SAME_WORLD_CLEARANCE : 0);
    }

    @Nullable
    public static Vector3d nearestGround(ServerWorld world, Vector3d near, int minimumDistance) {
        BlockPos center = new BlockPos(near);
        Columns columns = new Columns(center, minimumDistance);
        while (columns.hasNext()) {
            BlockPos column = columns.current();
            if (world.getWorldBorder().isWithinBounds(column)) {
                BlockPos landing = searchColumn(world, column, world.getChunkAt(column));
                if (landing != null) {
                    return Vector3d.atBottomCenterOf(landing);
                }
            }
            columns.advance();
        }
        AddonMain.LOGGER.warn("No supported, dry, unobstructed rift exit in {} near {} (search radius {})",
                world.dimension().location(), center, SEARCH_RADIUS);
        return null;
    }

    /** Ring cursor for the legacy synchronous administrative lookup. */
    private static final class Columns {
        final BlockPos center;
        int ring, dx, dz;

        Columns(BlockPos center, int firstRing) {
            this.center = center;
            ring = firstRing;
            dx = dz = -ring;
        }

        boolean hasNext() {
            return ring <= SEARCH_RADIUS;
        }

        BlockPos current() {
            return center.offset(dx, 0, dz);
        }

        void advance() {
            dz += Math.abs(dx) == ring ? 1 : 2 * ring;
            if (dz > ring) {
                dz = -ring;
                if (++dx > ring) {
                    ring++;
                    dx = dz = -ring;
                }
            }
        }
    }

    /** EN: Only the server tick reads blocks. Cold columns request a temporary FULL chunk ticket and yield.
     *  中文：仅在服务端 tick 读方块；遇到冷区块时提交临时加载票，让出当前 tick。 */
    public static final class LandingSearch implements AutoCloseable {
        private static final int COLUMNS_PER_TICK = 8;
        private static final int TIMEOUT_TICKS = 600;
        private static final TicketType<UUID> LOAD = TicketType.create("twoh_rift_landing", UUID::compareTo, TIMEOUT_TICKS + 20);
        private final ServerWorld target;
        private final PortalLandingSite.Search search;
        private final UUID owner;
        private final Set<ChunkPos> tickets = new HashSet<>();
        private final long deadline;
        private long lastTick = Long.MIN_VALUE;
        private boolean finished;
        private boolean timedOut;

        public LandingSearch(ServerWorld source, ServerWorld target, Vector3d near, UUID owner,
                             LivingEntity caster, Vector3d normal) {
            this.target = target;
            this.owner = owner;
            search = new PortalLandingSite.Search(target, corresponding(source, target, near), caster,
                    source == target ? SAME_WORLD_CLEARANCE : 0, feet -> clearApproach(target, caster, normal, feet))
                    .requireNaturalSupport(feet -> supportedExit(target, caster, feet.add(normal.scale(.75)))
                            && supportedExit(target, caster, feet.subtract(normal.scale(.75))));
            deadline = source.getGameTime() + TIMEOUT_TICKS;
        }

        public boolean poll(long tick) {
            if (finished || lastTick == tick) {
                return finished;
            }
            lastTick = tick;
            if (tick >= deadline) {
                timedOut = true;
                finished = true;
                return true;
            }
            finished = search.advance(COLUMNS_PER_TICK);
            ChunkPos missing = search.waitingFor();
            if (!finished && missing != null && tickets.add(missing)) {
                target.getChunkSource().addRegionTicket(LOAD, missing, 0, owner);
            }
            return finished;
        }

        @Nullable
        public Vector3d feet() {
            return timedOut ? null : search.feet();
        }

        public boolean timedOut() {
            return timedOut;
        }

        public boolean stillSafe() {
            return !timedOut && search.stillValid();
        }

        /** A pen preview or cancelled stroke must never place blocks. */
        @Nullable
        public Vector3d commit(LivingEntity caster) {
            return finished && !timedOut ? search.commit(caster) : null;
        }

        @Override
        public void close() {
            for (ChunkPos pos : tickets) {
                target.getChunkSource().removeRegionTicket(LOAD, pos, 0, owner);
            }
            tickets.clear();
        }
    }

    /** Both faces of the vertical aperture are usable. Allow room for one step through its midpoint. */
    private static boolean clearApproach(ServerWorld world, LivingEntity caster, Vector3d normal, Vector3d feet) {
        AxisAlignedBB body = caster.getBoundingBox().move(feet.subtract(caster.position()))
                .minmax(caster.getDimensions(Pose.STANDING).makeBoundingBox(feet));
        Vector3d step = normal.scale(.75);
        AxisAlignedBB aisle = body.move(step).minmax(body.move(step.scale(-1)));
        if (aisle.minX < world.getWorldBorder().getMinX() || aisle.maxX > world.getWorldBorder().getMaxX()
                || aisle.minZ < world.getWorldBorder().getMinZ() || aisle.maxZ > world.getWorldBorder().getMaxZ()
                || !world.noCollision(aisle) || world.containsAnyLiquid(aisle)) return false;
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(aisle.minX, aisle.minY, aisle.minZ),
                new BlockPos(Math.nextDown(aisle.maxX), Math.nextDown(aisle.maxY), Math.nextDown(aisle.maxZ)))) {
            BlockState state = world.getBlockState(p);
            if (caster.getType().isBlockDangerous(state) || state.is(Blocks.NETHER_PORTAL)
                    || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY)) return false;
        }
        return world.getEntitiesOfClass(SpatialRiftEntity.class, aisle.inflate(8)).isEmpty();
    }

    private static boolean supportedExit(ServerWorld world, LivingEntity caster, Vector3d step) {
        final double contact = 1.E-5;
        BlockPos floor = new BlockPos(step.x, step.y - contact, step.z);
        if (caster.getType().isBlockDangerous(world.getBlockState(floor))) return false;
        AxisAlignedBB contactPatch = new AxisAlignedBB(step.x - contact, step.y - contact, step.z - contact,
                step.x + contact, step.y, step.z + contact);
        return world.getBlockCollisions(null, contactPatch).anyMatch(shape -> !shape.isEmpty());
    }

    @Nullable
    private static BlockPos searchColumn(ServerWorld world, BlockPos column, Chunk chunk) {
        int top = Math.min(world.getMaxBuildHeight(), world.dimensionType().logicalHeight()) - 2;
        if (!world.dimensionType().hasCeiling()) {
            top = Math.min(top, chunk.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    column.getX() & 15, column.getZ() & 15) + 1);
        }
        if (top < 1) {
            return null;
        }
        int start = Math.max(1, Math.min(top, column.getY()));
        int range = Math.max(start - 1, top - start);
        for (int distance = 0; distance <= range; distance++) {
            if (start - distance >= 1) {
                BlockPos below = new BlockPos(column.getX(), start - distance, column.getZ());
                if (safe(world, below)) {
                    return below;
                }
            }
            if (distance > 0 && start + distance <= top) {
                BlockPos above = new BlockPos(column.getX(), start + distance, column.getZ());
                if (safe(world, above)) {
                    return above;
                }
            }
        }
        return null;
    }

    private static boolean safe(ServerWorld world, BlockPos p) {
        if (!world.getBlockState(p.below()).isFaceSturdy(world, p.below(), Direction.UP)
                || !world.getFluidState(p).isEmpty() || !world.getFluidState(p.above()).isEmpty()) {
            return false;
        }
        Vector3d feet = Vector3d.atBottomCenterOf(p);
        AxisAlignedBB room = new AxisAlignedBB(feet.x - .31, feet.y, feet.z - .31,
                feet.x + .31, feet.y + 1.85, feet.z + .31);
        return world.noCollision(room)
                && world.getEntitiesOfClass(SpatialRiftEntity.class, room.inflate(8)).isEmpty();
    }
}

package com.inza.twoh.rift;

import com.inza.twoh.AddonMain;
import com.inza.twoh.entity.SpatialRiftEntity;
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

    /** EN: Ring cursor shared by the synchronous admin command and the tick-budgeted skill.
     *  中文：管理命令和分 tick 查找的技能使用同一套逐圈搜索顺序。 */
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
        private final Columns columns;
        private final UUID owner;
        private final Set<ChunkPos> tickets = new HashSet<>();
        private final long deadline;
        private long lastTick = Long.MIN_VALUE;
        private Vector3d feet;
        private boolean finished;
        private boolean timedOut;

        public LandingSearch(ServerWorld source, ServerWorld target, Vector3d near, UUID owner) {
            this.target = target;
            this.owner = owner;
            columns = new Columns(new BlockPos(corresponding(source, target, near)), source == target ? SAME_WORLD_CLEARANCE : 0);
            deadline = source.getGameTime() + TIMEOUT_TICKS;
        }

        public boolean matches(ServerWorld source, Vector3d near) {
            return columns.center.equals(new BlockPos(corresponding(source, target, near)));
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
            for (int i = 0; columns.hasNext() && i < COLUMNS_PER_TICK; i++, columns.advance()) {
                BlockPos column = columns.current();
                if (!target.getWorldBorder().isWithinBounds(column)) {
                    continue;
                }
                ChunkPos pos = new ChunkPos(column);
                Chunk chunk = target.getChunkSource().getChunkNow(pos.x, pos.z);
                if (chunk == null) {
                    if (tickets.add(pos)) {
                        target.getChunkSource().addRegionTicket(LOAD, pos, 0, owner);
                    }
                    return false;
                }
                BlockPos landing = searchColumn(target, column, chunk);
                if (landing != null) {
                    feet = Vector3d.atBottomCenterOf(landing);
                    finished = true;
                    return true;
                }
            }
            finished = !columns.hasNext();
            return finished;
        }

        @Nullable
        public Vector3d feet() {
            return feet;
        }

        public boolean timedOut() {
            return timedOut;
        }

        public boolean stillSafe() {
            if (feet == null) {
                return false;
            }
            BlockPos p = new BlockPos(feet);
            ChunkPos chunk = new ChunkPos(p);
            return target.getWorldBorder().isWithinBounds(p) && target.getChunkSource().getChunkNow(chunk.x, chunk.z) != null
                    && safe(target, p);
        }

        @Override
        public void close() {
            for (ChunkPos pos : tickets) {
                target.getChunkSource().removeRegionTicket(LOAD, pos, 0, owner);
            }
            tickets.clear();
        }
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

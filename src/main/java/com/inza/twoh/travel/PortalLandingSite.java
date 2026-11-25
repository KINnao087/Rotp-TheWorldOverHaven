package com.inza.twoh.travel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;

/** A portal-style site search; selecting an exit and building it are separate server-thread operations. */
public final class PortalLandingSite {
    public static final int RADIUS = 16;
    private static final double EPS = 1.0e-5;
    private PortalLandingSite() {}

    public static final class Search {
        private final ServerWorld world;
        private final Vector3d hint;
        private final BlockPos center;
        private final LivingEntity traveler;
        private final int minimum;
        private final Predicate<Vector3d> permitted;
        private Predicate<Vector3d> naturalSupport = p -> true;
        private final Iterator<BlockPos.Mutable> spiral;
        private BlockPos column;
        private ChunkPos waiting;
        private Vector3d natural, platform;
        private double bestDistance = Double.POSITIVE_INFINITY;
        private boolean finished;

        public Search(ServerWorld target, Vector3d hint, @Nullable LivingEntity traveler,
                      int minimumDistance, Predicate<Vector3d> permitted) {
            this.world = target;
            this.hint = hint;
            this.center = new BlockPos(hint);
            this.traveler = traveler;
            this.minimum = minimumDistance;
            this.permitted = permitted;
            this.spiral = BlockPos.spiralAround(center, RADIUS, Direction.EAST, Direction.SOUTH).iterator();
        }

        public Search requireNaturalSupport(Predicate<Vector3d> check) {
            this.naturalSupport = check;
            return this;
        }

        /** Budget is columns, not ticks. A missing chunk never causes a synchronous load. */
        public boolean advance(int columnBudget) {
            if (finished) return true;
            AxisAlignedBB body = relativeBody();
            waiting = null;
            for (int i = 0; i < columnBudget; ++i) {
                if (column == null) {
                    if (!spiral.hasNext()) { finished = true; return true; }
                    column = spiral.next().immutable();
                }
                int ring = Math.max(Math.abs(column.getX()-center.getX()),Math.abs(column.getZ()-center.getZ()));
                // Later rings cannot improve an already discovered nearer natural site.
                if (natural != null && ring > 1 && (ring-1.)*(ring-1.) > bestDistance) {
                    finished = true;
                    return true;
                }
                if (ring >= minimum && world.getWorldBorder().isWithinBounds(column)) {
                    if (!ready(column, body)) return false;
                    scanColumn(column, body);
                }
                column = null;
            }
            if (!spiral.hasNext() && column == null) finished = true;
            return finished;
        }

        @Nullable public ChunkPos waitingFor() { return waiting; }
        @Nullable public Vector3d feet() { return !finished ? null : natural != null ? natural : platform; }
        public boolean needsPlatform() { return finished && natural == null && platform != null; }

        private AxisAlignedBB relativeBody() {
            if (traveler == null) return new AxisAlignedBB(-.31,0,-.31,.31,1.85,.31);
            AxisAlignedBB actual = traveler.getBoundingBox().move(-traveler.getX(),-traveler.getY(),-traveler.getZ());
            Pose pose = traveler instanceof PlayerEntity ? Pose.STANDING : traveler.getPose();
            return actual.minmax(traveler.getDimensions(pose).makeBoundingBox(0,0,0));
        }

        private boolean ready(BlockPos p, AxisAlignedBB b) {
            double x=p.getX()+.5, z=p.getZ()+.5;
            // Support/clearance on both sides of a rift and collision queries touching adjacent blocks.
            int x0=MathHelper.floor(x+b.minX-2)>>4, x1=MathHelper.floor(x+b.maxX+2)>>4;
            int z0=MathHelper.floor(z+b.minZ-2)>>4, z1=MathHelper.floor(z+b.maxZ+2)>>4;
            for(int cx=x0;cx<=x1;cx++) for(int cz=z0;cz<=z1;cz++)
                if (world.getChunkSource().getChunkNow(cx,cz)==null) {
                    waiting = new ChunkPos(cx,cz);
                    return false;
                }
            return true;
        }

        private void scanColumn(BlockPos p, AxisAlignedBB body) {
            // As with the vanilla portal forcer, inspect the full usable column, including interior caves.
            int ceiling = Math.min(world.getMaxBuildHeight(), world.dimensionType().logicalHeight());
            int top = Math.min(ceiling-1,world.getHeight(Heightmap.Type.WORLD_SURFACE,p.getX(),p.getZ())-1);
            BlockPos.Mutable floor = new BlockPos.Mutable();
            double bestHeight=Math.sqrt(bestDistance)+2;
            for (int y=top;y>=0;y--) {
                if (Math.abs(y-hint.y)>bestHeight) continue;
                floor.set(p.getX(),y,p.getZ());
                BlockState state = world.getBlockState(floor);
                if (state.isAir()) continue;
                BlockPos above=floor.above();
                if (world.getBlockState(above).isCollisionShapeFullBlock(world,above)) continue;
                VoxelShape shape = state.getCollisionShape(world,floor);
                if (shape.isEmpty()) continue;
                double surface=shape.max(Direction.Axis.Y,.5,.5);
                if (!Double.isFinite(surface)) continue;
                Vector3d pos=new Vector3d(p.getX()+.5,y+surface-body.minY,p.getZ()+.5);
                double cost=hint.distanceToSqr(pos);
                if (cost<bestDistance && naturalSafe(pos,body)) {
                    natural=pos;
                    bestDistance=cost;
                    bestHeight=Math.sqrt(cost)+2;
                }
            }
            if (natural!=null || platform!=null || Math.max(body.getXsize(),body.getZsize())>8) return;
            // Last resort: reserve an AIR-ONLY footprint at a sensible altitude. No portal blocks or frame.
            int high=ceiling-MathHelper.ceil(body.getYsize())-1;
            if (high<2) return;
            int base=MathHelper.clamp(MathHelper.floor(hint.y+body.minY), Math.min(70,high), high);
            for (int delta=0;delta<=Math.max(base-2,high-base);delta++) {
                if (base+delta<=high && prospective(p,base+delta,body)) break;
                if (delta>0 && base-delta>=2 && prospective(p,base-delta,body)) break;
            }
        }

        private boolean prospective(BlockPos p,int y,AxisAlignedBB body) {
            BlockPos foundation = new BlockPos(p.getX(),y-1,p.getZ());
            if (!air(foundation)) return false;
            Vector3d pos = new Vector3d(p.getX()+.5,y-body.minY,p.getZ()+.5);
            if (!buildable(pos,body)) return false;
            platform=pos;
            return true;
        }

        private boolean naturalSafe(Vector3d pos,AxisAlignedBB body) {
            AxisAlignedBB room=body.move(pos);
            double x=(room.minX+room.maxX)*.5,z=(room.minZ+room.maxZ)*.5;
            AxisAlignedBB contact=new AxisAlignedBB(x-EPS,room.minY-EPS,z-EPS,x+EPS,room.minY,z+EPS);
            return clearBody(room) && world.getBlockCollisions(null,contact).anyMatch(s->!s.isEmpty())
                    && permitted.test(pos) && naturalSupport.test(pos);
        }

        private boolean inside(AxisAlignedBB room) {
            WorldBorder b=world.getWorldBorder();
            return room.minX>=b.getMinX() && room.maxX<=b.getMaxX() && room.minZ>=b.getMinZ() && room.maxZ<=b.getMaxZ()
                    && room.minY>=0 && room.maxY<=Math.min(world.getMaxBuildHeight(),world.dimensionType().logicalHeight());
        }

        private boolean clearBody(AxisAlignedBB room) {
            if (!inside(room) || !world.noCollision(room) || world.containsAnyLiquid(room)) return false;
            for(BlockPos cell: BlockPos.betweenClosed(new BlockPos(room.minX+EPS,room.minY-EPS,room.minZ+EPS),
                    new BlockPos(room.maxX-EPS,room.maxY-EPS,room.maxZ-EPS))) {
                BlockState s=world.getBlockState(cell);
                if ((traveler != null ? traveler.getType() : EntityType.PLAYER).isBlockDangerous(s)
                        || s.is(Blocks.NETHER_PORTAL) || s.is(Blocks.END_PORTAL) || s.is(Blocks.END_GATEWAY)) return false;
            }
            return world.getEntitiesOfClass(LivingEntity.class,room,e->e!=traveler && e.isAlive() && !e.isSpectator()).isEmpty();
        }

        private AxisAlignedBB deck(Vector3d feet,AxisAlignedBB body) {
            AxisAlignedBB r=body.move(feet);
            int halfX=Math.max(1,MathHelper.ceil(Math.max(-body.minX,body.maxX)-.5));
            int halfZ=Math.max(1,MathHelper.ceil(Math.max(-body.minZ,body.maxZ)-.5));
            int x=MathHelper.floor(feet.x),z=MathHelper.floor(feet.z),y=MathHelper.floor(r.minY+EPS);
            return new AxisAlignedBB(x-halfX,y-1,z-halfZ,x+halfX+1,y,z+halfZ+1);
        }

        private boolean air(BlockPos p) {
            BlockState b=world.getBlockState(p);
            return b.is(Blocks.AIR) || b.is(Blocks.CAVE_AIR) || b.is(Blocks.VOID_AIR);
        }

        private boolean buildable(Vector3d feet,AxisAlignedBB body) {
            AxisAlignedBB deck=deck(feet,body);
            AxisAlignedBB clearance=new AxisAlignedBB(deck.minX, deck.minY, deck.minZ, deck.maxX,
                    deck.maxY+Math.max(2,MathHelper.ceil(body.getYsize())),deck.maxZ);
            if (!inside(clearance) || !permitted.test(feet)) return false;
            for(BlockPos p: cells(clearance)) if (!air(p)) return false;
            return world.getEntities((Entity)null,clearance,e->e.isAlive() && !e.isSpectator()).isEmpty();
        }

        private Iterable<BlockPos> cells(AxisAlignedBB box) {
            return BlockPos.betweenClosed(new BlockPos(box.minX,box.minY,box.minZ),
                    new BlockPos(box.maxX-EPS,box.maxY-EPS,box.maxZ-EPS));
        }

        public boolean stillValid() {
            Vector3d feet=feet();
            if (feet==null) return false;
            AxisAlignedBB body=relativeBody();
            return ready(new BlockPos(feet),body)
                    && (needsPlatform() ? buildable(feet,body) : naturalSafe(feet,body));
        }

        /** Called only once a cast is committed, not while its pen/preview is alive. */
        @Nullable public Vector3d commit(@Nullable Entity actor) {
            if (!stillValid()) return null;
            if (!needsPlatform()) return natural;
            if (actor==null || world.captureBlockSnapshots || world.restoringBlockSnapshots
                    || !world.capturedBlockSnapshots.isEmpty()) return null;
            if (actor instanceof PlayerEntity) {
                PlayerEntity player=(PlayerEntity)actor;
                if (!player.abilities.mayBuild) return null;
                for(BlockPos p: cells(deck(platform,relativeBody()))) if (!world.mayInteract(player,p)) return null;
            } else if (!ForgeEventFactory.getMobGriefingEvent(world,actor)) return null;

            List<BlockSnapshot> changes=new ArrayList<>();
            boolean ok=false;
            try {
                // Follow Forge's normal placement transaction. A cancelled claim hook must not leave half a deck.
                boolean placed=true;
                world.captureBlockSnapshots=true;
                try {
                    for(BlockPos p: cells(deck(platform,relativeBody()))) {
                        if (!world.setBlock(p.immutable(),Blocks.OBSIDIAN.defaultBlockState(),3)) { placed=false; break; }
                    }
                } finally {
                    world.captureBlockSnapshots=false;
                    changes.addAll(world.capturedBlockSnapshots);
                    world.capturedBlockSnapshots.clear();
                }
                if (!placed || changes.isEmpty() || ForgeEventFactory.onMultiBlockPlace(actor,changes,Direction.UP)
                        || !naturalSafe(platform,relativeBody())) return null;
                ok=true;
            } finally {
                if (!ok) {
                    world.restoringBlockSnapshots=true;
                    try {
                        for (int i=changes.size()-1;i>=0;i--) changes.get(i).restore(true,false);
                    } finally { world.restoringBlockSnapshots=false; }
                }
            }
            for(BlockSnapshot snap: changes) {
                BlockState old=snap.getReplacedBlock(), block=world.getBlockState(snap.getPos());
                block.onPlace(world,snap.getPos(),old,false);
                // Forge 36.2.34 dereferences the FULL chunk on the server despite its nullable annotation.
                world.markAndNotifyBlock(snap.getPos(),world.getChunkAt(snap.getPos()),old,block,snap.getFlag(),512);
            }
            natural=platform;
            return natural;
        }
    }
}

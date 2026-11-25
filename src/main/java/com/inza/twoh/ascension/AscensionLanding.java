package com.inza.twoh.ascension;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nullable;

/** Server-side occupied volume and support checks, performed immediately before committing a jump. */
final class AscensionLanding {
    private static final int SEARCH = 4;
    private static final double CONTACT = 1.0e-5;

    private AscensionLanding() {}

    static Vector3d insideBorder(ServerWorld world, Vector3d hint) {
        WorldBorder border = world.getWorldBorder();
        if (border.isWithinBounds(new BlockPos(hint))) return hint;
        double x = MathHelper.clamp(hint.x, border.getMinX() + .5, border.getMaxX() - .5);
        double z = MathHelper.clamp(hint.z, border.getMinZ() + .5, border.getMaxZ() - .5);
        return new Vector3d(x, hint.y, z);
    }

    private static AxisAlignedBB relativeBox(LivingEntity entity) {
        AxisAlignedBB box = entity.getBoundingBox().move(-entity.getX(), -entity.getY(), -entity.getZ());
        // Shift is the follow gesture. Leave space to release it, even if the player casts while crouching.
        Pose pose = entity instanceof PlayerEntity ? Pose.STANDING : entity.getPose();
        return box.minmax(entity.getDimensions(pose).makeBoundingBox(0, 0, 0));
    }

    static boolean columnsLoaded(ServerWorld world, LivingEntity entity, Vector3d hint) {
        AxisAlignedBB area = relativeBox(entity).move(hint).inflate(SEARCH + 2);
        // Include adjacent chunks touched by the collision shapes and by centering fallback candidates.
        for (int x = MathHelper.floor(area.minX) >> 4; x <= (MathHelper.floor(area.maxX) >> 4); x++)
            for (int z = MathHelper.floor(area.minZ) >> 4; z <= (MathHelper.floor(area.maxZ) >> 4); z++)
                if (world.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }

    @Nullable
    static Vector3d find(ServerWorld world, LivingEntity entity, Vector3d hint, boolean returning) {
        AxisAlignedBB body = relativeBox(entity);
        if (returning && safe(world, entity, body.move(hint))) return hint;

        // For returns, search the original floor first, including caves / rooms in ceiling dimensions.
        // Only the outbound route targets the generated surface of Heaven, which can be far below the caster.
        BlockPos base = new BlockPos(hint.x, hint.y + body.minY - CONTACT, hint.z);
        for (BlockPos entry : BlockPos.withinManhattan(base, SEARCH, returning ? SEARCH : 0, SEARCH)) {
            BlockPos floor = returning ? entry : new BlockPos(entry.getX(),
                    world.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, entry.getX(), entry.getZ()) - 1,
                    entry.getZ());
            if (floor.getY() < 0 || floor.getY() >= world.getMaxBuildHeight()) continue;
            VoxelShape shape = world.getBlockState(floor).getCollisionShape(world, floor);
            if (shape.isEmpty()) continue;
            double height = shape.max(Direction.Axis.Y, .5, .5);
            if (!Double.isFinite(height)) continue;
            Vector3d feet = new Vector3d(floor.getX() + .5, floor.getY() + height - body.minY, floor.getZ() + .5);
            if (safe(world, entity, body.move(feet))) return feet;
        }
        return null;
    }

    private static boolean safe(ServerWorld world, LivingEntity entity, AxisAlignedBB room) {
        WorldBorder border = world.getWorldBorder();
        // 1.16 WorldBorder.isWithinBounds(AABB) tests overlap, not full containment.
        if (room.minY < 0 || room.maxY > world.getMaxBuildHeight()
                || room.minX < border.getMinX() || room.maxX > border.getMaxX()
                || room.minZ < border.getMinZ() || room.maxZ > border.getMaxZ()) return false;

        double cx = (room.minX + room.maxX) * .5, cz = (room.minZ + room.maxZ) * .5;
        AxisAlignedBB support = new AxisAlignedBB(cx - CONTACT, room.minY - CONTACT, cz - CONTACT,
                cx + CONTACT, room.minY, cz + CONTACT);
        // Support must be a real collision surface directly under the feet (slabs and stairs included).
        if (world.getBlockCollisions(null, support).noneMatch(s -> !s.isEmpty())
                || !world.noCollision(room) || world.containsAnyLiquid(room)) return false;

        BlockPos lo = new BlockPos(room.minX + CONTACT, room.minY - CONTACT, room.minZ + CONTACT);
        BlockPos hi = new BlockPos(room.maxX - CONTACT, room.maxY - CONTACT, room.maxZ - CONTACT);
        for (BlockPos p : BlockPos.betweenClosed(lo, hi)) {
            BlockState state = world.getBlockState(p);
            if (entity.getType().isBlockDangerous(state) || state.is(Blocks.NETHER_PORTAL)
                    || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY)) return false;
        }
        return world.getEntitiesOfClass(LivingEntity.class, room, e -> e.isAlive() && !e.isSpectator()).isEmpty();
    }
}

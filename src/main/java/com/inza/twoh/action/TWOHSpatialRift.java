package com.inza.twoh.action;

import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.inza.twoh.entity.SpatialRiftEntity;
import com.inza.twoh.network.RiftDestinations;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

/** EN: Hold to draw, release to commit. A second cast closes the existing pair.
 * 中文：按住落笔，松手开门；已有裂隙时再次施放则收回。 */
public final class TWOHSpatialRift extends StandAction {
    public TWOHSpatialRift(Builder builder) {
        super(builder);
    }

    @Override
    public void startedHolding(
            World world, LivingEntity user, IStandPower power, ActionTarget target, boolean fulfilled) {
        super.startedHolding(world, user, power, target, fulfilled);
        if (!fulfilled || !(world instanceof ServerWorld)) {
            return;
        }
        if (user instanceof ServerPlayerEntity && user.isShiftKeyDown()) {
            RiftDestinations.open((ServerPlayerEntity) user);
            return;
        }
        SpatialRiftEntity.startPen((ServerWorld) world, user);
    }

    @Override
    protected void holdTick(
            World world, LivingEntity user, IStandPower power, int ticks, ActionTarget target, boolean fulfilled) {
        super.holdTick(world, user, power, ticks, target, fulfilled);
        if (fulfilled && world instanceof ServerWorld) {
            SpatialRiftEntity.samplePen((ServerWorld) world, user);
        }
    }

    @Override
    public void stoppedHolding(World world, LivingEntity user, IStandPower power, int ticks, boolean willFire) {
        super.stoppedHolding(world, user, power, ticks, willFire);
        if (world instanceof ServerWorld) {
            SpatialRiftEntity.releasePen((ServerWorld) world, user, willFire && ticks >= 1);
        }
    }
}

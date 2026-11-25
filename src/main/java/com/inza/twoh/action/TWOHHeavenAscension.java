package com.inza.twoh.action;

import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.action.stand.StandAction;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;
import com.inza.twoh.ascension.HeavenAscension;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

public final class TWOHHeavenAscension extends StandAction {
    public TWOHHeavenAscension(Builder builder) {
        super(builder);
    }

    @Override
    protected void perform(World world, LivingEntity user, IStandPower power, ActionTarget target) {
        if (world instanceof ServerWorld) {
            HeavenAscension.cast((ServerWorld) world, user, user.isShiftKeyDown());
        }
    }
}

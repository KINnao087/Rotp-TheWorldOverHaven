package com.inza.twoh.action;

import com.github.standobyte.jojo.action.stand.StandEntityHeavyAttack;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.entity.stand.StandEntityTask;
import com.github.standobyte.jojo.entity.stand.StandPose;
import com.github.standobyte.jojo.power.impl.stand.IStandPower;

public class TWOHFinisherPunch extends StandEntityHeavyAttack {
    public static final StandPose KICK = new StandPose("kick");

    public TWOHFinisherPunch(Builder builder) {
        super(builder);
    }

    @Override
    public StandPose getStandPose(IStandPower standPower, StandEntity standEntity, StandEntityTask task) {
        return KICK;
    }
}

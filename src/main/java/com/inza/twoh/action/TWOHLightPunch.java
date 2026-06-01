package com.inza.twoh.action;

import com.github.standobyte.jojo.action.stand.StandEntityLightAttack;
import com.github.standobyte.jojo.action.stand.punch.StandEntityPunch;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.util.mc.damage.StandEntityDamageSource;
import com.inza.twoh.realitymarksys.RealityMarkManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public class TWOHLightPunch extends StandEntityLightAttack {
    public TWOHLightPunch(Builder builder) {
        super(builder);
    }

    @Override
    public StandEntityPunch punchEntity(StandEntity stand, Entity target, StandEntityDamageSource dmgSource) {

        if (!(target instanceof LivingEntity)) return super.punchEntity(stand, target, dmgSource);

        RealityMarkManager.applyMark(stand.getUser(), (LivingEntity) target, 10000, 1);
        return super.punchEntity(stand, target, dmgSource);
    }
}

package com.inza.twoh.init;

import com.github.standobyte.jojo.action.Action;
import com.github.standobyte.jojo.entity.stand.StandEntityType;
import com.github.standobyte.jojo.init.power.stand.EntityStandRegistryObject;
import com.github.standobyte.jojo.init.power.stand.ModStandsInit;
import com.github.standobyte.jojo.power.impl.stand.StandInstance.StandPart;
import com.github.standobyte.jojo.power.impl.stand.stats.StandStats;
import com.github.standobyte.jojo.power.impl.stand.type.EntityStandType;
import com.github.standobyte.jojo.power.impl.stand.type.StandType;
import com.inza.twoh.AddonMain;
import com.inza.twoh.action.TWOHBarrage;
import com.inza.twoh.action.TWOHBlock;
import com.inza.twoh.action.TWOHFinisherPunch;
import com.inza.twoh.action.TWOHHeavyPunch;
import com.inza.twoh.action.TWOHLightPunch;
import com.inza.twoh.entity.TheWorldOverHavenStandEntity;

import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public class InitStands {
    @SuppressWarnings("unchecked")
    public static final DeferredRegister<Action<?>> ACTIONS = DeferredRegister.create(
            (Class<Action<?>>) ((Class<?>) Action.class), AddonMain.MOD_ID);
    @SuppressWarnings("unchecked")
    public static final DeferredRegister<StandType<?>> STANDS = DeferredRegister.create(
            (Class<StandType<?>>) ((Class<?>) StandType.class), AddonMain.MOD_ID);
    
 // ======================================== Example Stand ========================================
    
    
    // Create all the abilities here...
    public static final RegistryObject<TWOHLightPunch> TWOH_STAND_PUNCH = ACTIONS.register("twoh_stand_punch",
            () -> new TWOHLightPunch(new TWOHLightPunch.Builder()
                    .punchSound(InitSounds.EXAMPLE_STAND_PUNCH_LIGHT)));
    
    public static final RegistryObject<TWOHBarrage> TWOH_STAND_BARRAGE = ACTIONS.register("example_stand_barrage", 
            () -> new TWOHBarrage(new TWOHBarrage.Builder()
                    .barrageHitSound(InitSounds.EXAMPLE_STAND_PUNCH_BARRAGE)));

    public static final RegistryObject<TWOHFinisherPunch> TWOH_STAND_FINISHER_PUNCH = ACTIONS.register("example_stand_finisher_punch", 
            () -> new TWOHFinisherPunch(new TWOHFinisherPunch.Builder() // TODO finisher ability
                    .punchSound(InitSounds.EXAMPLE_STAND_PUNCH_HEAVY)
                    .partsRequired(StandPart.ARMS)));

    public static final RegistryObject<TWOHHeavyPunch> TWOH_STAND_HEAVY_PUNCH = ACTIONS.register("example_stand_heavy_punch", 
            () -> new TWOHHeavyPunch(new TWOHHeavyPunch.Builder()
                    .shiftVariationOf(TWOH_STAND_PUNCH).shiftVariationOf(TWOH_STAND_BARRAGE)
                    .setFinisherVariation(TWOH_STAND_FINISHER_PUNCH)
                    .punchSound(InitSounds.EXAMPLE_STAND_PUNCH_HEAVY)
                    .partsRequired(StandPart.ARMS)));
    
    public static final RegistryObject<TWOHBlock> TWOH_STAND_BLOCK = ACTIONS.register("example_stand_block", 
            () -> new TWOHBlock());
    

    // ...then create the Stand type instance. Moves, stats, entity sizes, and a few other things are determined here.
    public static final EntityStandRegistryObject<EntityStandType<StandStats>, StandEntityType<TheWorldOverHavenStandEntity>> STAND_EXAMPLE_STAND =
            new EntityStandRegistryObject<>("the_world_over_haven_stand",
                    STANDS, 
                    () -> new EntityStandType.Builder<StandStats>()
                    .color(0x00AFAF)
                    .storyPartName(ModStandsInit.PART_3_NAME)
                    .leftClickHotbar(
                            TWOH_STAND_PUNCH.get(),
                            TWOH_STAND_BARRAGE.get()
                            )
                    .rightClickHotbar(
                            TWOH_STAND_BLOCK.get()
                            )
                    .defaultStats(StandStats.class, new StandStats.Builder()
                            .tier(6)
                            .power(20)
                            .speed(20)
                            .range(50, 100)
                            .durability(20)
                            .precision(20)
                            .build())
                    .addSummonShout(InitSounds.EXAMPLE_STAND_SUMMON_VOICELINE)
                    .addOst(InitSounds.EXAMPLE_STAND_OST)
                    .build(),
                    
                    InitEntities.ENTITIES,
                    () -> new StandEntityType<TheWorldOverHavenStandEntity>(TheWorldOverHavenStandEntity::new, 0.7F, 2.1F)
                    .summonSound(InitSounds.EXAMPLE_STAND_SUMMON_SOUND)
                    .unsummonSound(InitSounds.EXAMPLE_STAND_UNSUMMON_SOUND))
            .withDefaultStandAttributes();
    

    
    // ======================================== ??? ========================================
    
    
    
}

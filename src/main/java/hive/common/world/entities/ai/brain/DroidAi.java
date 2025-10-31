package hive.common.world.entities.ai.brain;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import hive.common.world.entities.DroidEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.EraseMemoryIf;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.RandomStroll;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTarget;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetAwayFrom;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Optional;
import java.util.Set;

public final class DroidAi {

    public static final ImmutableList<SensorType<? extends Sensor<? super DroidEntity>>> SENSOR_TYPES = ImmutableList.of(
            SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.HURT_BY
    );
    public static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
            MemoryModuleType.LOOK_TARGET,
            MemoryModuleType.DOORS_TO_CLOSE,
            MemoryModuleType.NEAREST_LIVING_ENTITIES,
            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
            MemoryModuleType.NEAREST_VISIBLE_PLAYER,
            MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
            MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS,
            MemoryModuleType.HURT_BY,
            MemoryModuleType.HURT_BY_ENTITY,
            MemoryModuleType.WALK_TARGET,
            MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
            MemoryModuleType.ATTACK_TARGET,
            MemoryModuleType.ATTACK_COOLING_DOWN,
            MemoryModuleType.INTERACTION_TARGET,
            MemoryModuleType.PATH,
            MemoryModuleType.AVOID_TARGET
    );

    private DroidAi() {
    }

    public static Brain<DroidEntity> makeBrain(DroidEntity droid, Brain<DroidEntity> brain) {
        initCoreActivity(brain);
        initIdleActivity(brain);
        initFightActivity(droid, brain);
        initRetreatActivity(brain);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static void initCoreActivity(Brain<DroidEntity> brain) {
        brain.addActivity(
                Activity.CORE,
                0,
                ImmutableList.of(
                        new LookAtTargetSink(45, 90),
                        new MoveToTargetSink(),
                        InteractWithDoor.create()
                )
        );
    }

    private static void initIdleActivity(Brain<DroidEntity> brain) {
        brain.addActivity(
                Activity.IDLE,
                10,
                ImmutableList.of(
                        StartAttacking.create(DroidAi::findNearestValidAttackTarget),
                        createIdleLookBehaviors(),
                        createIdleMovementBehaviors()
                )
        );
    }

    private static void initFightActivity(DroidEntity droid, Brain<DroidEntity> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(
                Activity.FIGHT,
                10,
                ImmutableList.of(
                        StopAttackingIfTargetInvalid.create((world, target) -> !isNearestValidAttackTarget(world, droid, target)),
                        SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(1),
                        MeleeAttack.create(20)
                ),
                MemoryModuleType.ATTACK_TARGET
        );
    }

    private static void initRetreatActivity(Brain<DroidEntity> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(
                Activity.AVOID,
                10,
                ImmutableList.of(
                        SetWalkTargetAwayFrom.entity(MemoryModuleType.AVOID_TARGET, 1, 12, true),
                        createIdleLookBehaviors(),
                        createIdleMovementBehaviors(),
                        EraseMemoryIf.create(DroidAi::wantsToStopFleeing, MemoryModuleType.AVOID_TARGET)
                ),
                MemoryModuleType.AVOID_TARGET
        );
    }

    private static ImmutableList<Pair<OneShot<LivingEntity>, Integer>> createLookBehaviors() {
        return ImmutableList.of(
                Pair.of(SetEntityLookTarget.create(EntityType.PLAYER, 8), 1),
                Pair.of(SetEntityLookTarget.create(8), 1)
        );
    }

    private static RunOne<LivingEntity> createIdleLookBehaviors() {
        return new RunOne<>(
                ImmutableList.<Pair<? extends BehaviorControl<? super LivingEntity>, Integer>>builder()
                        .addAll(createLookBehaviors())
                        .add(Pair.of(new DoNothing(30, 60), 1))
                        .build()
        );
    }

    private static RunOne<DroidEntity> createIdleMovementBehaviors() {
        return new RunOne<>(
                ImmutableList.of(
                        Pair.of(RandomStroll.stroll(0.6f), 2),
                        Pair.of(new DoNothing(30, 60), 1)
                )
        );
    }

    private static boolean isNearestValidAttackTarget(ServerLevel world, DroidEntity entity, LivingEntity target) {
        return findNearestValidAttackTarget(world, entity).filter(t -> t == target).isPresent();
    }

    private static Optional<? extends LivingEntity> findNearestValidAttackTarget(ServerLevel world, DroidEntity entity) {
        return entity.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER);
    }

    private static boolean wantsToStopFleeing(DroidEntity p_35009_) {
        Brain<DroidEntity> brain = p_35009_.getBrain();
        return !brain.hasMemoryValue(MemoryModuleType.AVOID_TARGET);
    }

    public static void updateActivity(DroidEntity droid) {
        Brain<DroidEntity> brain = droid.getBrain();
        brain.setActiveActivityToFirstValid(
                ImmutableList.of(Activity.FIGHT, Activity.AVOID, Activity.IDLE)
        );
        droid.setAggressive(brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET));
    }

}

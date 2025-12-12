package hive.common.world.entities;

import com.mojang.serialization.Dynamic;
import hive.common.Hive;
import hive.common.ItemTags;
import hive.common.ServerConfigs;
import hive.common.rl.DecisionState;
import hive.common.rl.TrainingData;
import hive.common.world.HiveMind;
import hive.common.world.entities.ai.DroidMoveControl;
import hive.common.world.entities.ai.brain.DroidAi;
import hive.common.world.entities.ai.pathfinding.DroidPathNavigation;
import hive.common.world.packets.DebugPathEffectPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DroidEntity extends Monster {

    public static final float FLY_MULTIPLIER = 0.2f;
    public static final double JUMP_BOOST = 0.2;
    public static final List<EquipmentSlot> EQUIPMENT_POPULATION_ORDER = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND);
    private DecisionState decision = DecisionState.ATTACK;

    public DroidEntity(EntityType<? extends DroidEntity> type, Level world) {
        super(type, world);
        moveControl = new DroidMoveControl(this);
        setCanPickUpLoot(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.ATTACK_DAMAGE, 1)
                .add(Attributes.MOVEMENT_SPEED, 0.1f)
                .add(Attributes.FOLLOW_RANGE, 64);
    }

    @Override
    protected Brain.Provider<DroidEntity> brainProvider() {
        return Brain.provider(DroidAi.MEMORY_TYPES, DroidAi.SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> context) {
        return DroidAi.makeBrain(this, brainProvider().makeBrain(context));
    }

    @Override
    public Brain<DroidEntity> getBrain() {
        return (Brain<DroidEntity>) super.getBrain();
    }

    @Nullable
    @Override
    public LivingEntity getTarget() {
        return getTargetFromBrain();
    }

    @SuppressWarnings({"deprecation", "OverrideOnly"})
    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, EntitySpawnReason spawn, @Nullable SpawnGroupData data) {
        RandomSource random = world.getRandom();
        populateDefaultEquipmentSlots(random, difficulty);
        populateDefaultEquipmentEnchantments(world, random, difficulty);
        return super.finalizeSpawn(world, difficulty, spawn, data);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        double chance = level().getDifficulty() == Difficulty.HARD ? ServerConfigs.INSTANCE.droidHardGearRate.get() : ServerConfigs.INSTANCE.droidGearRate.get();
        for (EquipmentSlot slot : EQUIPMENT_POPULATION_ORDER) {
            if (random.nextDouble() < chance) {
                BuiltInRegistries.ITEM.getOrThrow(ItemTags.DROID_EQUIPMENT.get(slot))
                        .getRandomElement(random)
                        .ifPresent(item -> setItemSlot(slot, new ItemStack(item)));
            } else {
                break;
            }
        }
    }

    public DecisionState getDecision() {
        return decision;
    }

    protected void setDecision(DecisionState decision) {
        this.decision = decision;
    }

    @Override
    protected float getFlyingSpeed() {
        return getSpeed() * FLY_MULTIPLIER;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.PLAYER_SWIM;
    }

    @Override
    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.PLAYER_SPLASH;
    }

    @Override
    protected SoundEvent getSwimHighSpeedSplashSound() {
        return SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return source.type().effects().sound();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public LivingEntity.Fallsounds getFallSounds() {
        return new LivingEntity.Fallsounds(SoundEvents.PLAYER_SMALL_FALL, SoundEvents.PLAYER_BIG_FALL);
    }

    @Override
    protected void customServerAiStep(ServerLevel world) {
        super.customServerAiStep(world);
        ProfilerFiller profiler = Profiler.get();
        profiler.push(Hive.ID + ":droidBrain");
        getBrain().tick(world, this);
        profiler.pop();
        DroidAi.updateActivity(this);
        if (isAggressive()) {
            HiveMind hive = HiveMind.getOrCreate(world.getServer());
            TrainingData resp = hive.evaluate(world, this);
            setDecision(resp.decision());
            hive.addTrainingData(resp.input(), resp.decision());
        }
        if (getMoveControl() instanceof DroidMoveControl control) {
            boolean sprint = control.shouldSprint();
            if (sprint != isSprinting()) {
                setSprinting(sprint);
            }
        }
        if (ServerConfigs.INSTANCE.pathDebug.get() && world.getGameTime() % 20 == 0) {
            Path path = getNavigation().getPath();
            if (path != null) {
                List<BlockPos> all = new ArrayList<>();
                for (int i = 0; i < path.getNodeCount(); i++) {
                    Node node = path.getNode(i);
                    all.add(new BlockPos(node.x, node.y, node.z));
                }
                PacketDistributor.sendToPlayersTrackingEntity(this, new DebugPathEffectPacket(all));
            }
        }
    }

    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false;
    }

    @Override
    protected DroidPathNavigation createNavigation(Level world) {
        DroidPathNavigation navigation = new DroidPathNavigation(this, world);
        navigation.setCanOpenDoors(true);
        return navigation;
    }

    @Override
    public DroidPathNavigation getNavigation() {
        return (DroidPathNavigation) super.getNavigation();
    }

    public boolean isInSwimmableFluid() {
        return isInFluidType((fluidType, height) -> canSwimInFluidType(fluidType));
    }

}

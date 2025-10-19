package hive.common.world.entities;

import hive.common.ItemTags;
import hive.common.ServerConfigs;
import hive.common.world.entities.ai.DroidMoveControl;
import hive.common.world.entities.ai.DroidPathNavigation;
import hive.common.world.packets.DebugPathEffectPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
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

    @SuppressWarnings({"deprecation", "OverrideOnly"})
    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawn, @Nullable SpawnGroupData data) {
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
                BuiltInRegistries.ITEM.getTag(ItemTags.DROID_EQUIPMENT.get(slot)).orElseThrow()
                        .getRandomElement(random)
                        .ifPresent(item -> setItemSlot(slot, new ItemStack(item)));
            } else {
                break;
            }
        }
    }

    @Override
    protected float getFlyingSpeed() {
        return getSpeed() * FLY_MULTIPLIER;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new OpenDoorGoal(this, false));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1, false));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        targetSelector.addGoal(0, new HurtByTargetGoal(this).setAlertOthers(DroidEntity.class));
        targetSelector.addGoal(1, getTargetPlayerGoal(this));
    }

    public double getEffectiveGravity() {
        boolean down = getDeltaMovement().y <= 0;
        return down && hasEffect(MobEffects.SLOW_FALLING) ? Math.min(getGravity(), 0.01) : getGravity();
    }

    protected TargetGoal getTargetPlayerGoal(Mob mob) {
        NearestAttackableTargetGoal<Player> goal = new NearestAttackableTargetGoal<>(mob, Player.class, false);
        goal.targetConditions = goal.targetConditions.ignoreLineOfSight();
        return goal;
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
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (getMoveControl() instanceof DroidMoveControl control) {
            boolean sprint = control.shouldSprint();
            if (sprint != isSprinting()) {
                setSprinting(sprint);
            }
        }
        if (ServerConfigs.INSTANCE.pathDebug.get() && level().getGameTime() % 20 == 0) {
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

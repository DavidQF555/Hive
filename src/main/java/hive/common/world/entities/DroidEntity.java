package hive.common.world.entities;

import hive.common.ItemTags;
import hive.common.ServerConfigs;
import hive.common.world.Physics;
import hive.common.world.entities.ai.DroidMeleeAttackGoal;
import hive.common.world.entities.ai.movement.DroidMoveControl;
import hive.common.world.entities.ai.movement.DroidPathNavigation;
import hive.common.world.entities.ai.pathfinding.ModedNode;
import hive.common.world.packets.DebugPathEffectPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DroidEntity extends Monster {

    public static final List<EquipmentSlot> EQUIPMENT_POPULATION_ORDER = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND);
    // knockback damping in doHurtTarget, same as Mob.doHurtTarget
    private static final float KNOCKBACK_TARGET_SCALE = 0.5f;
    private static final double KNOCKBACK_SELF_DAMP = 0.6;
    private long nextAttackTick; // tick when attack is off cooldown

    public DroidEntity(EntityType<? extends DroidEntity> type, Level world) {
        super(type, world);
        moveControl = new DroidMoveControl(this);
        setCanPickUpLoot(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.ATTACK_DAMAGE, 1)
                .add(Attributes.ATTACK_SPEED, 4)
                .add(Attributes.ENTITY_INTERACTION_RANGE, 3)
                .add(Attributes.MOVEMENT_SPEED, 0.1f)
                .add(Attributes.FOLLOW_RANGE, 64);
    }

    public long getNextAttackTick() {
        return nextAttackTick;
    }

    public void setNextAttackTick(long tick) {
        this.nextAttackTick = tick;
    }

    @Override
    protected float getKnockback(Entity target, DamageSource source) {
        return super.getKnockback(target, source) + (isSprinting() ? 1 : 0);
    }

    @Override
    public boolean isWithinMeleeAttackRange(LivingEntity target) {
        double range = getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        return target.getBoundingBox().distanceToSqr(getEyePosition()) < range * range;
    }

    // modify knockback to use hit direction instead of body yaw
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        ItemStack weapon = getWeaponItem();
        DamageSource source = Optional.ofNullable(weapon.getItem().getDamageSource(this))
                .orElse(damageSources().mobAttack(this));
        damage = EnchantmentHelper.modifyDamage(level, weapon, target, source, damage);
        damage += weapon.getItem().getAttackDamageBonus(target, damage, source);
        boolean hurt = target.hurtServer(level, source, damage);
        if (hurt) {
            float kb = getKnockback(target, source);
            if (kb > 0 && target instanceof LivingEntity living) {
                // mirrors vanilla Mob.doHurtTarget, half the knockback impulse is applied to the target,
                // and the attacker's own xz momentum is dampened to 0.6 to brace against the hit
                living.knockback(kb * KNOCKBACK_TARGET_SCALE, getX() - target.getX(), getZ() - target.getZ());
                setDeltaMovement(getDeltaMovement().multiply(KNOCKBACK_SELF_DAMP, 1, KNOCKBACK_SELF_DAMP));
            }
            if (target instanceof LivingEntity living) {
                weapon.hurtEnemy(living, this);
            }
            EnchantmentHelper.doPostAttackEffects(level, target, source);
            setLastHurtMob(target);
            playAttackSound();
        }
        return hurt;
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

    @Override
    protected float getFlyingSpeed() {
        return getSpeed() * Physics.Constants.FLY_MULTIPLIER;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return pose == Pose.SWIMMING ? EntityDimensions.scalable(0.6f, 0.6f) : super.getDefaultDimensions(pose);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new OpenDoorGoal(this, false));
        goalSelector.addGoal(1, new DroidMeleeAttackGoal(this, 1, false));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        targetSelector.addGoal(0, new HurtByTargetGoal(this).setAlertOthers(DroidEntity.class));
        targetSelector.addGoal(1, getTargetPlayerGoal(this));
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
    protected void customServerAiStep(ServerLevel world) {
        super.customServerAiStep(world);
        if (getMoveControl() instanceof DroidMoveControl control) {
            boolean sprint = control.shouldSprint();
            if (sprint != isSprinting()) {
                setSprinting(sprint);
            }
        }
        if (ServerConfigs.INSTANCE.pathDebug.get() && world.getGameTime() % 20 == 0) {
            Path path = getNavigation().getPath();
            if (path != null) {
                int n = path.getNodeCount();
                List<BlockPos> all = new ArrayList<>(n);
                byte[] modes = new byte[n];
                for (int i = 0; i < n; i++) {
                    Node node = path.getNode(i);
                    all.add(new BlockPos(node.x, node.y, node.z));
                    modes[i] = (byte) ModedNode.modeOf(node).ordinal();
                }
                PacketDistributor.sendToPlayersTrackingEntity(this, new DebugPathEffectPacket(all, modes));
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

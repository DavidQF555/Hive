package hive.common.world.entities;

import hive.common.ItemTags;
import hive.common.ServerConfigs;
import hive.common.world.Physics;
import hive.common.world.entities.ai.DroidMeleeAttackGoal;
import hive.common.world.entities.ai.movement.DroidLookControl;
import hive.common.world.entities.ai.movement.DroidMoveControl;
import hive.common.world.entities.ai.movement.DroidPathNavigation;
import hive.common.world.entities.ai.pathfinding.ModedNode;
import hive.common.world.packets.DebugPathEffectPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
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
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DroidEntity extends Monster {

    public static final List<EquipmentSlot> EQUIPMENT_POPULATION_ORDER = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND);
    public static final float ATTACK_FACING_THRESHOLD_DEGREES = 20;
    // knockback damping in doHurtTarget, same as Mob.doHurtTarget
    private static final float KNOCKBACK_TARGET_SCALE = 0.5f;
    private static final double KNOCKBACK_SELF_DAMP = 0.6;
    private static final int TICKS_PER_SECOND = 20;
    // mirrors LocalPlayer.hasEnoughImpulseToStartSprinting
    private static final double SPRINT_INPUT_MAGNITUDE_SQ = 0.8 * 0.8;
    private static final double SPRINT_SWIM_INPUT_MAGNITUDE_SQ = 1E-5;
    private long nextAttackTick; // tick when attack is off cooldown

    public DroidEntity(EntityType<? extends DroidEntity> type, Level world) {
        super(type, world);
        moveControl = new DroidMoveControl(this);
        lookControl = new DroidLookControl(this);
        setCanPickUpLoot(true);
        setPathfindingMalus(PathType.WATER, 0);
        setPathfindingMalus(PathType.WATER_BORDER, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.ATTACK_DAMAGE, 1)
                .add(Attributes.ATTACK_SPEED, 4)
                .add(Attributes.ENTITY_INTERACTION_RANGE, 3)
                .add(Attributes.MOVEMENT_SPEED, 0.1f)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(NeoForgeMod.SWIM_SPEED, 1);
    }

    public long getNextAttackTick() {
        return nextAttackTick;
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
    public boolean doHurtTarget(Entity target) {
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        ItemStack weapon = getWeaponItem();
        DamageSource source = damageSources().mobAttack(this);
        if (level() instanceof ServerLevel level) {
            damage = EnchantmentHelper.modifyDamage(level, weapon, target, source, damage);
        }
        damage += weapon.getItem().getAttackDamageBonus(target, damage, source);
        boolean hurt = target.hurt(source, damage);
        if (hurt) {
            float kb = getKnockback(target, source);
            if (kb > 0 && target instanceof LivingEntity living) {
                // mirrors vanilla Mob.doHurtTarget, half the knockback impulse is applied to the target,
                // and the attacker's own xz momentum is dampened to 0.6 to brace against the hit
                living.knockback(kb * KNOCKBACK_TARGET_SCALE, getX() - target.getX(), getZ() - target.getZ());
                setDeltaMovement(getDeltaMovement().multiply(KNOCKBACK_SELF_DAMP, 1, KNOCKBACK_SELF_DAMP));
            }
            if (level() instanceof ServerLevel level) {
                EnchantmentHelper.doPostAttackEffects(level, target, source);
            }
            setLastHurtMob(target);
            playAttackSound();
        }
        return hurt;
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
    public float getFlyingSpeed() {
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
    public DroidMoveControl getMoveControl() {
        return (DroidMoveControl) super.getMoveControl();
    }

    @Override
    public int getMaxHeadYRot() {
        return (int) getMaxHeadRotationRelativeToBody();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        boolean sprint = shouldSprint();
        if (sprint != isSprinting()) {
            setSprinting(sprint);
        }

        DroidMoveControl control = getMoveControl();
        LivingEntity target = getTarget();
        // tells move control to prepare for attack by rotating
        control.setAttackTarget(isAttackable(target) ? target : null);
        tryAttackTarget();

        if (ServerConfigs.INSTANCE.pathDebug.get() && level().getGameTime() % 20 == 0) {
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

    private void tryAttackTarget() {
        if (!canSwing()) {
            return;
        }
        LivingEntity target = getTarget();
        long now = level().getGameTime();
        if (now < nextAttackTick || target.invulnerableTime > Physics.Constants.INVULNERABLE_TICKS) {
            return;
        }
        nextAttackTick = now + getAttackCooldownTicks();
        swing(InteractionHand.MAIN_HAND);
        doHurtTarget(target);
    }

    protected boolean canSwing() {
        LivingEntity target = getTarget();
        if (!isAttackable(target)) {
            return false;
        }
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return Math.abs(Mth.wrapDegrees(targetYaw - yHeadRot)) <= ATTACK_FACING_THRESHOLD_DEGREES;
    }

    private boolean isAttackable(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && isWithinMeleeAttackRange(target) && getSensing().hasLineOfSight(target);
    }

    // mirrors LocalPlayer.hasEnoughImpulseToStartSprinting
    protected boolean shouldSprint() {
        double impulseSq = xxa * xxa + zza * zza;
        if (isVisuallySwimming()) {
            return impulseSq > SPRINT_SWIM_INPUT_MAGNITUDE_SQ;
        }
        if (isInWater()) {
            return false;
        }
        return impulseSq > SPRINT_INPUT_MAGNITUDE_SQ;
    }

    private int getAttackCooldownTicks() {
        double speed = getAttributeValue(Attributes.ATTACK_SPEED);
        return speed > 0 ? (int) Math.ceil((double) TICKS_PER_SECOND / speed) : TICKS_PER_SECOND;
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

}

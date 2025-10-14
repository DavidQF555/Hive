package hive.common.world.entities;

import hive.common.world.entities.ai.DroidMoveControl;
import hive.common.world.entities.ai.DroidPathNavigation;
import hive.common.world.packets.PathEffectPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class DroidEntity extends PathfinderMob {

    public static final float FLY_MULTIPLIER = 0.2f;
    public static final double JUMP_BOOST = 0.2;

    public DroidEntity(EntityType<? extends DroidEntity> type, Level world) {
        super(type, world);
        moveControl = new DroidMoveControl(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.ATTACK_DAMAGE, 1)
                .add(Attributes.MOVEMENT_SPEED, 0.1f)
                .add(Attributes.FOLLOW_RANGE, 64);
    }

    @Override
    protected float getFlyingSpeed() {
        return getSpeed() * FLY_MULTIPLIER;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new MeleeAttackGoal(this, 1, false));
        goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 1));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Villager.class, false));
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
        if (world.getGameTime() % 20 == 0) {
            Path path = getNavigation().getPath();
            if (path != null) {
                List<BlockPos> all = new ArrayList<>();
                for (int i = 0; i < path.getNodeCount(); i++) {
                    Node node = path.getNode(i);
                    all.add(new BlockPos(node.x, node.y, node.z));
                }
                PacketDistributor.sendToPlayersTrackingEntity(this, new PathEffectPacket(all));
            }
        }
    }

    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false;
    }

    @Override
    protected DroidPathNavigation createNavigation(Level world) {
        return new DroidPathNavigation(this, world);
    }

    @Override
    public DroidPathNavigation getNavigation() {
        return (DroidPathNavigation) super.getNavigation();
    }

    public boolean isInSwimmableFluid() {
        return isInFluidType((fluidType, height) -> canSwimInFluidType(fluidType));
    }

}

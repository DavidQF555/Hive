package hive.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ClientHelper {

    private static final double DISTANCE = 0.1;

    private ClientHelper() {
    }

    public static void renderPath(List<BlockPos> path) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world != null && !path.isEmpty()) {
            for (BlockPos pos : path) {
                world.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0, 0, 0);
            }
            for (int i = 1; i < path.size(); i++) {
                BlockPos from = path.get(i - 1);
                BlockPos to = path.get(i);
                double total = Math.sqrt(from.distSqr(to));
                Vec3 dir = Vec3.atLowerCornerOf(to.subtract(from)).normalize().scale(DISTANCE);
                Vec3 current = Vec3.atCenterOf(from);
                for (double j = 0; j < total; j += DISTANCE) {
                    world.addParticle(ParticleTypes.SMOKE, current.x(), current.y(), current.z(), 0, 0, 0);
                    current = current.add(dir);
                }
            }
        }
    }

}

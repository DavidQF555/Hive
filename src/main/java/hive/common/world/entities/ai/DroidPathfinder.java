package hive.common.world.entities.ai;

import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.jetbrains.annotations.Nullable;

public class DroidPathfinder extends PathFinder {

    private static final float JUMP_WEIGHT = 0.8f;

    public DroidPathfinder(NodeEvaluator eval, int max) {
        super(eval, max);
        neighbors = new Node[128];
    }

    public static boolean isJump(@Nullable Node prev, Node next) {
        return prev != null && (next.y > prev.y || Math.abs(prev.x - next.x) >= 2 || Math.abs(prev.z - next.z) >= 2);
    }

    @Override
    protected float distance(Node n1, Node n2) {
        float base = super.distance(n1, n2);
        return isJump(n1, n2) ? base * JUMP_WEIGHT : base;
    }

}

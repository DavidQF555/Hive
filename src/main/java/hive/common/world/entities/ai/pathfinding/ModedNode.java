package hive.common.world.entities.ai.pathfinding;

import net.minecraft.world.level.pathfinder.Node;

public class ModedNode extends Node {

    public final MovementMode mode;

    public ModedNode(int x, int y, int z, MovementMode mode) {
        super(x, y, z);
        this.mode = mode;
        this.hash = hash(x, y, z, mode);
    }

    public static int hash(int x, int y, int z, MovementMode mode) {
        return Node.createHash(x, y, z) * MovementMode.COUNT + mode.ordinal();
    }

    public static MovementMode modeOf(Node node) {
        return node instanceof ModedNode m ? m.mode : MovementMode.WALK;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ModedNode node) {
            return this.hash == node.hash && this.mode == node.mode && this.x == node.x && this.y == node.y && this.z == node.z;
        }
        return false;
    }

    @Override
    public String toString() {
        return "ModedNode{" + x + "," + y + "," + z + "," + mode + "}";
    }
}

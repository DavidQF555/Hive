package hive.common.world.entities.ai;

public enum ModeTransition {

    WALK_TO_SWIM(MovementMode.WALK, MovementMode.SWIM, 0),
    SWIM_TO_WALK(MovementMode.SWIM, MovementMode.WALK, 0),
    JUMP_TO_WALK(MovementMode.JUMP, MovementMode.WALK, 0);

    private static final ModeTransition[][] BY_FROM_TO = buildLookup();
    private static final ModeTransition[][] BY_FROM = buildFromIndex();

    public final MovementMode from;
    public final MovementMode to;
    public final int costTicks;

    ModeTransition(MovementMode from, MovementMode to, int costTicks) {
        this.from = from;
        this.to = to;
        this.costTicks = costTicks;
    }

    public static ModeTransition find(MovementMode from, MovementMode to) {
        return BY_FROM_TO[from.ordinal()][to.ordinal()];
    }

    public static ModeTransition[] fromMode(MovementMode from) {
        return BY_FROM[from.ordinal()];
    }

    private static ModeTransition[][] buildLookup() {
        int n = MovementMode.COUNT;
        ModeTransition[][] table = new ModeTransition[n][n];
        for (ModeTransition t : values()) {
            table[t.from.ordinal()][t.to.ordinal()] = t;
        }
        return table;
    }

    private static ModeTransition[][] buildFromIndex() {
        ModeTransition[][] out = new ModeTransition[MovementMode.COUNT][];
        for (MovementMode from : MovementMode.values()) {
            int count = 0;
            for (ModeTransition t : values()) {
                if (t.from == from) count++;
            }
            ModeTransition[] arr = new ModeTransition[count];
            int i = 0;
            for (ModeTransition t : values()) {
                if (t.from == from) arr[i++] = t;
            }
            out[from.ordinal()] = arr;
        }
        return out;
    }

}

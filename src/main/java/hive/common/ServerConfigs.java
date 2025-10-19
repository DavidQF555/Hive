package hive.common;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ServerConfigs {

    public static final ServerConfigs INSTANCE;
    public static final ModConfigSpec SPEC;

    static {
        Pair<ServerConfigs, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(ServerConfigs::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    public final ModConfigSpec.BooleanValue pathDebug;
    public final ModConfigSpec.DoubleValue droidGearRate, droidHardGearRate;

    public ServerConfigs(ModConfigSpec.Builder builder) {
        builder.comment("Multiverse server-side configuration");
        pathDebug = builder.comment("Whether to display particles for Droid entity paths")
                .define("pathDebug", false);
        droidGearRate = builder.comment("Chance of each Droid entity equipment slot to be equipped when not in Hard difficulty")
                .defineInRange("droidGearRate", 0.75, 0, 1);
        droidHardGearRate = builder.comment("Chance of each Droid entity equipment slot to be equipped when in Hard difficulty")
                .defineInRange("droidHardGearRate", 0.9, 0, 1);
    }

}

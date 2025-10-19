package hive.common;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ServerConfigs {

    public static final ServerConfigs INSTANCE;
    public static final ForgeConfigSpec SPEC;

    static {
        Pair<ServerConfigs, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(ServerConfigs::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    public final ForgeConfigSpec.BooleanValue pathDebug;
    public final ForgeConfigSpec.DoubleValue droidGearRate, droidHardGearRate;

    public ServerConfigs(ForgeConfigSpec.Builder builder) {
        builder.comment("Multiverse server-side configuration");
        pathDebug = builder.comment("Whether to display particles for Droid entity paths")
                .define("pathDebug", false);
        droidGearRate = builder.comment("Chance of each Droid entity equipment slot to be equipped when not in Hard difficulty")
                .defineInRange("droidGearRate", 0.75, 0, 1);
        droidHardGearRate = builder.comment("Chance of each Droid entity equipment slot to be equipped when in Hard difficulty")
                .defineInRange("droidHardGearRate", 0.9, 0, 1);
    }

}

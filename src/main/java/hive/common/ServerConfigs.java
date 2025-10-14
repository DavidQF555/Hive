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

    public ServerConfigs(ModConfigSpec.Builder builder) {
        builder.comment("Multiverse server-side configuration");
        pathDebug = builder.define("pathDebug", false);
    }

}

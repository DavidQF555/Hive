package hive.common.world.packets;

import hive.client.render.ClientHelper;
import hive.common.Hive;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public record DebugPathEffectPacket(List<BlockPos> pos) {

    private static final BiConsumer<DebugPathEffectPacket, FriendlyByteBuf> ENCODER = (message, buffer) -> {
        buffer.writeInt(message.pos().size());
        for (BlockPos pos : message.pos()) {
            buffer.writeBlockPos(pos);
        }
    };
    private static final Function<FriendlyByteBuf, DebugPathEffectPacket> DECODER = buffer -> {
        List<BlockPos> pos = new ArrayList<>();
        int size = buffer.readInt();
        for (int i = 0; i < size; i++) {
            pos.add(buffer.readBlockPos());
        }
        return new DebugPathEffectPacket(pos);
    };
    private static final BiConsumer<DebugPathEffectPacket, Supplier<NetworkEvent.Context>> CONSUMER = (message, context) -> {
        NetworkEvent.Context cont = context.get();
        cont.enqueueWork(() -> handle(message));
        cont.setPacketHandled(true);
    };

    public static void register(int index) {
        Hive.CHANNEL.registerMessage(index, DebugPathEffectPacket.class, ENCODER, DECODER, CONSUMER, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void handle(DebugPathEffectPacket packet) {
        ClientHelper.renderPath(packet.pos());
    }

}

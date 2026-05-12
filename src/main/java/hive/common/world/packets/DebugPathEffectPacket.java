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

public record DebugPathEffectPacket(List<BlockPos> pos, byte[] modes) {

    private static final BiConsumer<DebugPathEffectPacket, FriendlyByteBuf> ENCODER = (message, buffer) -> {
        buffer.writeInt(message.pos().size());
        for (int i = 0; i < message.pos().size(); i++) {
            buffer.writeBlockPos(message.pos().get(i));
            buffer.writeByte(message.modes()[i]);
        }
    };
    private static final Function<FriendlyByteBuf, DebugPathEffectPacket> DECODER = buffer -> {
        List<BlockPos> pos = new ArrayList<>();
        int size = buffer.readInt();
        byte[] modes = new byte[size];
        for (int i = 0; i < size; i++) {
            pos.add(buffer.readBlockPos());
            modes[i] = buffer.readByte();
        }
        return new DebugPathEffectPacket(pos, modes);
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
        ClientHelper.renderPath(packet.pos(), packet.modes());
    }

}

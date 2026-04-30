package hive.common.world.packets;

import hive.client.render.ClientHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record DebugPathEffectPacket(List<BlockPos> pos, byte[] modes) implements CustomPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, DebugPathEffectPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), DebugPathEffectPacket::pos,
            ByteBufCodecs.BYTE_ARRAY, DebugPathEffectPacket::modes,
            DebugPathEffectPacket::new
    );

    public static void handle(DebugPathEffectPacket packet, IPayloadContext context) {
        ClientHelper.renderPath(packet.pos(), packet.modes());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PacketRegistry.DEBUG_PATH;
    }

}

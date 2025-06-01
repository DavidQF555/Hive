package hive.common.world.packets;

import hive.client.render.ClientHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record PathEffectPacket(List<BlockPos> pos) implements CustomPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, PathEffectPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), PathEffectPacket::pos,
            PathEffectPacket::new
    );

    public static void handle(PathEffectPacket packet, IPayloadContext context) {
        ClientHelper.renderPath(packet.pos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PacketRegistry.PATH;
    }

}

package dev.autostock.net;

import dev.autostock.core.DraftCodec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

public record FreezeRequest(UUID requestId, String json) implements CustomPayload {
    public static final Id<FreezeRequest> ID = new Id<>(Identifier.of("autostock", "freeze_request_v2"));
    public static final PacketCodec<RegistryByteBuf, FreezeRequest> CODEC = new PacketCodec<>() {
        public FreezeRequest decode(RegistryByteBuf buf) { return new FreezeRequest(buf.readUuid(), buf.readString(DraftCodec.MAX_JSON_BYTES)); }
        public void encode(RegistryByteBuf buf, FreezeRequest value) { buf.writeUuid(value.requestId()); buf.writeString(value.json(), DraftCodec.MAX_JSON_BYTES); }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}

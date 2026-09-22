package dev.autostock.net;

import dev.autostock.core.DraftCodec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

public record DraftRequest(UUID requestId, String json) implements CustomPayload {
    public static final Id<DraftRequest> ID = new Id<>(Identifier.of("autostock", "draft_request_v2"));
    public static final PacketCodec<RegistryByteBuf, DraftRequest> CODEC = new PacketCodec<>() {
        @Override public DraftRequest decode(RegistryByteBuf buf) {
            return new DraftRequest(buf.readUuid(), buf.readString(DraftCodec.MAX_JSON_BYTES));
        }
        @Override public void encode(RegistryByteBuf buf, DraftRequest value) {
            buf.writeUuid(value.requestId());
            buf.writeString(value.json(), DraftCodec.MAX_JSON_BYTES);
        }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}

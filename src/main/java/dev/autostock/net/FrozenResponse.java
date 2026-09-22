package dev.autostock.net;

import dev.autostock.core.DraftCodec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

public record FrozenResponse(UUID requestId, UUID taskId, String json) implements CustomPayload {
    public static final Id<FrozenResponse> ID = new Id<>(Identifier.of("autostock", "frozen_response_v2"));
    public static final PacketCodec<RegistryByteBuf, FrozenResponse> CODEC = new PacketCodec<>() {
        public FrozenResponse decode(RegistryByteBuf buf) { return new FrozenResponse(buf.readUuid(), buf.readUuid(), buf.readString(DraftCodec.MAX_JSON_BYTES)); }
        public void encode(RegistryByteBuf buf, FrozenResponse value) { buf.writeUuid(value.requestId()); buf.writeUuid(value.taskId()); buf.writeString(value.json(), DraftCodec.MAX_JSON_BYTES); }
    };
    public Id<? extends CustomPayload> getId() { return ID; }
}

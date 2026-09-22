package dev.autostock.net;

import dev.autostock.core.DraftCodec;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FrozenResponse(UUID requestId, UUID taskId, String json) implements CustomPacketPayload {
    public static final Type<FrozenResponse> ID = new Type<>(Identifier.fromNamespaceAndPath("autostock", "frozen_response_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FrozenResponse> CODEC = new StreamCodec<>() {
        public FrozenResponse decode(RegistryFriendlyByteBuf buf) { return new FrozenResponse(buf.readUUID(), buf.readUUID(), buf.readUtf(DraftCodec.MAX_JSON_BYTES)); }
        public void encode(RegistryFriendlyByteBuf buf, FrozenResponse value) { buf.writeUUID(value.requestId()); buf.writeUUID(value.taskId()); buf.writeUtf(value.json(), DraftCodec.MAX_JSON_BYTES); }
    };
    public Type<? extends CustomPacketPayload> type() { return ID; }
}

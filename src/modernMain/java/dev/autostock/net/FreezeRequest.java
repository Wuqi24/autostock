package dev.autostock.net;

import dev.autostock.core.DraftCodec;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FreezeRequest(UUID requestId, String json) implements CustomPacketPayload {
    public static final Type<FreezeRequest> ID = new Type<>(Identifier.fromNamespaceAndPath("autostock", "freeze_request_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FreezeRequest> CODEC = new StreamCodec<>() {
        public FreezeRequest decode(RegistryFriendlyByteBuf buf) { return new FreezeRequest(buf.readUUID(), buf.readUtf(DraftCodec.MAX_JSON_BYTES)); }
        public void encode(RegistryFriendlyByteBuf buf, FreezeRequest value) { buf.writeUUID(value.requestId()); buf.writeUtf(value.json(), DraftCodec.MAX_JSON_BYTES); }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}

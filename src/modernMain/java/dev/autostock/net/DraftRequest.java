package dev.autostock.net;

import dev.autostock.core.DraftCodec;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DraftRequest(UUID requestId, String json) implements CustomPacketPayload {
    public static final Type<DraftRequest> ID = new Type<>(Identifier.fromNamespaceAndPath("autostock", "draft_request_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DraftRequest> CODEC = new StreamCodec<>() {
        @Override public DraftRequest decode(RegistryFriendlyByteBuf buf) {
            return new DraftRequest(buf.readUUID(), buf.readUtf(DraftCodec.MAX_JSON_BYTES));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, DraftRequest value) {
            buf.writeUUID(value.requestId());
            buf.writeUtf(value.json(), DraftCodec.MAX_JSON_BYTES);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}

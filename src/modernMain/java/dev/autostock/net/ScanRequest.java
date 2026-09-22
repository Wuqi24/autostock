package dev.autostock.net;

import dev.autostock.core.DraftCodec;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ScanRequest(UUID requestId, String json) implements CustomPacketPayload {
    public static final Type<ScanRequest> ID = new Type<>(Identifier.fromNamespaceAndPath("autostock", "scan_request_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScanRequest> CODEC = new StreamCodec<>() {
        public ScanRequest decode(RegistryFriendlyByteBuf buf) { return new ScanRequest(buf.readUUID(), buf.readUtf(DraftCodec.MAX_JSON_BYTES)); }
        public void encode(RegistryFriendlyByteBuf buf, ScanRequest value) {
            buf.writeUUID(value.requestId()); buf.writeUtf(value.json(), DraftCodec.MAX_JSON_BYTES);
        }
    };
    public Type<? extends CustomPacketPayload> type() { return ID; }
}

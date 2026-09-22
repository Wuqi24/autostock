package dev.autostock.net;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ScanResponse(UUID requestId, String json, boolean frozen) implements CustomPacketPayload {
    public ScanResponse(UUID requestId, String json) { this(requestId, json, false); }
    public static final int MAX_BYTES = 240_000;
    public static final Type<ScanResponse> ID = new Type<>(Identifier.fromNamespaceAndPath("autostock", "scan_response_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScanResponse> CODEC = new StreamCodec<>() {
        public ScanResponse decode(RegistryFriendlyByteBuf buf) { return new ScanResponse(buf.readUUID(), buf.readUtf(MAX_BYTES), buf.readBoolean()); }
        public void encode(RegistryFriendlyByteBuf buf, ScanResponse value) {
            buf.writeUUID(value.requestId()); buf.writeUtf(value.json(), MAX_BYTES);
            buf.writeBoolean(value.frozen());
        }
    };
    public Type<? extends CustomPacketPayload> type() { return ID; }
}

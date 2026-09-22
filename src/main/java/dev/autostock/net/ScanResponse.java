package dev.autostock.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

public record ScanResponse(UUID requestId, String json, boolean frozen) implements CustomPayload {
    public ScanResponse(UUID requestId, String json) { this(requestId, json, false); }
    public static final int MAX_BYTES = 240_000;
    public static final Id<ScanResponse> ID = new Id<>(Identifier.of("autostock", "scan_response_v2"));
    public static final PacketCodec<RegistryByteBuf, ScanResponse> CODEC = new PacketCodec<>() {
        public ScanResponse decode(RegistryByteBuf buf) { return new ScanResponse(buf.readUuid(), buf.readString(MAX_BYTES), buf.readBoolean()); }
        public void encode(RegistryByteBuf buf, ScanResponse value) {
            buf.writeUuid(value.requestId()); buf.writeString(value.json(), MAX_BYTES);
            buf.writeBoolean(value.frozen());
        }
    };
    public Id<? extends CustomPayload> getId() { return ID; }
}

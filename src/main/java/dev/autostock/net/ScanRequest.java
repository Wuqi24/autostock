package dev.autostock.net;

import dev.autostock.core.DraftCodec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

public record ScanRequest(UUID requestId, String json) implements CustomPayload {
    public static final Id<ScanRequest> ID = new Id<>(Identifier.of("autostock", "scan_request_v2"));
    public static final PacketCodec<RegistryByteBuf, ScanRequest> CODEC = new PacketCodec<>() {
        public ScanRequest decode(RegistryByteBuf buf) { return new ScanRequest(buf.readUuid(), buf.readString(DraftCodec.MAX_JSON_BYTES)); }
        public void encode(RegistryByteBuf buf, ScanRequest value) {
            buf.writeUuid(value.requestId()); buf.writeString(value.json(), DraftCodec.MAX_JSON_BYTES);
        }
    };
    public Id<? extends CustomPayload> getId() { return ID; }
}

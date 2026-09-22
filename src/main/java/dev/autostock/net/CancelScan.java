package dev.autostock.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

public record CancelScan(UUID requestId) implements CustomPayload {
    public static final Id<CancelScan> ID = new Id<>(Identifier.of("autostock", "cancel_scan_v2"));
    public static final PacketCodec<RegistryByteBuf, CancelScan> CODEC = new PacketCodec<>() {
        public CancelScan decode(RegistryByteBuf buf) { return new CancelScan(buf.readUuid()); }
        public void encode(RegistryByteBuf buf, CancelScan value) { buf.writeUuid(value.requestId()); }
    };
    public Id<? extends CustomPayload> getId() { return ID; }
}

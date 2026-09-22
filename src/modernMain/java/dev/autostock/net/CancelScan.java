package dev.autostock.net;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CancelScan(UUID requestId) implements CustomPacketPayload {
    public static final Type<CancelScan> ID = new Type<>(Identifier.fromNamespaceAndPath("autostock", "cancel_scan_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CancelScan> CODEC = new StreamCodec<>() {
        public CancelScan decode(RegistryFriendlyByteBuf buf) { return new CancelScan(buf.readUUID()); }
        public void encode(RegistryFriendlyByteBuf buf, CancelScan value) { buf.writeUUID(value.requestId()); }
    };
    public Type<? extends CustomPacketPayload> type() { return ID; }
}

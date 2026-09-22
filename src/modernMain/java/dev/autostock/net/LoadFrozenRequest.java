package dev.autostock.net;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record LoadFrozenRequest(UUID requestId, UUID taskId) implements CustomPacketPayload {
    public static final Type<LoadFrozenRequest> ID = new Type<>(Identifier.fromNamespaceAndPath("autostock", "load_frozen_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LoadFrozenRequest> CODEC = new StreamCodec<>() {
        public LoadFrozenRequest decode(RegistryFriendlyByteBuf buf) { return new LoadFrozenRequest(buf.readUUID(), buf.readUUID()); }
        public void encode(RegistryFriendlyByteBuf buf, LoadFrozenRequest value) { buf.writeUUID(value.requestId()); buf.writeUUID(value.taskId()); }
    };
    public Type<? extends CustomPacketPayload> type() { return ID; }
}

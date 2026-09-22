package dev.autostock.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

public record LoadFrozenRequest(UUID requestId, UUID taskId) implements CustomPayload {
    public static final Id<LoadFrozenRequest> ID = new Id<>(Identifier.of("autostock", "load_frozen_v2"));
    public static final PacketCodec<RegistryByteBuf, LoadFrozenRequest> CODEC = new PacketCodec<>() {
        public LoadFrozenRequest decode(RegistryByteBuf buf) { return new LoadFrozenRequest(buf.readUuid(), buf.readUuid()); }
        public void encode(RegistryByteBuf buf, LoadFrozenRequest value) { buf.writeUuid(value.requestId()); buf.writeUuid(value.taskId()); }
    };
    public Id<? extends CustomPayload> getId() { return ID; }
}

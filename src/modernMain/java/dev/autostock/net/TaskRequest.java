package dev.autostock.net;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TaskRequest(UUID taskId,boolean cancel) implements CustomPacketPayload {
    public static final Type<TaskRequest> ID=new Type<>(Identifier.fromNamespaceAndPath("autostock","task_v4"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TaskRequest> CODEC=new StreamCodec<>() {
        public TaskRequest decode(RegistryFriendlyByteBuf buf) { return new TaskRequest(buf.readUUID(),buf.readBoolean()); }
        public void encode(RegistryFriendlyByteBuf buf,TaskRequest value) { buf.writeUUID(value.taskId());buf.writeBoolean(value.cancel()); }
    };
    public Type<? extends CustomPacketPayload> type() { return ID; }
}

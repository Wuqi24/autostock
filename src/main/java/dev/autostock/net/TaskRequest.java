package dev.autostock.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

public record TaskRequest(UUID taskId,boolean cancel) implements CustomPayload {
    public static final Id<TaskRequest> ID=new Id<>(Identifier.of("autostock","task_v4"));
    public static final PacketCodec<RegistryByteBuf,TaskRequest> CODEC=new PacketCodec<>() {
        public TaskRequest decode(RegistryByteBuf buf) { return new TaskRequest(buf.readUuid(),buf.readBoolean()); }
        public void encode(RegistryByteBuf buf,TaskRequest value) { buf.writeUuid(value.taskId());buf.writeBoolean(value.cancel()); }
    };
    public Id<? extends CustomPayload> getId() { return ID; }
}

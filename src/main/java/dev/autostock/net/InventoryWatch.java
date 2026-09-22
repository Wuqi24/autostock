package dev.autostock.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

public record InventoryWatch(UUID session,boolean active) implements CustomPayload {
    public static final Id<InventoryWatch> ID=new Id<>(Identifier.of("autostock","inventory_watch_v1"));
    public static final PacketCodec<RegistryByteBuf,InventoryWatch> CODEC=new PacketCodec<>() {
        public InventoryWatch decode(RegistryByteBuf b){return new InventoryWatch(b.readUuid(),b.readBoolean());}
        public void encode(RegistryByteBuf b,InventoryWatch v){b.writeUuid(v.session);b.writeBoolean(v.active);}
    };
    public Id<? extends CustomPayload> getId(){return ID;}
}

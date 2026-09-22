package dev.autostock.net;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record InventoryWatch(UUID session,boolean active) implements CustomPacketPayload {
    public static final Type<InventoryWatch> ID=new Type<>(Identifier.fromNamespaceAndPath("autostock","inventory_watch_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,InventoryWatch> CODEC=new StreamCodec<>() {
        public InventoryWatch decode(RegistryFriendlyByteBuf b){return new InventoryWatch(b.readUUID(),b.readBoolean());}
        public void encode(RegistryFriendlyByteBuf b,InventoryWatch v){b.writeUUID(v.session);b.writeBoolean(v.active);}
    };
    public Type<? extends CustomPacketPayload> type(){return ID;}
}

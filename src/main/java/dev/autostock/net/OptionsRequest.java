package dev.autostock.net;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
public record OptionsRequest(String json) implements CustomPayload {
 public static final Id<OptionsRequest> ID=new Id<>(Identifier.of("autostock","options_v1"));
 public static final PacketCodec<RegistryByteBuf,OptionsRequest> CODEC=new PacketCodec<>(){public OptionsRequest decode(RegistryByteBuf b){return new OptionsRequest(b.readString(2048));}public void encode(RegistryByteBuf b,OptionsRequest v){b.writeString(v.json,2048);}};
 public Id<? extends CustomPayload> getId(){return ID;}
}
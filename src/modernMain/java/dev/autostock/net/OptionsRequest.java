package dev.autostock.net;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record OptionsRequest(String json) implements CustomPacketPayload {
 public static final Type<OptionsRequest> ID=new Type<>(Identifier.fromNamespaceAndPath("autostock","options_v1"));
 public static final StreamCodec<RegistryFriendlyByteBuf,OptionsRequest> CODEC=new StreamCodec<>(){public OptionsRequest decode(RegistryFriendlyByteBuf b){return new OptionsRequest(b.readUtf(2048));}public void encode(RegistryFriendlyByteBuf b,OptionsRequest v){b.writeUtf(v.json,2048);}};
 public Type<? extends CustomPacketPayload> type(){return ID;}
}
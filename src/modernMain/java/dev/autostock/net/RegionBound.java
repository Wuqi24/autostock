package dev.autostock.net;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record RegionBound(String json) implements CustomPacketPayload {public static final Type<RegionBound> ID=new Type<>(Identifier.fromNamespaceAndPath("autostock","region_bound_v1"));public static final StreamCodec<RegistryFriendlyByteBuf,RegionBound> CODEC=new StreamCodec<>(){public RegionBound decode(RegistryFriendlyByteBuf b){return new RegionBound(b.readUtf(32768));}public void encode(RegistryFriendlyByteBuf b,RegionBound v){b.writeUtf(v.json,32768);}};public Type<? extends CustomPacketPayload> type(){return ID;}}
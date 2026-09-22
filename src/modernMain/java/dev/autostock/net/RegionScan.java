package dev.autostock.net;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record RegionScan(String json) implements CustomPacketPayload {public static final Type<RegionScan> ID=new Type<>(Identifier.fromNamespaceAndPath("autostock","region_scan_v1"));public static final StreamCodec<RegistryFriendlyByteBuf,RegionScan> CODEC=new StreamCodec<>(){public RegionScan decode(RegistryFriendlyByteBuf b){return new RegionScan(b.readUtf(32768));}public void encode(RegistryFriendlyByteBuf b,RegionScan v){b.writeUtf(v.json,32768);}};public Type<? extends CustomPacketPayload> type(){return ID;}}
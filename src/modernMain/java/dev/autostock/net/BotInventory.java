package dev.autostock.net;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record BotInventory(UUID session,UUID bot,long revision,String state,String dimension,BlockPos position,float health,List<ItemStack> slots,List<Integer> categories) implements CustomPacketPayload {
    public BotInventory {
        if(slots.size()!=36||categories.size()!=36||revision<0||categories.stream().anyMatch(i->i<0||i>3))throw new IllegalArgumentException("背包快照无效");
        slots=slots.stream().map(ItemStack::copy).toList();categories=List.copyOf(categories);
    }
    public static final Type<BotInventory> ID=new Type<>(Identifier.fromNamespaceAndPath("autostock","bot_inventory_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BotInventory> CODEC=new StreamCodec<>() {
        public BotInventory decode(RegistryFriendlyByteBuf b){
            var session=b.readUUID();var bot=b.readUUID();long revision=b.readLong();var state=b.readUtf(32);var dimension=b.readUtf(128);var pos=b.readBlockPos();float health=b.readFloat();
            var slots=new ArrayList<ItemStack>();var categories=new ArrayList<Integer>();
            for(int i=0;i<36;i++){slots.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(b));categories.add((int)b.readByte());}
            return new BotInventory(session,bot,revision,state,dimension,pos,health,slots,categories);
        }
        public void encode(RegistryFriendlyByteBuf b,BotInventory v){
            b.writeUUID(v.session);b.writeUUID(v.bot);b.writeLong(v.revision);b.writeUtf(v.state,32);b.writeUtf(v.dimension,128);b.writeBlockPos(v.position);b.writeFloat(v.health);
            for(int i=0;i<36;i++){ItemStack.OPTIONAL_STREAM_CODEC.encode(b,v.slots.get(i));b.writeByte(v.categories.get(i));}
        }
    };
    public Type<? extends CustomPacketPayload> type(){return ID;}
}

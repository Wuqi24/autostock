package dev.autostock.net;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.*;

public record BotInventory(UUID session,UUID bot,long revision,String state,String dimension,BlockPos position,float health,List<ItemStack> slots,List<Integer> categories) implements CustomPayload {
    public BotInventory {
        if(slots.size()!=36||categories.size()!=36||revision<0||categories.stream().anyMatch(i->i<0||i>3))throw new IllegalArgumentException("背包快照无效");
        slots=slots.stream().map(ItemStack::copy).toList();categories=List.copyOf(categories);
    }
    public static final Id<BotInventory> ID=new Id<>(Identifier.of("autostock","bot_inventory_v1"));
    public static final PacketCodec<RegistryByteBuf,BotInventory> CODEC=new PacketCodec<>() {
        public BotInventory decode(RegistryByteBuf b){
            var session=b.readUuid();var bot=b.readUuid();long revision=b.readLong();var state=b.readString(32);var dimension=b.readString(128);var pos=b.readBlockPos();float health=b.readFloat();
            var slots=new ArrayList<ItemStack>();var categories=new ArrayList<Integer>();
            for(int i=0;i<36;i++){slots.add(ItemStack.OPTIONAL_PACKET_CODEC.decode(b));categories.add((int)b.readByte());}
            return new BotInventory(session,bot,revision,state,dimension,pos,health,slots,categories);
        }
        public void encode(RegistryByteBuf b,BotInventory v){
            b.writeUuid(v.session);b.writeUuid(v.bot);b.writeLong(v.revision);b.writeString(v.state,32);b.writeString(v.dimension,128);b.writeBlockPos(v.position);b.writeFloat(v.health);
            for(int i=0;i<36;i++){ItemStack.OPTIONAL_PACKET_CODEC.encode(b,v.slots.get(i));b.writeByte(v.categories.get(i));}
        }
    };
    public Id<? extends CustomPayload> getId(){return ID;}
}

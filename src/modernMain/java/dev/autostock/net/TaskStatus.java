package dev.autostock.net;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TaskStatus(UUID taskId,String state,String detail,long delivered,long inTransit,long required,
                         BlockPos target,List<BlockPos> path,boolean bindingsVerified,String material,long materialRemaining,long etaSeconds) implements CustomPacketPayload {
    public TaskStatus(UUID taskId,String state,String detail,long delivered,long inTransit,long required,BlockPos target,List<BlockPos> path,boolean verified){this(taskId,state,detail,delivered,inTransit,required,target,path,verified,"",0,-1);}
    public TaskStatus(UUID taskId,String state,String detail,long delivered,long inTransit,long required,BlockPos target,List<BlockPos> path){this(taskId,state,detail,delivered,inTransit,required,target,path,false);}
    public TaskStatus { path=List.copyOf(path); if(path.size()>256)throw new IllegalArgumentException("路径状态过长"); }
    public static final Type<TaskStatus> ID=new Type<>(Identifier.fromNamespaceAndPath("autostock","task_status_v4"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TaskStatus> CODEC=new StreamCodec<>() {
        public TaskStatus decode(RegistryFriendlyByteBuf buf) {
            var task=buf.readUUID();var state=buf.readUtf(32);var detail=buf.readUtf(512);
            long w=buf.readLong(),t=buf.readLong(),d=buf.readLong(); var target=buf.readBoolean()?buf.readBlockPos():null;
            int count=buf.readVarInt();if(count<0||count>256)throw new IllegalArgumentException("路径状态过长");
            var path=new ArrayList<BlockPos>();for(int i=0;i<count;i++)path.add(buf.readBlockPos());
            return new TaskStatus(task,state,detail,w,t,d,target,path,buf.readBoolean(),buf.readUtf(128),buf.readLong(),buf.readLong());
        }
        public void encode(RegistryFriendlyByteBuf buf,TaskStatus value) {
            buf.writeUUID(value.taskId());buf.writeUtf(value.state(),32);buf.writeUtf(value.detail(),512);
            buf.writeLong(value.delivered());buf.writeLong(value.inTransit());buf.writeLong(value.required());
            buf.writeBoolean(value.target()!=null);if(value.target()!=null)buf.writeBlockPos(value.target());
            buf.writeVarInt(value.path().size());value.path().forEach(buf::writeBlockPos);buf.writeBoolean(value.bindingsVerified());buf.writeUtf(value.material(),128);buf.writeLong(value.materialRemaining());buf.writeLong(value.etaSeconds());
        }
    };
    public Type<? extends CustomPacketPayload> type() { return ID; }
}


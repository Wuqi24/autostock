package dev.autostock.net;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.ArrayList;
import dev.autostock.core.ScanReport;
import dev.autostock.core.RegionRole;

public record DraftResponse(UUID requestId, boolean accepted, String message, String issue, List<ScanReport.Mark> marks) implements CustomPacketPayload {
    public DraftResponse { marks = List.copyOf(marks); if (marks.size() > 32) throw new IllegalArgumentException("Too many error markers"); }
    public DraftResponse(UUID id, boolean accepted, String message) { this(id, accepted, message, "", List.of()); }
    public static final Type<DraftResponse> ID = new Type<>(Identifier.fromNamespaceAndPath("autostock", "draft_response_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DraftResponse> CODEC = new StreamCodec<>() {
        @Override public DraftResponse decode(RegistryFriendlyByteBuf buf) {
            var id = buf.readUUID(); boolean accepted = buf.readBoolean(); String message = buf.readUtf(256);
            String issue = buf.readUtf(32); int count = buf.readVarInt();
            if (count < 0 || count > 32) throw new IllegalArgumentException("Invalid error marker count");
            var marks = new ArrayList<ScanReport.Mark>();
            for (int i = 0; i < count; i++) marks.add(new ScanReport.Mark(buf.readInt(), buf.readInt(), buf.readInt(), RegionRole.OUTPUT));
            return new DraftResponse(id, accepted, message, issue, marks);
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, DraftResponse value) {
            buf.writeUUID(value.requestId());
            buf.writeBoolean(value.accepted());
            buf.writeUtf(value.message(), 256);
            buf.writeUtf(value.issue(), 32); buf.writeVarInt(value.marks().size());
            for (var mark : value.marks()) { buf.writeInt(mark.x()); buf.writeInt(mark.y()); buf.writeInt(mark.z()); }
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}

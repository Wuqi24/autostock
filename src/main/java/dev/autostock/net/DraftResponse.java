package dev.autostock.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import dev.autostock.core.ScanReport;
import dev.autostock.core.RegionRole;

public record DraftResponse(UUID requestId, boolean accepted, String message, String issue, List<ScanReport.Mark> marks) implements CustomPayload {
    public DraftResponse { marks = List.copyOf(marks); if (marks.size() > 32) throw new IllegalArgumentException("Too many error markers"); }
    public DraftResponse(UUID id, boolean accepted, String message) { this(id, accepted, message, "", List.of()); }
    public static final Id<DraftResponse> ID = new Id<>(Identifier.of("autostock", "draft_response_v2"));
    public static final PacketCodec<RegistryByteBuf, DraftResponse> CODEC = new PacketCodec<>() {
        @Override public DraftResponse decode(RegistryByteBuf buf) {
            var id = buf.readUuid(); boolean accepted = buf.readBoolean(); String message = buf.readString(256);
            String issue = buf.readString(32); int count = buf.readVarInt();
            if (count < 0 || count > 32) throw new IllegalArgumentException("Invalid error marker count");
            var marks = new ArrayList<ScanReport.Mark>();
            for (int i = 0; i < count; i++) marks.add(new ScanReport.Mark(buf.readInt(), buf.readInt(), buf.readInt(), RegionRole.OUTPUT));
            return new DraftResponse(id, accepted, message, issue, marks);
        }
        @Override public void encode(RegistryByteBuf buf, DraftResponse value) {
            buf.writeUuid(value.requestId());
            buf.writeBoolean(value.accepted());
            buf.writeString(value.message(), 256);
            buf.writeString(value.issue(), 32); buf.writeVarInt(value.marks().size());
            for (var mark : value.marks()) { buf.writeInt(mark.x()); buf.writeInt(mark.y()); buf.writeInt(mark.z()); }
        }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}

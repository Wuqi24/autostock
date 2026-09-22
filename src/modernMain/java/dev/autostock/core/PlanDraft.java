package dev.autostock.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Immutable client proposal. It grants no access to inventories and is not an executable task. */
public record PlanDraft(int protocol, String dimension, String schematicName,
                        Map<RegionRole, List<RegionBox>> regions, Map<String, Long> demand,
                        int multiplier, boolean quickShulker) {
    public static final int PROTOCOL = 2;
    public static final long MAX_REGION_VOLUME = 262_144;
    public static final int MAX_ITEM_TYPES = 512;
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public PlanDraft(int protocol, String dimension, String schematicName,
                     Map<RegionRole, List<RegionBox>> regions, Map<String, Long> demand) {
        this(protocol, dimension, schematicName, regions, demand, 1, true);
    }

    public PlanDraft {
        if (protocol != PROTOCOL) throw new IllegalArgumentException("客户端与服务端协议版本不一致");
        if (multiplier < 1) throw new IllegalArgumentException("Litematica 倍率无效");
        if (dimension == null || dimension.length() > 128 || !ID.matcher(dimension).matches()) {
            throw new IllegalArgumentException("维度标识无效");
        }
        if (schematicName == null || schematicName.isBlank() || schematicName.length() > 128) {
            throw new IllegalArgumentException("原理图名称为空或过长");
        }
        if (regions == null || regions.size() != RegionRole.values().length) {
            throw new IllegalArgumentException("请先读取材料区、空盒区、备货区三份选区");
        }
        var copied = new EnumMap<RegionRole, List<RegionBox>>(RegionRole.class);
        for (var role : RegionRole.values()) {
            var boxes = regions.get(role);
            if (boxes == null || boxes.isEmpty() || boxes.size() > 32) {
                throw new IllegalArgumentException(role.label() + "需包含 1–32 个子选区");
            }
            long volume = 0;
            for (var box : boxes) {
                if (box == null) throw new IllegalArgumentException("子选区不能为空");
                volume = Math.addExact(volume, box.volume());
                if (volume > MAX_REGION_VOLUME) {
                    throw new IllegalArgumentException(role.label() + "子选区体积合计超过 262144 方块");
                }
            }
            copied.put(role, List.copyOf(boxes));
        }
        for (var empty : copied.get(RegionRole.EMPTY_BOX)) {
            for (var output : copied.get(RegionRole.OUTPUT)) {
                if (empty.overlaps(output)) throw new IllegalArgumentException("空盒区与备货区不能重叠");
            }
        }
        if (demand == null || demand.size() > MAX_ITEM_TYPES) {
            throw new IllegalArgumentException("材料种类超过基础版上限 512");
        }
        var counts = new TreeMap<String, Long>();
        long total = 0;
        for (var entry : demand.entrySet()) {
            if (entry.getKey() == null || entry.getKey().length() > 128 || !ID.matcher(entry.getKey()).matches()
                    || entry.getValue() == null || entry.getValue() <= 0 || entry.getValue() > 1_000_000_000L) {
                throw new IllegalArgumentException("材料标识或数量无效");
            }
            total = Math.addExact(total, entry.getValue());
            if (total > 1_000_000_000L) throw new IllegalArgumentException("总材料数量超过基础版上限");
            counts.put(entry.getKey(), entry.getValue());
        }
        regions = Collections.unmodifiableMap(copied);
        demand = Collections.unmodifiableMap(counts);
    }

    public long totalItems() { return demand.values().stream().mapToLong(Long::longValue).sum(); }
}

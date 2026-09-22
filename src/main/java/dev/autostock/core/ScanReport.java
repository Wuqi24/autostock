package dev.autostock.core;

import java.util.List;

public record ScanReport(String dimension, String schematicName, StockPlanner.Result plan,
                         long emptyBoxes, int outputSlots, int materialContainers,
                         int emptyContainers, int outputContainers, List<Mark> marks,
                         List<String> notes, long scanTicks) {
    public ScanReport {
        if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || dimension.length() > 128
                || schematicName == null || schematicName.length() > 128 || plan == null || plan.rows() == null
                || plan.preview() == null || plan.rows().size() > 512 || plan.preview().size() > 8 || plan.boxes() < 0
                || marks == null || marks.size() > 256 || notes == null || notes.size() > 16
                || emptyBoxes < 0 || outputSlots < 0 || scanTicks < 0) throw new IllegalArgumentException("扫描报告无效");
        for (var row : plan.rows()) {
            if (row == null || row.item() == null || !row.item().matches("[a-z0-9_.-]+:[a-z0-9/._-]+")
                    || row.required() < 0 || row.stocked() < 0 || row.inTransit() < 0 || row.pending() < 0 || row.available() < 0
                    || row.shortage() < 0 || row.sources() < 0) throw new IllegalArgumentException("扫描材料行无效");
        }
        for (var box : plan.preview()) {
            if (box == null || box.number() < 1 || box.number() > plan.boxes() || box.slots() == null || box.slots().size() > 27) {
                throw new IllegalArgumentException("装盒预览无效");
            }
            for (var slot : box.slots()) if (slot == null || slot.item() == null
                    || !slot.item().matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || slot.count() < 1 || slot.count() > slot.limit()) {
                throw new IllegalArgumentException("装盒槽位无效");
            }
        }
        for (var mark : marks) if (mark == null || mark.role() == null) throw new IllegalArgumentException("容器标记无效");
        marks = List.copyOf(marks);
        notes = List.copyOf(notes);
    }
    public record Mark(int x, int y, int z, RegionRole role,boolean shortage) {public Mark(int x,int y,int z,RegionRole role){this(x,y,z,role,false);} }
}

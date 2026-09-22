package dev.autostock.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure planning over an inventory snapshot. A variant denotes real stack compatibility. */
public final class StockPlanner {
    public record Supply(String item, String variant, int stackLimit, long count, boolean boxable) {
        public Supply {
            if (item == null || variant == null || stackLimit < 1 || stackLimit > 99 || count < 0) {
                throw new IllegalArgumentException("无效库存分组");
            }
        }
    }
    public record Row(String item, long required, long stocked, long inTransit, long pending, long available,
                      long shortage, int sources) { }
    public record Slot(String item, long count, int limit) { }
    public record Box(int number, List<Slot> slots) { }
    public record Result(List<Row> rows, long boxes, List<Box> preview, boolean complete,
                         long unboxableItems) { }
    private record Key(String item, String variant, int limit, boolean boxable) { }

    private StockPlanner() { }

    public static Result plan(Map<String, Long> demand, Map<String, Long> stocked,
                              List<Supply> supplies, Map<String, Integer> sourceCounts) {
        return plan(demand, stocked, Map.of(), supplies, sourceCounts);
    }

    public static Result plan(Map<String, Long> demand, Map<String, Long> stocked, Map<String, Long> inTransit,
                              List<Supply> supplies, Map<String, Integer> sourceCounts) {
        var grouped = new LinkedHashMap<Key, Long>();
        var available = new HashMap<String, Long>();
        for (var supply : supplies) {
            grouped.merge(new Key(supply.item(), supply.variant(), supply.stackLimit(), supply.boxable()),
                    supply.count(), Math::addExact);
            available.merge(supply.item(), supply.count(), Math::addExact);
        }
        var rows = new ArrayList<Row>();
        var remaining = new HashMap<String, Long>();
        boolean complete = true;
        for (var entry : new TreeMap<>(demand).entrySet()) {
            long required = entry.getValue();
            long existing = stocked.getOrDefault(entry.getKey(), 0L);
            long transit = inTransit.getOrDefault(entry.getKey(), 0L);
            if (required < 0 || existing < 0 || transit < 0) throw new IllegalArgumentException("物品数量不能为负");
            long pending = Math.max(required - existing, 0);
            pending -= Math.min(pending, transit);
            long stock = available.getOrDefault(entry.getKey(), 0L);
            long shortage = Math.max(pending - stock, 0);
            rows.add(new Row(entry.getKey(), required, existing, transit, pending, stock, shortage,
                    sourceCounts.getOrDefault(entry.getKey(), 0)));
            remaining.put(entry.getKey(), pending);
            if (shortage > 0) complete = false;
        }
        long slotCount = 0;
        long unboxable = 0;
        var previewSlots = new ArrayList<Slot>();
        for (var entry : grouped.entrySet()) {
            var key = entry.getKey();
            long use = Math.min(remaining.getOrDefault(key.item(), 0L), entry.getValue());
            if (use == 0) continue;
            remaining.put(key.item(), remaining.get(key.item()) - use);
            if (!key.boxable()) {
                unboxable = Math.addExact(unboxable, use);
                complete = false;
                continue;
            }
            slotCount = Math.addExact(slotCount, (use - 1) / key.limit() + 1);
            long previewRemaining = use;
            while (previewRemaining > 0 && previewSlots.size() < 8 * 27) {
                long count = Math.min(previewRemaining, key.limit());
                previewSlots.add(new Slot(key.item(), count, key.limit()));
                previewRemaining -= count;
            }
        }
        var boxes = new ArrayList<Box>();
        for (int i = 0; i < previewSlots.size(); i += 27) {
            boxes.add(new Box(i / 27 + 1, List.copyOf(previewSlots.subList(i, Math.min(i + 27, previewSlots.size())))));
        }
        return new Result(List.copyOf(rows), (slotCount + 26) / 27, List.copyOf(boxes), complete, unboxable);
    }
}

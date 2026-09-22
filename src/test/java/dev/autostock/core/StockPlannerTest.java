package dev.autostock.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class StockPlannerTest {
    @Test void transitDeductsFromPickupWithoutPretendingDelivered() {
        var row = StockPlanner.plan(java.util.Map.of("stone", 100L), java.util.Map.of("stone", 30L),
                java.util.Map.of("stone", 40L), java.util.List.of(), java.util.Map.of()).rows().getFirst();
        assertEquals(30, row.pending()); assertEquals(30, row.stocked()); assertEquals(40, row.inTransit());
        var over = StockPlanner.plan(java.util.Map.of("stone", 100L), java.util.Map.of("stone", Long.MAX_VALUE),
                java.util.Map.of("stone", Long.MAX_VALUE), java.util.List.of(), java.util.Map.of()).rows().getFirst();
        assertEquals(0, over.pending());
    }
    private StockPlanner.Supply supply(String id, String variant, int limit, long count) {
        return new StockPlanner.Supply(id, variant, limit, count, true);
    }
    @Test void deductsOnlyExistingStockAndReportsShortage() {
        var p = StockPlanner.plan(Map.of("stone", 1000L), Map.of("stone", 400L),
                List.of(supply("stone", "a", 64, 500)), Map.of("stone", 2));
        var row = p.rows().getFirst();
        assertEquals(600, row.pending()); assertEquals(100, row.shortage());
        assertEquals(2, row.sources()); assertFalse(p.complete());
        assertEquals(1, p.boxes());
        assertEquals(500, p.preview().getFirst().slots().stream().mapToLong(StockPlanner.Slot::count).sum());
    }
    @Test void mergesCompatibleStacksBeforeCountingSlots() {
        var p = StockPlanner.plan(Map.of("stone", 128L), Map.of(),
                List.of(supply("stone", "same", 64, 32), supply("stone", "same", 64, 96)), Map.of());
        assertEquals(2, p.preview().getFirst().slots().size()); assertTrue(p.complete());
    }
    @Test void sameItemDifferentComponentsRemainSeparate() {
        var p = StockPlanner.plan(Map.of("stone", 64L), Map.of(),
                List.of(supply("stone", "named", 64, 32), supply("stone", "plain", 64, 32)), Map.of());
        assertEquals(2, p.preview().getFirst().slots().size());
    }
    @Test void capacityUsesActualStackLimitsNot1728() {
        var p = StockPlanner.plan(Map.of("pearl", 432L, "tool", 1L), Map.of(),
                List.of(supply("pearl", "a", 16, 432), supply("tool", "a", 1, 1)), Map.of());
        assertEquals(2, p.boxes()); assertEquals(27, p.preview().getFirst().slots().size());
        assertEquals(1, p.preview().get(1).slots().size());
    }
    @Test void neverMovesOrPacksExcessStock() {
        var p = StockPlanner.plan(Map.of("stone", 20L), Map.of("stone", 30L),
                List.of(supply("stone", "a", 64, 100)), Map.of());
        assertEquals(0, p.boxes()); assertTrue(p.complete()); assertEquals(30, p.rows().getFirst().stocked());
    }
    @Test void noMaterialLossAtBoxBoundary() {
        var p = StockPlanner.plan(Map.of("stone", 1729L), Map.of(),
                List.of(supply("stone", "a", 64, 1800)), Map.of());
        assertEquals(2, p.boxes());
        assertEquals(1729, p.preview().stream().flatMap(b -> b.slots().stream()).mapToLong(StockPlanner.Slot::count).sum());
    }
    @Test void hugeDemandHasBoundedPreview() {
        var p = StockPlanner.plan(Map.of("stone", 1_000_000_000L), Map.of(),
                List.of(supply("stone", "a", 64, 1_000_000_000L)), Map.of());
        assertEquals(578704, p.boxes()); assertEquals(8, p.preview().size()); assertTrue(p.complete());
    }
    @Test void forbiddenContentsAreNotSilentlyPacked() {
        var p = StockPlanner.plan(Map.of("box", 1L), Map.of(),
                List.of(new StockPlanner.Supply("box", "a", 1, 1, false)), Map.of());
        assertEquals(0, p.boxes()); assertEquals(1, p.unboxableItems()); assertFalse(p.complete());
    }
}

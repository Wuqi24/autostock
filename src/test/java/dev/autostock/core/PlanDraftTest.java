package dev.autostock.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PlanDraftTest {
    private Map<RegionRole, List<RegionBox>> regions() {
        var result = new EnumMap<RegionRole, List<RegionBox>>(RegionRole.class);
        result.put(RegionRole.MATERIAL, List.of(new RegionBox(0, 64, 0, 10, 70, 10)));
        result.put(RegionRole.EMPTY_BOX, List.of(new RegionBox(0, 64, 0, 1, 65, 1)));
        result.put(RegionRole.OUTPUT, List.of(new RegionBox(5, 64, 5, 6, 65, 6)));
        return result;
    }

    private PlanDraft draft(Map<RegionRole, List<RegionBox>> regions, Map<String, Long> demand) {
        return new PlanDraft(PlanDraft.PROTOCOL, "minecraft:overworld", "测试原理图", regions, demand);
    }

    @Test void inclusiveCornersAndReverseSelection() {
        var box = RegionBox.between(5, 70, 5, 3, 68, 3);
        assertEquals(27, box.volume());
        assertTrue(box.overlaps(new RegionBox(5, 70, 5, 6, 71, 6)));
        assertFalse(box.overlaps(new RegionBox(6, 70, 5, 6, 71, 6)));
    }

    @Test void allowsMaterialOverlapWithBothOtherRoles() {
        assertDoesNotThrow(() -> draft(regions(), Map.of("minecraft:stone", 64L)));
    }

    @Test void rejectsAnyEmptyOutputIntersection() {
        var regions = regions();
        regions.put(RegionRole.OUTPUT, List.of(new RegionBox(1, 65, 1, 2, 66, 2)));
        assertThrows(IllegalArgumentException.class, () -> draft(regions, Map.of()));
    }

    @Test void rejectsHugeRegionBeforePotentialScan() {
        var regions = regions();
        regions.put(RegionRole.MATERIAL, List.of(new RegionBox(-30_000_000, -2048, -30_000_000,
                30_000_000, 2048, 30_000_000)));
        assertThrows(ArithmeticException.class, () -> draft(regions, Map.of()));
    }

    @Test void rejectsMissingRoleAndBadIdentifiers() {
        var regions = regions();
        regions.remove(RegionRole.OUTPUT);
        assertThrows(IllegalArgumentException.class, () -> draft(regions, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> draft(regions(), Map.of("../stone", 1L)));
    }

    @Test void rejectsNegativeZeroAndExcessiveCounts() {
        for (long count : new long[]{-1, 0, 1_000_000_001L}) {
            assertThrows(IllegalArgumentException.class, () -> draft(regions(), Map.of("minecraft:stone", count)));
        }
        assertThrows(IllegalArgumentException.class, () -> draft(regions(),
                Map.of("minecraft:stone", 600_000_000L, "minecraft:dirt", 600_000_000L)));
    }

    @Test void snapshotCannotChangeWhenCallerMutatesInputs() {
        var regions = regions();
        var boxes = new ArrayList<>(regions.get(RegionRole.MATERIAL));
        regions.put(RegionRole.MATERIAL, boxes);
        var counts = new HashMap<>(Map.of("minecraft:stone", 100L));
        var draft = draft(regions, counts);
        counts.put("minecraft:stone", 1L);
        boxes.clear();
        assertEquals(100, draft.totalItems());
        assertEquals(1, draft.regions().get(RegionRole.MATERIAL).size());
        assertThrows(UnsupportedOperationException.class, () -> draft.demand().clear());
    }

    @Test void roundtripAndHashIndependentOfItemInsertionOrder() {
        var a = new HashMap<String, Long>();
        a.put("minecraft:stone", 5L); a.put("minecraft:dirt", 6L);
        var b = new HashMap<String, Long>();
        b.put("minecraft:dirt", 6L); b.put("minecraft:stone", 5L);
        var draft = draft(regions(), a);
        assertEquals(draft, DraftCodec.decode(DraftCodec.encode(draft)));
        assertEquals(DraftCodec.hash(draft), DraftCodec.hash(draft(regions(), b)));
    }

    @Test void networkDecodeCannotBypassConstructorValidation() {
        var json = DraftCodec.encode(draft(regions(), Map.of("minecraft:stone", 5L)));
        assertThrows(RuntimeException.class, () -> DraftCodec.decode(json.replace("\"protocol\":2", "\"protocol\":9")));
        assertThrows(RuntimeException.class, () -> DraftCodec.decode(json.replace("\"minecraft:stone\":5", "\"minecraft:stone\":-5")));
        assertThrows(RuntimeException.class, () -> DraftCodec.decode("null"));
        assertThrows(RuntimeException.class, () -> DraftCodec.decode("{}"));
    }

    @Test void boundsPayloadByBytesNotCharacters() {
        assertThrows(IllegalArgumentException.class, () -> DraftCodec.decode("测".repeat(8_001)));
    }

    @Test void emptySchematicNeedsNoMaterials() {
        var draft = draft(regions(), Map.of());
        assertEquals(0, draft.totalItems());
        assertEquals(draft, DraftCodec.decode(DraftCodec.encode(draft)));
    }
}


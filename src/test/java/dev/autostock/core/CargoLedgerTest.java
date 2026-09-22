package dev.autostock.core;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static dev.autostock.core.CargoLedger.*;

class CargoLedgerTest {
    @Test void sourceDispositionChangesOnlyAfterVerifiedEmptyAndPausePreservesIt() {
        var ledger = new CargoLedger(); var box = receipt(Kind.SOURCE_BOX, a, 1);
        ledger.recordVerifiedReceipt(box);
        assertEquals(Target.SOURCE, ledger.sourceDisposition(box.id()));
        ledger.sourceBoxVerifiedEmpty(box.id());
        assertEquals(Target.HOTBAR, ledger.sourceDisposition(box.id()));
        ledger.pause();
        assertEquals(State.PAUSED, ledger.state());
        assertEquals(Target.PRESERVE, ledger.sourceDisposition(box.id()));
        assertEquals(1, ledger.remaining(box.id()));
        ledger.resumeAfterWorldRecheck();
        assertEquals(Target.HOTBAR, ledger.sourceDisposition(box.id()));
        ledger.beginCancellation();
        assertThrows(IllegalArgumentException.class, ledger::resumeAfterWorldRecheck);
    }
    private final Origin a = new Origin("minecraft:overworld", "chestA", 0);
    private final Origin b = new Origin("minecraft:overworld", "chestB", 1);
    private Receipt receipt(Kind kind, Origin origin, long count) {
        return new Receipt(UUID.randomUUID(), kind, origin, kind == Kind.LOOSE_MATERIAL ? "minecraft:stone" : "minecraft:shulker_box", count);
    }

    @Test void sameItemFromDifferentContainersKeepsBothOrigins() {
        var ledger = new CargoLedger();
        var first = receipt(Kind.LOOSE_MATERIAL, a, 12);
        var second = receipt(Kind.LOOSE_MATERIAL, b, 20);
        ledger.recordVerifiedReceipt(first); ledger.recordVerifiedReceipt(second); ledger.beginCancellation();
        var orders = ledger.cancellationOrders();
        assertEquals(2, orders.size()); assertEquals(a, orders.get(0).origin()); assertEquals(b, orders.get(1).origin());
        assertEquals(12, orders.get(0).count()); assertEquals(20, orders.get(1).count());
    }

    @Test void borrowedSourceBoxNeverTurnsIntoAnEmptyBoxByInference() {
        var ledger = new CargoLedger();
        ledger.recordVerifiedReceipt(receipt(Kind.SOURCE_BOX, a, 1)); ledger.beginCancellation();
        assertEquals(Target.PRESERVE, ledger.cancellationOrders().getFirst().target());
    }

    @Test void cancellationPreservesAllCargoAndPersonalItems() {
        var ledger = new CargoLedger();
        for (var kind : Kind.values()) ledger.recordVerifiedReceipt(receipt(kind, a, 1));
        ledger.beginCancellation();
        assertEquals(java.util.Collections.nCopies(5, Target.PRESERVE),
                ledger.cancellationOrders().stream().map(Order::target).toList());
    }

    @Test void fullContainerPreservesUnreturnedQuantityAndReportsWaiting() {
        var ledger = new CargoLedger(); var material = receipt(Kind.LOOSE_MATERIAL, a, 32);
        ledger.recordVerifiedReceipt(material); ledger.beginCancellation();
        ledger.recordVerifiedReturn(UUID.randomUUID(), material.id(), 10);
        ledger.reportBlocked(material.id(), "来源和背包均满，保留现场");
        assertEquals(22, ledger.remaining(material.id())); assertEquals(State.CANCELLED, ledger.state());
        assertEquals(Target.PRESERVE, ledger.cancellationOrders().getFirst().target());
    }

    @Test void emergencyOverridesCancellationWithoutRemovingCargo() {
        var ledger = new CargoLedger(); var box = receipt(Kind.FINISHED_BOX, null, 1);
        ledger.recordVerifiedReceipt(box); ledger.beginCancellation(); ledger.emergencyStop(); ledger.beginCancellation();
        assertEquals(State.EMERGENCY, ledger.state()); assertEquals(1, ledger.remaining(box.id()));
        assertEquals(Target.PRESERVE, ledger.cancellationOrders().getFirst().target());
    }

    @Test void duplicateReceiptsDoNotCountOrDebitTwiceAndConflictsFail() {
        var ledger = new CargoLedger(); var material = receipt(Kind.LOOSE_MATERIAL, a, 32);
        ledger.recordVerifiedReceipt(material); ledger.recordVerifiedReceipt(material);
        var returned = UUID.randomUUID();
        ledger.recordVerifiedReturn(returned, material.id(), 10); ledger.recordVerifiedReturn(returned, material.id(), 10);
        assertEquals(22, ledger.remaining(material.id()));
        assertThrows(IllegalArgumentException.class, () -> ledger.recordVerifiedReturn(returned, material.id(), 11));
        assertThrows(IllegalArgumentException.class, () -> ledger.recordVerifiedReceipt(new Receipt(material.id(), Kind.LOOSE_MATERIAL, b, material.item(), 32)));
    }

    @Test void completionRequiresEveryTaskItemButLeavesPersonalInventoryAlone() {
        var ledger = new CargoLedger(); var task = receipt(Kind.LOOSE_MATERIAL, a, 2);
        var personal = receipt(Kind.PERSONAL, null, 1);
        ledger.recordVerifiedReceipt(task); ledger.recordVerifiedReceipt(personal); ledger.beginCancellation();
        assertThrows(IllegalArgumentException.class, () -> ledger.recordVerifiedReturn(UUID.randomUUID(), task.id(), 3));
        ledger.recordVerifiedReturn(UUID.randomUUID(), task.id(), 2);
        assertEquals(State.CANCELLED, ledger.state()); assertEquals(1, ledger.remaining(personal.id()));
    }
}


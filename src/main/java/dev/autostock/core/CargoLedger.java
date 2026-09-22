package dev.autostock.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Provenance and cancellation accounting only. Never creates or mutates physical items.
 * Call receipt methods only after a real transfer has been verified by the executor.
 * This in-memory ledger is not a restart recovery mechanism.
 */
public final class CargoLedger {
    public enum Kind { FINISHED_BOX, SOURCE_BOX, LOOSE_MATERIAL, EMPTY_BOX, PERSONAL }
    public enum Target { OUTPUT, SOURCE, EMPTY_AREA, HOTBAR, PRESERVE }
    public enum State { ACTIVE, PAUSED, CANCELLED, WAITING_FOR_PLAYER, EMERGENCY }
    public record Origin(String dimension, String containerKey, int preferredSlot) {
        public Origin {
            if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")
                    || containerKey == null || containerKey.isBlank() || containerKey.length() > 256
                    || preferredSlot < 0 || preferredSlot >= 54) throw new IllegalArgumentException("无效物品来源");
        }
    }
    public record Receipt(UUID id, Kind kind, Origin origin, String item, long count) {
        public Receipt {
            Objects.requireNonNull(id); Objects.requireNonNull(kind);
            if (item == null || !item.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || count <= 0 || count > 1_000_000_000L
                    || ((kind == Kind.SOURCE_BOX || kind == Kind.LOOSE_MATERIAL) && origin == null)) {
                throw new IllegalArgumentException("任务物品必须包含有效数量及必要来源");
            }
        }
    }
    public record Order(UUID receipt, Target target, Origin origin, String item, long count, String reason) { }
    private record Returned(UUID receipt, long count) { }
    private static final class Entry {
        final Receipt receipt;
        long remaining;
        Kind currentKind;
        String blocked;
        Entry(Receipt receipt) { this.receipt = receipt; remaining = receipt.count(); currentKind = receipt.kind(); }
    }
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, Returned> returns = new LinkedHashMap<>();
    private boolean cancelling;
    private boolean emergency;
    private boolean paused;

    public void recordVerifiedReceipt(Receipt receipt) {
        var existing = entries.get(receipt.id());
        if (existing != null) {
            if (!existing.receipt.equals(receipt)) throw new IllegalArgumentException("同一收货凭证内容发生冲突");
            return;
        }
        if (entries.size() >= 4096) throw new IllegalArgumentException("来源批次数量超过上限，请保留现场");
        entries.put(receipt.id(), new Entry(receipt));
    }

    public void beginCancellation() { cancelling = true; }
    public void emergencyStop() { emergency = true; }
    public void pause() { paused = true; }
    public void resumeAfterWorldRecheck() {
        if (cancelling || emergency) throw new IllegalArgumentException("已取消或紧急停止的任务不能直接恢复动作");
        paused = false;
    }
    public void sourceBoxVerifiedEmpty(UUID receipt) {
        var entry = entry(receipt);
        if (entry.currentKind != Kind.SOURCE_BOX || entry.remaining != 1) throw new IllegalArgumentException("不是单个在途源盒");
        entry.currentKind = Kind.EMPTY_BOX;
    }
    public Target sourceDisposition(UUID receipt) {
        var entry = entry(receipt);
        if (state() != State.ACTIVE) return Target.PRESERVE;
        return entry.currentKind == Kind.SOURCE_BOX ? Target.SOURCE : entry.currentKind == Kind.EMPTY_BOX ? Target.HOTBAR : Target.PRESERVE;
    }

    public void reportBlocked(UUID receipt, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 256) throw new IllegalArgumentException("需记录简短失败原因");
        entry(receipt).blocked = reason;
    }

    public void clearBlockedAfterUserAction(UUID receipt) { entry(receipt).blocked = null; }

    public void recordVerifiedReturn(UUID confirmation, UUID receipt, long count) {
        Objects.requireNonNull(confirmation);
        var value = new Returned(receipt, count);
        var previous = returns.get(confirmation);
        if (previous != null) {
            if (!previous.equals(value)) throw new IllegalArgumentException("同一归还凭证内容发生冲突");
            return;
        }
        var entry = entry(receipt);
        if (entry.receipt.kind() == Kind.PERSONAL || count <= 0 || count > entry.remaining) {
            throw new IllegalArgumentException("归还数量或物品分类无效");
        }
        if (returns.size() >= 8192) throw new IllegalArgumentException("归还凭证数量超过上限，请保留现场");
        returns.put(confirmation, value);
        entry.remaining -= count;
        if (entry.remaining == 0) entry.blocked = null;
        // Even during an emergency, record already verified world changes truthfully.
    }

    public long remaining(UUID receipt) { return entry(receipt).remaining; }

    public State state() {
        if (emergency) return State.EMERGENCY;
        if (cancelling) return State.CANCELLED;
        if (paused) return State.PAUSED;
        return entries.values().stream().anyMatch(e -> e.remaining > 0 && e.blocked != null) ? State.WAITING_FOR_PLAYER : State.ACTIVE;
    }

    public List<Order> cancellationOrders() {
        if (!cancelling && !emergency && !paused) return List.of();
        return entries.values().stream().filter(e -> e.remaining > 0).map(e -> {
            return new Order(e.receipt.id(), Target.PRESERVE, e.receipt.origin(), e.receipt.item(), e.remaining,
                    emergency ? "紧急停止，保留全部物品等待玩家处理" : cancelling ? "取消后保留物品，不自动归还" : "暂停，原地保留物品");
        }).toList();
    }

    private Entry entry(UUID id) {
        var entry = entries.get(id);
        if (entry == null) throw new IllegalArgumentException("未知来源批次");
        return entry;
    }
}

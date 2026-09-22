package dev.autostock.server;

import dev.autostock.core.DraftCodec;
import dev.autostock.core.PlanDraft;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Immutable demand only. A saved demand is not evidence of completed world actions. */
public final class FrozenTasks {
    private final Path root;
    public FrozenTasks(Path worldRoot) { root = worldRoot.resolve("autostock/frozen"); }
    public void save(UUID owner, UUID task, PlanDraft demand) throws IOException {
        Path target = path(owner, task);
        if (Files.exists(target)) {
            if (!load(owner, task).equals(demand)) throw new IOException("任务需求已冻结，禁止覆盖");
            return;
        }
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "freeze-", ".tmp");
        try {
            Files.writeString(temp, DraftCodec.encode(demand));
            // Unique task IDs; no replacement of any existing frozen demand.
            Files.move(temp, target);
        } finally { Files.deleteIfExists(temp); }
    }
    public PlanDraft load(UUID owner, UUID task) throws IOException {
        Path file = path(owner, task);
        if (Files.size(file) > DraftCodec.MAX_JSON_BYTES) throw new IOException("冻结需求文件过大");
        try { return DraftCodec.decode(Files.readString(file)); }
        catch (RuntimeException error) { throw new IOException("冻结需求损坏，保留文件并停止恢复", error); }
    }
    private Path path(UUID owner, UUID task) { return root.resolve(owner.toString()).resolve(task + ".json"); }
}

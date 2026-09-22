package dev.autostock.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/** Recovery hints only. Never stores item stacks or authoritative material quantities. */
final class TaskJournal {
    enum State { RUNNING, PAUSED, CANCELLED, EMERGENCY, ERROR, COMPLETED }
    record Entry(int version, UUID task, State state, String dimension, SourceBoxFlow.Origin source,
                 String detail) {
        Entry {
            if (version != 1 || task == null || state == null || dimension == null || dimension.length() > 128
                    || detail == null || detail.length() > 512) throw new IllegalArgumentException("任务恢复记录无效");
        }
    }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    final UUID owner;
    TaskJournal(Path world, UUID owner) { this.owner=owner;file = world.resolve("autostock/tasks").resolve(owner + ".json"); }
    Entry load() throws IOException {
        if (!Files.exists(file)) return null;
        if (Files.size(file) > 16384) throw new IOException("任务恢复文件过大");
        try {
            var entry = GSON.fromJson(Files.readString(file),Entry.class);
            if (entry == null) throw new IllegalArgumentException();
            return entry;
        } catch (RuntimeException error) { throw new IOException("任务恢复记录损坏，停止并保留现场",error); }
    }
    void save(Entry entry) throws IOException {
        Files.createDirectories(file.getParent());
        var temp = Files.createTempFile(file.getParent(),"task-",".tmp");
        try {
            Files.writeString(temp,GSON.toJson(entry));
            try { Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}

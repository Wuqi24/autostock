package dev.autostock.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Coordinate bindings only: never serializes or writes actual inventory contents. */
final class OutputBindings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private record Data(int version, Map<String, Set<String>> dimensions) { }
    private final Path path;
    private final Map<String, Set<String>> dimensions;

    OutputBindings(MinecraftServer server, UUID owner) throws IOException {
        this(server.getSavePath(WorldSavePath.ROOT), owner);
    }

    OutputBindings(Path worldRoot, UUID owner) throws IOException {
        path = worldRoot.resolve("autostock").resolve("bindings").resolve(owner + ".json");
        dimensions = new HashMap<>();
        if (Files.exists(path)) {
            if (Files.size(path) > 524_288) throw new IOException("备货绑定文件过大");
            try {
                var data = GSON.fromJson(Files.readString(path), Data.class);
                if (data == null || data.version() != 1 || data.dimensions() == null) throw new IllegalArgumentException();
                for (var entry : data.dimensions().entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null || entry.getValue().size() > 1024
                            || entry.getValue().stream().anyMatch(k -> k == null || k.length() > 256)) throw new IllegalArgumentException();
                    dimensions.put(entry.getKey(), Set.copyOf(entry.getValue()));
                }
            } catch (RuntimeException error) { throw new IOException("备货绑定文件损坏，已停止扫描", error); }
        }
    }

    boolean contains(String dimension, String key) { return dimensions.getOrDefault(dimension, Set.of()).contains(key); }
    Set<String> keys(String dimension) { return dimensions.getOrDefault(dimension, Set.of()); }

    void commit(String dimension, Set<String> keys) throws IOException {
        var updated = new HashMap<>(dimensions);
        updated.put(dimension, Set.copyOf(keys));
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), "bindings-", ".tmp");
        try {
            Files.writeString(temp, GSON.toJson(new Data(1, updated)));
            try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            dimensions.clear(); dimensions.putAll(updated);
        } finally { Files.deleteIfExists(temp); }
    }
}

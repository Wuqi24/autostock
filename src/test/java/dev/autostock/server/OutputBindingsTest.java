package dev.autostock.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OutputBindingsTest {
    @TempDir Path world;
    @Test void persistsAndRemovesUnselectedBindings() throws Exception {
        var owner = UUID.randomUUID();
        var bindings = new OutputBindings(world, owner);
        assertFalse(bindings.contains("minecraft:overworld", "a"));
        bindings.commit("minecraft:overworld", Set.of("a", "b"));
        var reloaded = new OutputBindings(world, owner);
        assertTrue(reloaded.contains("minecraft:overworld", "a"));
        reloaded.commit("minecraft:overworld", Set.of("b"));
        assertFalse(new OutputBindings(world, owner).contains("minecraft:overworld", "a"));
    }
    @Test void isolatesPlayersAndDimensions() throws Exception {
        var owner = UUID.randomUUID();
        var bindings = new OutputBindings(world, owner);
        bindings.commit("minecraft:overworld", Set.of("a"));
        bindings.commit("minecraft:the_nether", Set.of("b"));
        var reloaded = new OutputBindings(world, owner);
        assertTrue(reloaded.contains("minecraft:overworld", "a"));
        assertFalse(reloaded.contains("minecraft:the_nether", "a"));
        assertFalse(new OutputBindings(world, UUID.randomUUID()).contains("minecraft:overworld", "a"));
    }
    @Test void corruptFileFailsWithoutOverwriting() throws Exception {
        var owner = UUID.randomUUID();
        var file = world.resolve("autostock/bindings/" + owner + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "bad data");
        assertThrows(IOException.class, () -> new OutputBindings(world, owner));
        assertEquals("bad data", Files.readString(file));
    }
}

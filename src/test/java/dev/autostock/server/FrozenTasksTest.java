package dev.autostock.server;

import dev.autostock.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrozenTasksTest {
    @TempDir Path world;
    private PlanDraft draft(long count, int multiplier) {
        return new PlanDraft(PlanDraft.PROTOCOL, "minecraft:overworld", "筛选结果", Map.of(
                RegionRole.MATERIAL, List.of(new RegionBox(0,64,0,1,65,1)),
                RegionRole.EMPTY_BOX, List.of(new RegionBox(2,64,0,3,65,1)),
                RegionRole.OUTPUT, List.of(new RegionBox(4,64,0,5,65,1))), Map.of("minecraft:stone", count), multiplier, false);
    }
    @Test void survivesReloadAndRejectsEditingSameTask() throws Exception {
        var owner = UUID.randomUUID(); var task = UUID.randomUUID(); var store = new FrozenTasks(world);
        store.save(owner, task, draft(192, 3)); store.save(owner, task, draft(192, 3));
        assertEquals(draft(192, 3), new FrozenTasks(world).load(owner, task));
        assertThrows(java.io.IOException.class, () -> store.save(owner, task, draft(64, 1)));
        assertEquals(192, store.load(owner, task).totalItems());
        assertThrows(java.io.IOException.class, () -> store.load(UUID.randomUUID(), task));
    }
    @Test void corruptDemandNeverBecomesAnEmptySuccessfulTask() throws Exception {
        var owner = UUID.randomUUID(); var task = UUID.randomUUID();
        var path = world.resolve("autostock/frozen/" + owner + "/" + task + ".json");
        Files.createDirectories(path.getParent()); Files.writeString(path, "{}");
        assertThrows(java.io.IOException.class, () -> new FrozenTasks(world).load(owner, task));
        assertEquals("{}", Files.readString(path));
    }
}

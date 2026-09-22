package dev.autostock.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TaskJournalTest {
    @TempDir Path world;
    @Test void sourceIdentityAndCancelledStateSurviveReload() throws Exception {
        var owner=UUID.randomUUID();var task=UUID.randomUUID();
        var origin=new SourceBoxFlow.Origin(1,64,3,5,Map.of("1, 64, 3",UUID.randomUUID().toString()));
        var entry=new TaskJournal.Entry(1,task,TaskJournal.State.CANCELLED,"minecraft:overworld",origin,"保留物品");
        new TaskJournal(world,owner).save(entry);
        assertEquals(entry,new TaskJournal(world,owner).load());
        assertNull(new TaskJournal(world,UUID.randomUUID()).load());
        var json=Files.readString(world.resolve("autostock/tasks/"+owner+".json"));
        assertFalse(json.contains("minecraft:stone"));assertFalse(json.contains("inventory"));
    }
    @Test void corruptMetadataIsRetainedAndNeverStartsNewEmptyTask() throws Exception {
        var owner=UUID.randomUUID();var path=world.resolve("autostock/tasks/"+owner+".json");
        Files.createDirectories(path.getParent());Files.writeString(path,"{}");
        assertThrows(java.io.IOException.class,()->new TaskJournal(world,owner).load());
        assertEquals("{}",Files.readString(path));
        assertThrows(IllegalArgumentException.class,()->new SourceBoxFlow.Origin(1,64,3,-1,Map.of("p","id")));
    }
}

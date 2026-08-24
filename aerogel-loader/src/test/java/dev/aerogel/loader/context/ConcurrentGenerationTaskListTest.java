package dev.aerogel.loader.context;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConcurrentGenerationTaskListTest {
    @Test
    void additionsDuringDrainBelongToTheNextExactGeneration() {
        ConcurrentGenerationTaskList<Integer> tasks =
            new ConcurrentGenerationTaskList<>();
        tasks.add(1);
        tasks.add(2);
        List<Integer> first = new ArrayList<>();

        tasks.forEach(value -> {
            first.add(value);
            if (value == 1) tasks.add(3);
        });
        tasks.clear();

        assertEquals(List.of(1, 2), first);
        List<Integer> second = new ArrayList<>();
        tasks.forEach(second::add);
        tasks.clear();
        assertEquals(List.of(3), second);
        assertTrue(tasks.isEmpty());
    }
}

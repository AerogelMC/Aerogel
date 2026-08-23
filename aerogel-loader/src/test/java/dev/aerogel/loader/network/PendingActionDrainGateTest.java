package dev.aerogel.loader.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PendingActionDrainGateTest {
    @Test
    void unchangedQueueNeedsNoRepeatedDrain() {
        PendingActionDrainGate gate = new PendingActionDrainGate();

        long initial = gate.requiredGeneration();
        assertNotEquals(PendingActionDrainGate.NONE, initial);
        gate.drained(initial);

        assertEquals(PendingActionDrainGate.NONE, gate.requiredGeneration());
        gate.published();
        assertNotEquals(PendingActionDrainGate.NONE, gate.requiredGeneration());
    }

    @Test
    void publicationRacingWithDrainCannotBeLost() {
        PendingActionDrainGate gate = new PendingActionDrainGate();
        long observed = gate.requiredGeneration();

        gate.published();
        gate.drained(observed);

        long next = gate.requiredGeneration();
        assertNotEquals(PendingActionDrainGate.NONE, next);
        gate.drained(next);
        assertEquals(PendingActionDrainGate.NONE, gate.requiredGeneration());
    }
}

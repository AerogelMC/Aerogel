package dev.aerogel.loader.event;

import dev.aerogel.api.event.block.BlockStateChangeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStateChangeEventTest {
    private static final Level LEVEL = new Level() { };
    private static final BlockState AIR = new BlockState() {
        @Override public boolean isAir() { return true; }
    };
    private static final BlockState SOLID = new BlockState();

    @Test
    void classifiesTheEffectiveReplacementAndCarriesOriginData() {
        BlockPos changed = new BlockPos(1, 2, 3);
        BlockPos origin = new BlockPos(4, 5, 6);
        BlockStateChangeEvent event = new BlockStateChangeEvent(
            LEVEL, changed, AIR, SOLID, 3, 512,
            BlockStateChangeEvent.Reason.PISTON, null, origin, null);

        assertEquals(BlockStateChangeEvent.ChangeType.PLACE, event.changeType());
        assertEquals(BlockStateChangeEvent.Reason.PISTON, event.reason());
        assertSame(origin, event.sourcePosition().orElseThrow());
        assertTrue(event.sourceEntity().isEmpty());

        event.setState(AIR);
        assertEquals(BlockStateChangeEvent.ChangeType.REPLACE, event.changeType());
    }
}

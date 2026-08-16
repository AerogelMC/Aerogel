package dev.aerogel.loader.event;

import dev.aerogel.api.event.block.BlockStateChangeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlockChangeContextTest {
    @Test
    void nestedOperationsRestoreTheirParentContext() {
        Entity player = new Entity() { };
        BlockPos position = new BlockPos(1, 2, 3);

        BlockChangeContext.run(
            BlockStateChangeEvent.Reason.PLAYER_PLACE, player, position, null, () -> {
                assertEquals(BlockStateChangeEvent.Reason.PLAYER_PLACE,
                    BlockChangeContext.current().reason());
                assertSame(player, BlockChangeContext.current().sourceEntity());

                BlockChangeContext.run(
                    BlockStateChangeEvent.Reason.PISTON, null, position, null, () ->
                        assertEquals(BlockStateChangeEvent.Reason.PISTON,
                            BlockChangeContext.current().reason()));

                assertEquals(BlockStateChangeEvent.Reason.PLAYER_PLACE,
                    BlockChangeContext.current().reason());
            });

        assertEquals(BlockStateChangeEvent.Reason.DIRECT,
            BlockChangeContext.current().reason());
        assertNull(BlockChangeContext.current().sourceEntity());
    }

    @Test
    void thrownActionsCannotLeakContext() {
        assertThrows(IllegalStateException.class, () -> BlockChangeContext.run(
            BlockStateChangeEvent.Reason.EXPLOSION, new Entity() { }, null, new Vec3(),
            () -> { throw new IllegalStateException("boom"); }));

        assertEquals(BlockStateChangeEvent.Reason.DIRECT,
            BlockChangeContext.current().reason());
    }
}

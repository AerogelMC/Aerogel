package dev.aerogel.loader.command;

import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginsCommandTest {
    @Test
    void colorsEachTpsValueByItsHealthRange() {
        assertEquals(ChatFormatting.GREEN, PluginsCommand.tpsColor(20.0D, 20.0D));
        assertEquals(ChatFormatting.GREEN, PluginsCommand.tpsColor(19.0D, 20.0D));
        assertEquals(ChatFormatting.YELLOW, PluginsCommand.tpsColor(18.999D, 20.0D));
        assertEquals(ChatFormatting.YELLOW, PluginsCommand.tpsColor(18.0D, 20.0D));
        assertEquals(ChatFormatting.RED, PluginsCommand.tpsColor(17.999D, 20.0D));
        assertEquals(ChatFormatting.GREEN, PluginsCommand.tpsColor(57.0D, 60.0D));
        assertEquals(ChatFormatting.YELLOW, PluginsCommand.tpsColor(54.0D, 60.0D));
        assertEquals(ChatFormatting.RED, PluginsCommand.tpsColor(53.999D, 60.0D));
    }

    @Test
    void marksConfiguredTickRateOrHigherAsPerfect() {
        assertEquals("20.0*", PluginsCommand.formatTps(20.0D, 20.0D));
        assertEquals("20.0*", PluginsCommand.formatTps(21.5D, 20.0D));
        assertEquals("60.0*", PluginsCommand.formatTps(60.1D, 60.0D));
        assertEquals("59.99", PluginsCommand.formatTps(59.99D, 60.0D));
    }
}

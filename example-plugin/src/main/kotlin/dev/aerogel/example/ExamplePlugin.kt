package dev.aerogel.example

import dev.aerogel.api.AerogelPlugin
import dev.aerogel.api.PluginContext
import dev.aerogel.api.event.EventHandler
import dev.aerogel.api.event.player.PlayerJoinEvent
import net.minecraft.network.chat.Component

class ExamplePlugin : AerogelPlugin {
    override fun onLoad(context: PluginContext) {
        // Log a message when the plugin is loaded
        context.logger().info("ExamplePlugin loaded!")

        // Event listener approach 1:
        // Register a PlayerJoinEvent listener through the plugin context
        context.events().listen(PlayerJoinEvent::class.java) { event ->
            event.player().sendSystemMessage(
                Component.literal("Hello! 1")
            )
        }
    }

    // Event listener approach 2:
    // Handle PlayerJoinEvent using the @EventHandler annotation
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        event.player().sendSystemMessage(
            Component.literal("Hello! 2")
        )
    }
}
import dev.aerogel.api.mixin.InjectionPoint
import dev.aerogel.api.mixin.mixin
import net.minecraft.server.MinecraftServer

mixin<MinecraftServer> {
    inject(
        method = MinecraftServer::getServerModName,
        at = InjectionPoint.HEAD,
        cancellable = true
    ) { callback ->
        callback.returnValue = "test"
    }
}

package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.runtime.AerogelRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEventListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(targets = "net.minecraft.world.level.gameevent.DynamicGameEventListener")
abstract class DynamicGameEventListenerMixin {
    @Shadow @Final private GameEventListener listener;
    @Shadow private SectionPos lastSection;

    @Unique private static final ThreadLocal<Boolean> AEROGEL_REPLAYING =
        ThreadLocal.withInitial(() -> false);

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void aerogel$ownListenerMove(ServerLevel level, CallbackInfo callback) {
        if (AEROGEL_REPLAYING.get()) return;
        Optional<SectionPos> nextSection = listener.getListenerSource()
            .getPosition(level)
            .map(position -> SectionPos.of(BlockPos.containing(
                position.x, position.y, position.z)));
        if (nextSection.isEmpty() || nextSection.get().equals(lastSection)) return;

        long[] owners = aerogel$owners(lastSection, nextSection.get());
        Runnable replay = () -> aerogel$replay(() ->
            ((DynamicGameEventListener<?>) (Object) this).move(level));
        if (AerogelRuntime.routeGameEventListenerMutation(level, owners, replay)) {
            callback.cancel();
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void aerogel$ownListenerRemoval(ServerLevel level, CallbackInfo callback) {
        if (AEROGEL_REPLAYING.get() || lastSection == null) return;
        long[] owners = { ChunkPos.pack(lastSection.x(), lastSection.z()) };
        Runnable replay = () -> aerogel$replay(() ->
            ((DynamicGameEventListener<?>) (Object) this).remove(level));
        if (AerogelRuntime.routeGameEventListenerMutation(level, owners, replay)) {
            callback.cancel();
        }
    }

    @Unique
    private static long[] aerogel$owners(SectionPos previous, SectionPos next) {
        long nextKey = ChunkPos.pack(next.x(), next.z());
        if (previous == null) return new long[] { nextKey };
        long previousKey = ChunkPos.pack(previous.x(), previous.z());
        return previousKey == nextKey
            ? new long[] { nextKey }
            : new long[] { previousKey, nextKey };
    }

    @Unique
    private static void aerogel$replay(Runnable action) {
        AEROGEL_REPLAYING.set(true);
        try {
            action.run();
        } finally {
            AEROGEL_REPLAYING.remove();
        }
    }
}

package dev.aerogel.loader.mixin.core;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Maintains the exact dimension-activity result at ticket mutation points. */
@Mixin(targets = "net.minecraft.world.level.TicketStorage")
abstract class TicketStorageMixin {
    @Shadow @Final
    private Long2ObjectOpenHashMap<List<Ticket>> tickets;

    @Unique
    private int aerogel$dimensionActiveTicketCount;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$initializeDimensionActiveTicketCount(CallbackInfo callback) {
        int count = 0;
        for (List<Ticket> chunkTickets : tickets.values()) {
            for (Ticket ticket : chunkTickets) {
                if (ticket.getType().shouldKeepDimensionActive()) count++;
            }
        }
        aerogel$dimensionActiveTicketCount = count;
    }

    @Inject(
        method = "addTicket(JLnet/minecraft/server/level/Ticket;)Z",
        at = @At("RETURN")
    )
    private void aerogel$recordDimensionActiveTicketAdded(
        long chunkKey, Ticket ticket, CallbackInfoReturnable<Boolean> callback
    ) {
        if (callback.getReturnValueZ()
            && ticket.getType().shouldKeepDimensionActive()) {
            aerogel$dimensionActiveTicketCount++;
        }
    }

    @Inject(
        method = "removeTicket(JLnet/minecraft/server/level/Ticket;)Z",
        at = @At("RETURN")
    )
    private void aerogel$recordDimensionActiveTicketRemoved(
        long chunkKey, Ticket ticket, CallbackInfoReturnable<Boolean> callback
    ) {
        if (callback.getReturnValueZ()
            && ticket.getType().shouldKeepDimensionActive()) {
            aerogel$dimensionActiveTicketCount--;
        }
    }

    @Redirect(
        method = "removeTicketIf",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/TicketStorage$TicketPredicate;"
                + "test(Lnet/minecraft/server/level/Ticket;J)Z"
        )
    )
    private boolean aerogel$recordPredicateTicketRemoval(
        TicketStorage.TicketPredicate predicate, Ticket ticket, long chunkKey
    ) {
        boolean remove = predicate.test(ticket, chunkKey);
        if (remove && ticket.getType().shouldKeepDimensionActive()) {
            aerogel$dimensionActiveTicketCount--;
        }
        return remove;
    }

    @Inject(method = "shouldKeepDimensionActive", at = @At("HEAD"), cancellable = true)
    private void aerogel$readDimensionActiveTicketCount(
        CallbackInfoReturnable<Boolean> callback
    ) {
        callback.setReturnValue(aerogel$dimensionActiveTicketCount != 0);
    }
}

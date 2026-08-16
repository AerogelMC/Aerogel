package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.item.ItemBuilder;
import dev.aerogel.api.persistence.PersistentDataView;
import dev.aerogel.loader.internal.ItemBuilders;
import dev.aerogel.loader.internal.PersistentDataViews;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "net.minecraft.world.item.ItemStack")
abstract class ItemStackMixin {
    @Unique
    public ItemBuilder edit() {
        return ItemBuilders.edit((ItemStack) (Object) this);
    }

    @Unique
    public PersistentDataView data() {
        return PersistentDataViews.item((ItemStack) (Object) this);
    }
}

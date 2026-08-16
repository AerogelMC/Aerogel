package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import java.util.Map;

public record ClientboundUpdateRecipesPacket(
    Map<ResourceKey<RecipePropertySet>, RecipePropertySet> itemSets,
    SelectableRecipe.SingleInputSet<StonecutterRecipe> stonecutterRecipes
) implements Packet<ClientGamePacketListener> { }

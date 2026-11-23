package com.simplefinances.registry;

import com.simplefinances.SimpleFinances;
import com.simplefinances.recipe.CoinMergeRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

public final class ModRecipes {
    public static final RecipeSerializer<CoinMergeRecipe> COIN_MERGE = Registry.register(
            BuiltInRegistries.RECIPE_SERIALIZER,
            SimpleFinances.id("coin_merge"),
            new SimpleCraftingRecipeSerializer<>(CoinMergeRecipe::new)
    );

    private ModRecipes() {
    }

    public static void init() {
        // Forces class initialization.
    }
}

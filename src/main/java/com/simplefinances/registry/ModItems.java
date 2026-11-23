package com.simplefinances.registry;

import com.simplefinances.SimpleFinances;
import com.simplefinances.item.CoinItem;
import com.simplefinances.item.CollectibleCoinItem;
import com.simplefinances.item.CollectibleTier;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

public final class ModItems {
    public static final Item COIN_1 = register("coin_1",
            new CoinItem("tooltip.simplefinances.coin_1", new Item.Properties().stacksTo(64)));

    public static final Item COIN_50 = register("coin_50",
            new CoinItem("tooltip.simplefinances.coin_50", new Item.Properties().stacksTo(64)));

    public static final Item COLLECTIBLE_RUBY = register("collectible_ruby",
            new CollectibleCoinItem(CollectibleTier.RUBY,
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final Item COLLECTIBLE_SAPPHIRE = register("collectible_sapphire",
            new CollectibleCoinItem(CollectibleTier.SAPPHIRE,
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final Item COLLECTIBLE_PEARL = register("collectible_pearl",
            new CollectibleCoinItem(CollectibleTier.PEARL,
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final CreativeModeTab GROUP = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            SimpleFinances.id("main"),
            FabricItemGroup.builder()
                    .title(Component.translatable("itemGroup.simplefinances.main"))
                    .icon(() -> new ItemStack(COIN_1))
                    .displayItems((parameters, output) -> {
                        output.accept(COIN_1);
                        output.accept(COIN_50);
                        output.accept(COLLECTIBLE_RUBY);
                        output.accept(COLLECTIBLE_SAPPHIRE);
                        output.accept(COLLECTIBLE_PEARL);
                    })
                    .build()
    );

    private ModItems() {
    }

    private static Item register(String name, Item item) {
        ResourceLocation id = SimpleFinances.id(name);
        return Registry.register(BuiltInRegistries.ITEM, id, item);
    }

    public static void init() {
        // Forces class initialization.
    }
}

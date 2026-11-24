package com.simplefinances.registry;

import com.simplefinances.SimpleFinances;
import com.simplefinances.item.BiomeCoinItem;
import com.simplefinances.item.ClumpItem;
import com.simplefinances.item.CoinBiome;
import com.simplefinances.item.CoinCondition;
import com.simplefinances.item.CoinItem;
import com.simplefinances.item.CollectibleCoinItem;
import com.simplefinances.item.CollectibleTier;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    private static final Map<CoinBiome, Map<CoinCondition, Item>> COINS = new EnumMap<>(CoinBiome.class);
    private static final Map<CoinBiome, Item> CLUMPS = new EnumMap<>(CoinBiome.class);

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

    // ---------------------------------------------------------------- biome coins

    static {
        for (CoinBiome biome : CoinBiome.values()) {
            if (biome.nether()) {
                registerCoin(biome, CoinCondition.PERFECT);
            } else {
                for (CoinCondition condition : CoinCondition.values()) {
                    registerCoin(biome, condition);
                }
            }
            if (!biome.nether()) {
                registerClump(biome);
            }
        }
    }

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
                        for (CoinBiome biome : CoinBiome.values()) {
                            for (Item item : biomeCoins(biome)) {
                                output.accept(item);
                            }
                        }
                        for (CoinBiome biome : CoinBiome.values()) {
                            Item clump = clump(biome);
                            if (clump != null) {
                                output.accept(clump);
                            }
                        }
                    })
                    .build()
    );

    private ModItems() {
    }

    private static Item register(String name, Item item) {
        ResourceLocation id = SimpleFinances.id(name);
        return Registry.register(BuiltInRegistries.ITEM, id, item);
    }

    private static void registerCoin(CoinBiome biome, CoinCondition condition) {
        String name = "coin_" + biome.id() + (biome.nether() ? "" : "_" + condition.id());
        Rarity rarity = switch (condition) {
            case DIRTY -> Rarity.COMMON;
            case SLIGHTLY_DIRTY -> Rarity.UNCOMMON;
            case PERFECT -> Rarity.EPIC;
        };
        Item item = register(name, new BiomeCoinItem(biome, condition,
                new Item.Properties().stacksTo(64).rarity(rarity)));
        COINS.computeIfAbsent(biome, key -> new EnumMap<>(CoinCondition.class)).put(condition, item);
    }

    private static void registerClump(CoinBiome biome) {
        Item item = register("clump_" + biome.id(), new ClumpItem(biome,
                new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
        CLUMPS.put(biome, item);
    }

    /**
     * Returns the coin item for the given biome and condition, or null if the
     * combination does not exist (Nether coins are always perfect).
     */
    public static Item coin(CoinBiome biome, CoinCondition condition) {
        if (biome == null) {
            return null;
        }
        Map<CoinCondition, Item> map = COINS.get(biome);
        if (map == null) {
            return null;
        }
        return map.get(biome.nether() ? CoinCondition.PERFECT : condition);
    }

    public static Item clump(CoinBiome biome) {
        return biome == null ? null : CLUMPS.get(biome);
    }

    private static Iterable<Item> biomeCoins(CoinBiome biome) {
        Map<CoinCondition, Item> map = COINS.get(biome);
        return map == null ? java.util.List.of() : map.values();
    }

    public static void init() {
        // Forces class initialization.
    }
}

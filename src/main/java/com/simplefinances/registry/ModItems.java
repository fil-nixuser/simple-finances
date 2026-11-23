package com.simplefinances.registry;

import com.simplefinances.SimpleFinances;
import com.simplefinances.item.ClumpItem;
import com.simplefinances.item.CoinCondition;
import com.simplefinances.item.CoinSeries;
import com.simplefinances.item.CoinItem;
import com.simplefinances.item.CollectibleCoinItem;
import com.simplefinances.item.CollectibleTier;
import com.simplefinances.item.SeriesCoinItem;
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
    private static final Map<CoinSeries, Map<CoinCondition, Item>> COINS = new EnumMap<>(CoinSeries.class);
    private static final Map<CoinSeries, Item> CLUMPS = new EnumMap<>(CoinSeries.class);

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

    // ---------------------------------------------------------------- series coins

    static {
        for (CoinSeries series : CoinSeries.values()) {
            if (series.hellish()) {
                registerCoin(series, CoinCondition.PERFECT);
            } else {
                for (CoinCondition condition : CoinCondition.values()) {
                    registerCoin(series, condition);
                }
            }
            if (!series.hellish()) {
                registerClump(series);
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
                        for (CoinSeries series : CoinSeries.values()) {
                            for (Item item : seriesCoins(series)) {
                                output.accept(item);
                            }
                        }
                        for (CoinSeries series : CoinSeries.values()) {
                            Item clump = clump(series);
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

    private static void registerCoin(CoinSeries series, CoinCondition condition) {
        String name = "coin_" + series.id() + (series.hellish() ? "" : "_" + condition.id());
        Rarity rarity = switch (condition) {
            case DIRTY -> Rarity.COMMON;
            case SLIGHTLY_DIRTY -> Rarity.UNCOMMON;
            case PERFECT -> Rarity.EPIC;
        };
        Item item = register(name, new SeriesCoinItem(series, condition,
                new Item.Properties().stacksTo(1).rarity(rarity)));
        COINS.computeIfAbsent(series, key -> new EnumMap<>(CoinCondition.class)).put(condition, item);
    }

    private static void registerClump(CoinSeries series) {
        Item item = register("clump_" + series.id(), new ClumpItem(series,
                new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
        CLUMPS.put(series, item);
    }

    /**
     * Returns the coin item for the given series and condition, or null if the
     * combination does not exist (hellish coins are always perfect).
     */
    public static Item coin(CoinSeries series, CoinCondition condition) {
        if (series == null) {
            return null;
        }
        Map<CoinCondition, Item> map = COINS.get(series);
        if (map == null) {
            return null;
        }
        return map.get(series.hellish() ? CoinCondition.PERFECT : condition);
    }

    public static Item clump(CoinSeries series) {
        return series == null ? null : CLUMPS.get(series);
    }

    private static Iterable<Item> seriesCoins(CoinSeries series) {
        Map<CoinCondition, Item> map = COINS.get(series);
        return map == null ? java.util.List.of() : map.values();
    }

    public static void init() {
        // Forces class initialization.
    }
}

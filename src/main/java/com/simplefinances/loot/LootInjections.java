package com.simplefinances.loot;

import com.simplefinances.SimpleFinances;
import com.simplefinances.item.CoinCondition;
import com.simplefinances.item.CoinSeries;
import com.simplefinances.registry.ModItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Injects clumps and hellish coins into vanilla structure loot.
 * Every added pool rolls exactly once: a clump has the given percent chance,
 * otherwise the pool produces nothing.
 */
public final class LootInjections {
    private record Rule(Supplier<Item> item, double percent) {
    }

    private static final Map<ResourceLocation, Rule> RULES = new HashMap<>();

    private LootInjections() {
    }

    public static void init() {
        addClump(CoinSeries.OCEANIC,
                "chests/shipwreck_treasure",
                "chests/shipwreck_supply",
                "chests/underwater_ruin_big",
                "chests/underwater_ruin_small");

        addClump(CoinSeries.END,
                "chests/end_city_treasure");

        addClump(CoinSeries.TRIAL,
                "chests/trial_chambers/supply",
                "chests/trial_chambers/corridor",
                "chests/trial_chambers/entrance",
                "chests/trial_chambers/intersection",
                "chests/trial_chambers/intersection_barrel",
                "chests/trial_chambers/reward",
                "chests/trial_chambers/reward_ominous",
                "chests/trial_chambers/reward_common",
                "chests/trial_chambers/reward_rare",
                "chests/trial_chambers/reward_unique",
                "chests/trial_chambers/reward_ominous_common",
                "chests/trial_chambers/reward_ominous_rare",
                "chests/trial_chambers/reward_ominous_unique");

        addClump(CoinSeries.WARDEN,
                "chests/ancient_city",
                "chests/ancient_city_ice_box");

        addCoin(CoinSeries.PIGLIN, 1,
                "chests/bastion_treasure",
                "chests/bastion_other",
                "chests/bastion_bridge",
                "chests/bastion_hoglin_stable");

        // Every chest in a Nether Fortress uses this loot table.
        addCoin(CoinSeries.FORTRESS, 1,
                "chests/nether_bridge");

        addCoin(CoinSeries.NETHERITE, 5,
                "blocks/ancient_debris");

        // A rare oceanic clump can be pulled out while fishing (0.5% per catch).
        addClump(CoinSeries.OCEANIC, 0.5,
                "gameplay/fishing");

        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!source.isBuiltin()) {
                return;
            }
            Rule rule = RULES.get(key.location());
            if (rule == null) {
                return;
            }
            Item item = rule.item().get();
            if (item == null) {
                return;
            }
            addPool(tableBuilder, item, rule.percent());
        });

        SimpleFinances.LOGGER.info("Registered {} loot injection rules", RULES.size());
    }

    private static void addClump(CoinSeries series, String... tables) {
        addClump(series, 1, tables);
    }

    private static void addClump(CoinSeries series, double percent, String... tables) {
        for (String table : tables) {
            RULES.put(ResourceLocation.withDefaultNamespace(table), new Rule(() -> ModItems.clump(series), percent));
        }
    }

    private static void addCoin(CoinSeries series, double percent, String... tables) {
        for (String table : tables) {
            RULES.put(ResourceLocation.withDefaultNamespace(table), new Rule(
                    () -> ModItems.coin(series, CoinCondition.PERFECT), percent));
        }
    }

    private static void addPool(LootTable.Builder tableBuilder, Item item, double percent) {
        // Weights are integers in 1.21.1, so scale percent by 100 to allow
        // fractional chances such as 0.5%.
        int weight = (int) Math.round(Math.max(0.0D, Math.min(100.0D, percent)) * 100.0D);
        LootPool.Builder pool = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(item).setWeight(weight));
        if (weight < 10_000) {
            pool.add(EmptyLootItem.emptyItem().setWeight(10_000 - weight));
        }
        tableBuilder.withPool(pool);
    }
}

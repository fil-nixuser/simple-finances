package com.simplefinances.item;

import com.simplefinances.util.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A coin of one of the seven biomes. Normal biomes have three wear states,
 * Nether biomes only ever exist in perfect condition.
 */
public class BiomeCoinItem extends Item {
    private final CoinBiome biome;
    private final CoinCondition condition;

    public BiomeCoinItem(CoinBiome biome, CoinCondition condition, Properties properties) {
        super(properties);
        this.biome = biome;
        this.condition = condition;
    }

    public CoinBiome biome() {
        return biome;
    }

    public CoinCondition condition() {
        return condition;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return condition == CoinCondition.PERFECT;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (!biome.nether()) {
            tooltip.add(Component.translatable("tooltip.simplefinances.coin.condition", condition.displayName())
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.simplefinances.collectible.value",
                        Money.format(condition.conversionValue()))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.simplefinances.coin.convert_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (biome.nether()) {
            tooltip.add(Component.translatable("tooltip.simplefinances.coin.nether")
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }
}

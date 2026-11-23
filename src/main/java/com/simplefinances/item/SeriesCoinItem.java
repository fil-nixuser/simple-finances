package com.simplefinances.item;

import com.simplefinances.util.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A coin of one of the seven series. Normal series have three wear states,
 * hellish series only ever exist in perfect condition.
 */
public class SeriesCoinItem extends Item {
    private final CoinSeries series;
    private final CoinCondition condition;

    public SeriesCoinItem(CoinSeries series, CoinCondition condition, Properties properties) {
        super(properties);
        this.series = series;
        this.condition = condition;
    }

    public CoinSeries series() {
        return series;
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
        if (!series.hellish()) {
            tooltip.add(Component.translatable("tooltip.simplefinances.coin.condition", condition.displayName())
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.simplefinances.collectible.value",
                        Money.format(condition.conversionValue()))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.simplefinances.coin.convert_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (series.hellish()) {
            tooltip.add(Component.translatable("tooltip.simplefinances.coin.hellish")
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }
}

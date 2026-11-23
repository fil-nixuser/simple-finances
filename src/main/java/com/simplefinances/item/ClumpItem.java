package com.simplefinances.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A dirty clump found in structure chests. Washing it in a water cauldron
 * yields a coin of the matching series.
 */
public class ClumpItem extends Item {
    private final CoinSeries series;

    public ClumpItem(CoinSeries series, Properties properties) {
        super(properties);
        this.series = series;
    }

    public CoinSeries series() {
        return series;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.simplefinances.clump.series", series.displayName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.simplefinances.clump.origin", series.originName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.simplefinances.clump.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}

package com.simplefinances.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A dirty clump found in structure chests. Washing it in a water cauldron
 * yields a coin of the matching biome.
 */
public class ClumpItem extends Item {
    private final CoinBiome biome;

    public ClumpItem(CoinBiome biome, Properties properties) {
        super(properties);
        this.biome = biome;
    }

    public CoinBiome biome() {
        return biome;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.simplefinances.clump.biome", biome.displayName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.simplefinances.clump.origin", biome.originName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.simplefinances.clump.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}

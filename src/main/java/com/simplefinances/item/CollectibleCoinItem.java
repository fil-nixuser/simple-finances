package com.simplefinances.item;

import com.simplefinances.registry.ModComponents;
import com.simplefinances.util.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class CollectibleCoinItem extends Item {
    private final CollectibleTier tier;

    public CollectibleCoinItem(CollectibleTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public CollectibleTier tier() {
        return tier;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CollectibleInfo info = stack.get(ModComponents.COLLECTIBLE_INFO);

        tooltip.add(Component.translatable("tooltip.simplefinances.collectible.tier", tier.displayName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.simplefinances.collectible.value",
                        Money.format(tier.conversionValue()))
                .withStyle(ChatFormatting.GOLD));

        if (info == null) {
            tooltip.add(Component.translatable("tooltip.simplefinances.collectible.blank")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        if (!info.description().isBlank()) {
            tooltip.add(Component.translatable("tooltip.simplefinances.collectible.description", info.description())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!info.date().isBlank()) {
            tooltip.add(Component.translatable("tooltip.simplefinances.collectible.date", info.date())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!info.recipient().isBlank()) {
            tooltip.add(Component.translatable("tooltip.simplefinances.collectible.recipient", info.recipient())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

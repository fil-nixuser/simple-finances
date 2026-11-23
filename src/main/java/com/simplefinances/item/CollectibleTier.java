package com.simplefinances.item;

import com.simplefinances.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

public enum CollectibleTier {
    RUBY("ruby", ChatFormatting.DARK_RED, "tier.simplefinances.ruby", 25_000L),
    SAPPHIRE("sapphire", ChatFormatting.BLUE, "tier.simplefinances.sapphire", 10_000L),
    PEARL("pearl", ChatFormatting.WHITE, "tier.simplefinances.pearl", 1_000L);

    private final String id;
    private final ChatFormatting color;
    private final String translationKey;
    private final long conversionValue;

    CollectibleTier(String id, ChatFormatting color, String translationKey, long conversionValue) {
        this.id = id;
        this.color = color;
        this.translationKey = translationKey;
        this.conversionValue = conversionValue;
    }

    public String id() {
        return id;
    }

    public ChatFormatting color() {
        return color;
    }

    public long conversionValue() {
        return conversionValue;
    }

    public Component displayName() {
        return Component.translatable(translationKey).withStyle(color);
    }

    public Item item() {
        return switch (this) {
            case RUBY -> ModItems.COLLECTIBLE_RUBY;
            case SAPPHIRE -> ModItems.COLLECTIBLE_SAPPHIRE;
            case PEARL -> ModItems.COLLECTIBLE_PEARL;
        };
    }

    public static CollectibleTier byId(String id) {
        for (CollectibleTier tier : values()) {
            if (tier.id.equalsIgnoreCase(id)) {
                return tier;
            }
        }
        return PEARL;
    }

    public static CollectibleTier byIndex(int index) {
        CollectibleTier[] values = values();
        if (index < 0 || index >= values.length) {
            return PEARL;
        }
        return values[index];
    }
}

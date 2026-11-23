package com.simplefinances.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Wear state of a washed coin. Determines how much money the coin converts to.
 */
public enum CoinCondition {
    DIRTY("dirty", ChatFormatting.DARK_GRAY, "condition.simplefinances.dirty", 500L),
    SLIGHTLY_DIRTY("slightly_dirty", ChatFormatting.GRAY, "condition.simplefinances.slightly_dirty", 5_000L),
    PERFECT("perfect", ChatFormatting.GOLD, "condition.simplefinances.perfect", 12_500L);

    private final String id;
    private final ChatFormatting color;
    private final String translationKey;
    private final long conversionValue;

    CoinCondition(String id, ChatFormatting color, String translationKey, long conversionValue) {
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

    public static CoinCondition byId(String id) {
        for (CoinCondition condition : values()) {
            if (condition.id.equalsIgnoreCase(id)) {
                return condition;
            }
        }
        return null;
    }
}

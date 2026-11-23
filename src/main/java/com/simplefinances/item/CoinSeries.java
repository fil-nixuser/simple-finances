package com.simplefinances.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The seven coin series. Four overworld series are dug out as clumps and washed
 * in a cauldron; the three hellish series are always perfect and found directly.
 */
public enum CoinSeries {
    OCEANIC("oceanic", ChatFormatting.DARK_AQUA, "series.simplefinances.oceanic", "origin.simplefinances.oceanic", false),
    END("end", ChatFormatting.LIGHT_PURPLE, "series.simplefinances.end", "origin.simplefinances.end", false),
    TRIAL("trial", ChatFormatting.GOLD, "series.simplefinances.trial", "origin.simplefinances.trial", false),
    WARDEN("warden", ChatFormatting.DARK_GREEN, "series.simplefinances.warden", "origin.simplefinances.warden", false),
    NETHERITE("netherite", ChatFormatting.DARK_GRAY, "series.simplefinances.netherite", "origin.simplefinances.netherite", true),
    PIGLIN("piglin", ChatFormatting.RED, "series.simplefinances.piglin", "origin.simplefinances.piglin", true),
    FORTRESS("fortress", ChatFormatting.DARK_RED, "series.simplefinances.fortress", "origin.simplefinances.fortress", true);

    private final String id;
    private final ChatFormatting color;
    private final String translationKey;
    private final String originKey;
    private final boolean hellish;

    CoinSeries(String id, ChatFormatting color, String translationKey, String originKey, boolean hellish) {
        this.id = id;
        this.color = color;
        this.translationKey = translationKey;
        this.originKey = originKey;
        this.hellish = hellish;
    }

    public String id() {
        return id;
    }

    public ChatFormatting color() {
        return color;
    }

    public boolean hellish() {
        return hellish;
    }

    public Component displayName() {
        return Component.translatable(translationKey).withStyle(color);
    }

    public Component originName() {
        return Component.translatable(originKey);
    }

    public static CoinSeries byId(String id) {
        if (id == null) {
            return null;
        }
        for (CoinSeries series : values()) {
            if (series.id.equalsIgnoreCase(id)) {
                return series;
            }
        }
        return null;
    }
}

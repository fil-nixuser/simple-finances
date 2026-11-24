package com.simplefinances.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The seven biome coins. Four overworld coins are dug out as clumps and washed
 * in a cauldron; the three Nether coins are always perfect and found directly.
 */
public enum CoinBiome {
    OCEANIC("oceanic", ChatFormatting.DARK_AQUA, "biome.simplefinances.oceanic", "origin.simplefinances.oceanic", false),
    END("end", ChatFormatting.LIGHT_PURPLE, "biome.simplefinances.end", "origin.simplefinances.end", false),
    TRIAL("trial", ChatFormatting.GOLD, "biome.simplefinances.trial", "origin.simplefinances.trial", false),
    SCULK("sculk", ChatFormatting.DARK_GREEN, "biome.simplefinances.sculk", "origin.simplefinances.sculk", false),
    ANCIENT("ancient", ChatFormatting.DARK_GRAY, "biome.simplefinances.ancient", "origin.simplefinances.ancient", true),
    BASTION("bastion", ChatFormatting.RED, "biome.simplefinances.bastion", "origin.simplefinances.bastion", true),
    FORTRESS("fortress", ChatFormatting.DARK_RED, "biome.simplefinances.fortress", "origin.simplefinances.fortress", true);

    private final String id;
    private final ChatFormatting color;
    private final String translationKey;
    private final String originKey;
    private final boolean nether;

    CoinBiome(String id, ChatFormatting color, String translationKey, String originKey, boolean nether) {
        this.id = id;
        this.color = color;
        this.translationKey = translationKey;
        this.originKey = originKey;
        this.nether = nether;
    }

    public String id() {
        return id;
    }

    public ChatFormatting color() {
        return color;
    }

    public boolean nether() {
        return nether;
    }

    public Component displayName() {
        return Component.translatable(translationKey).withStyle(color);
    }

    public Component originName() {
        return Component.translatable(originKey);
    }

    public static CoinBiome byId(String id) {
        if (id == null) {
            return null;
        }
        for (CoinBiome biome : values()) {
            if (biome.id.equalsIgnoreCase(id)) {
                return biome;
            }
        }
        // Old collection entries used these ids.
        if ("warden".equalsIgnoreCase(id)) {
            return SCULK;
        }
        if ("netherite".equalsIgnoreCase(id)) {
            return ANCIENT;
        }
        if ("piglin".equalsIgnoreCase(id)) {
            return BASTION;
        }
        return null;
    }
}

package com.simplefinances.item;

import com.simplefinances.registry.ModComponents;
import com.simplefinances.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A uniform view over every coin that can be converted: the old admin-awarded
 * collectibles and the new series coins. Used by the conversion logic, the
 * wallet screen and the server collection.
 */
public record CoinView(ItemStack icon, Component name, long value, String albumKey, CollectibleInfo info) {
    public static CoinView of(ItemStack stack) {
        if (stack.getItem() instanceof SeriesCoinItem coin) {
            return new CoinView(
                    stack.copyWithCount(1),
                    stack.getHoverName(),
                    coin.condition().conversionValue(),
                    albumKey(coin.series(), coin.condition()),
                    null
            );
        }
        if (stack.getItem() instanceof CollectibleCoinItem coin) {
            CollectibleTier tier = coin.tier();
            return new CoinView(
                    new ItemStack(tier.item()),
                    tier.displayName(),
                    tier.conversionValue(),
                    tier.id(),
                    stack.get(ModComponents.COLLECTIBLE_INFO)
            );
        }
        return null;
    }

    public static CoinView fromAlbumKey(String key) {
        int split = key == null ? -1 : key.indexOf(':');
        if (split > 0) {
            CoinSeries series = CoinSeries.byId(key.substring(0, split));
            CoinCondition condition = CoinCondition.byId(key.substring(split + 1));
            if (series != null && condition != null) {
                Item item = ModItems.coin(series, condition);
                if (item != null) {
                    ItemStack icon = new ItemStack(item);
                    return new CoinView(icon, icon.getHoverName(), condition.conversionValue(), key, null);
                }
            }
        }
        CollectibleTier tier = CollectibleTier.byId(key);
        return new CoinView(new ItemStack(tier.item()), tier.displayName(), tier.conversionValue(), tier.id(), null);
    }

    public static String albumKey(CoinSeries series, CoinCondition condition) {
        return series.id() + ":" + condition.id();
    }
}

package com.simplefinances.mixin;

import com.simplefinances.registry.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla crafting consumes exactly one item from each occupied grid slot.
 * Our 50 -> 1 merge recipe needs to consume all 50 coins, otherwise taking
 * the result would leave 49 coins behind and duplicate value.
 *
 * <p>The injection runs right after {@code checkTakeAchievements}, so recipe
 * unlocks still see the full grid, but before vanilla removes one item per
 * slot. If the grid holds exactly 50 ordinary coins, the whole grid is
 * cleared and vanilla then operates on an empty input.</p>
 */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Inject(
            method = "onTake",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/ResultSlot;checkTakeAchievements(Lnet/minecraft/world/item/ItemStack;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void simplefinances$consumeMergedCoins(Player player, ItemStack result, CallbackInfo ci) {
        int total = 0;
        for (int i = 0; i < this.craftSlots.getContainerSize(); i++) {
            ItemStack stack = this.craftSlots.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!stack.is(ModItems.COIN_1)) {
                return;
            }
            total += stack.getCount();
        }

        if (total != 50) {
            return;
        }

        for (int i = 0; i < this.craftSlots.getContainerSize(); i++) {
            if (this.craftSlots.getItem(i).is(ModItems.COIN_1)) {
                this.craftSlots.setItem(i, ItemStack.EMPTY);
            }
        }
    }
}

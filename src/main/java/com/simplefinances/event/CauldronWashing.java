package com.simplefinances.event;

import com.simplefinances.item.ClumpItem;
import com.simplefinances.item.CoinCondition;
import com.simplefinances.registry.ModItems;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Washing a clump in a water cauldron yields a coin of the matching series.
 * Water is not consumed and the cauldron can be reused indefinitely.
 */
public final class CauldronWashing {
    private CauldronWashing() {
    }

    public static void init() {
        UseBlockCallback.EVENT.register(CauldronWashing::onUseBlock);
    }

    private static InteractionResult onUseBlock(net.minecraft.world.entity.player.Player player,
                                               net.minecraft.world.level.Level world,
                                               InteractionHand hand,
                                               BlockHitResult hitResult) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof ClumpItem clump)) {
            return InteractionResult.PASS;
        }
        if (!world.getBlockState(hitResult.getBlockPos()).is(Blocks.WATER_CAULDRON)) {
            return InteractionResult.PASS;
        }
        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        CoinCondition condition = rollCondition(player);
        Item coin = ModItems.coin(clump.series(), condition);
        if (coin == null) {
            return InteractionResult.PASS;
        }

        if (!player.isCreative()) {
            stack.shrink(1);
        }

        ItemStack reward = new ItemStack(coin);
        if (!player.getInventory().add(reward) && !reward.isEmpty()) {
            player.drop(reward, false);
        }
        world.playSound(null, hitResult.getBlockPos(), SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    /**
     * 80% dirty, 19.9% slightly dirty, 0.1% perfect.
     */
    private static CoinCondition rollCondition(net.minecraft.world.entity.player.Player player) {
        double roll = player.getRandom().nextDouble() * 100.0D;
        if (roll < 80.0D) {
            return CoinCondition.DIRTY;
        }
        if (roll < 99.9D) {
            return CoinCondition.SLIGHTLY_DIRTY;
        }
        return CoinCondition.PERFECT;
    }
}

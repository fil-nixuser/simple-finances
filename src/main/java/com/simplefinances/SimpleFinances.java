package com.simplefinances;

import com.simplefinances.command.FinanceCommands;
import com.simplefinances.economy.FinanceService;
import com.simplefinances.net.ModNetwork;
import com.simplefinances.registry.ModComponents;
import com.simplefinances.registry.ModItems;
import com.simplefinances.registry.ModRecipes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SimpleFinances implements ModInitializer {
    public static final String MOD_ID = "simplefinances";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModComponents.init();
        ModItems.init();
        ModRecipes.init();
        ModNetwork.init();
        FinanceCommands.init();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                FinanceService.onJoin(handler.getPlayer()));

        LOGGER.info("Simple Finances initialized");
    }
}

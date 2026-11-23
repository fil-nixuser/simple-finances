package com.simplefinances.client;

import com.simplefinances.net.ActionPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class SimpleFinancesClient implements ClientModInitializer {
    private static KeyMapping walletKey;

    @Override
    public void onInitializeClient() {
        ClientNetwork.init();

        walletKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.simplefinances.wallet",
                com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "key.categories.simplefinances"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (walletKey.consumeClick()) {
                if (client.player != null && client.screen == null && client.getConnection() != null) {
                    ClientPlayNetworking.send(ActionPayload.requestOpen());
                }
            }
        });
    }
}

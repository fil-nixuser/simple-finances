package com.simplefinances.net;

import com.simplefinances.economy.FinanceService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class ModNetwork {
    private ModNetwork() {
    }

    public static void init() {
        PayloadTypeRegistry.playC2S().register(ActionPayload.TYPE, ActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StatePayload.TYPE, StatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NotifyPayload.TYPE, NotifyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CollectionPayload.TYPE, CollectionPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (payload, context) ->
                context.server().execute(() -> FinanceService.handleAction(context.player(), payload)));
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}

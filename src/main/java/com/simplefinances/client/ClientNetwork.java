package com.simplefinances.client;

import com.simplefinances.client.gui.FinanceScreen;
import com.simplefinances.client.toast.FinanceToast;
import com.simplefinances.net.CollectionPayload;
import com.simplefinances.net.NotifyPayload;
import com.simplefinances.net.StatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientNetwork {
    private ClientNetwork() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> FinanceScreen.handleState(payload)));

        ClientPlayNetworking.registerGlobalReceiver(NotifyPayload.TYPE, (payload, context) ->
                context.client().execute(() -> FinanceToast.show(payload)));

        ClientPlayNetworking.registerGlobalReceiver(CollectionPayload.TYPE, (payload, context) ->
                context.client().execute(() -> FinanceScreen.handleCollection(payload)));
    }
}

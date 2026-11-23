package com.simplefinances.net;

import com.simplefinances.SimpleFinances;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Every wallet/admin button press is sent to the server through this payload.
 * The server validates everything, the client only asks.
 */
public record ActionPayload(Action action, long amount, String target, String text,
                            int tier, long duration, int flag) implements CustomPacketPayload {
    public static final Type<ActionPayload> TYPE = new Type<>(SimpleFinances.id("action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.action.ordinal());
                buf.writeVarLong(payload.amount);
                buf.writeUtf(payload.target, 512);
                buf.writeUtf(payload.text, 512);
                buf.writeVarInt(payload.tier);
                buf.writeVarLong(payload.duration);
                buf.writeVarInt(payload.flag);
            },
            buf -> new ActionPayload(
                    Action.byIndex(buf.readVarInt()),
                    buf.readVarLong(),
                    buf.readUtf(512),
                    buf.readUtf(512),
                    buf.readVarInt(),
                    buf.readVarLong(),
                    buf.readVarInt()
            )
    );

    public static ActionPayload requestOpen() {
        return new ActionPayload(Action.REQUEST_OPEN, 0L, "", "", 0, 0L, 0);
    }

    public static ActionPayload deposit(long amount) {
        return new ActionPayload(Action.DEPOSIT, amount, "", "", 0, 0L, 0);
    }

    public static ActionPayload withdraw(long amount) {
        return new ActionPayload(Action.WITHDRAW, amount, "", "", 0, 0L, 0);
    }

    public static ActionPayload transfer(String target, long amount) {
        return new ActionPayload(Action.TRANSFER, amount, target == null ? "" : target, "", 0, 0L, 0);
    }

    public static ActionPayload adminBlock(String target, long minutes, String reason) {
        return new ActionPayload(Action.ADMIN_BLOCK, 0L, target == null ? "" : target,
                reason == null ? "" : reason, 0, minutes, 0);
    }

    public static ActionPayload adminUnblock(String target) {
        return new ActionPayload(Action.ADMIN_UNBLOCK, 0L, target == null ? "" : target, "", 0, 0L, 0);
    }

    public static ActionPayload adminCollectible(int tier, String description, String recipients, boolean all) {
        return new ActionPayload(Action.ADMIN_COLLECTIBLE, 0L,
                recipients == null ? "" : recipients,
                description == null ? "" : description,
                tier, 0L, all ? 1 : 0);
    }

    public static ActionPayload adminConfig(int feePercent, int cooldownSeconds) {
        return new ActionPayload(Action.ADMIN_CONFIG, feePercent, "", "", 0, cooldownSeconds, 0);
    }

    public static ActionPayload convertCollectible(int slot) {
        return new ActionPayload(Action.CONVERT_COLLECTIBLE, slot, "", "", 0, 0L, 0);
    }

    public static ActionPayload requestCollection() {
        return requestCollection(0);
    }

    public static ActionPayload requestCollection(int page) {
        return new ActionPayload(Action.REQUEST_COLLECTION, Math.max(0, page), "", "", 0, 0L, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        REQUEST_OPEN,
        DEPOSIT,
        WITHDRAW,
        TRANSFER,
        ADMIN_BLOCK,
        ADMIN_UNBLOCK,
        ADMIN_COLLECTIBLE,
        ADMIN_CONFIG,
        CONVERT_COLLECTIBLE,
        REQUEST_COLLECTION;

        public static Action byIndex(int index) {
            Action[] values = values();
            if (index < 0 || index >= values.length) {
                return REQUEST_OPEN;
            }
            return values[index];
        }
    }
}

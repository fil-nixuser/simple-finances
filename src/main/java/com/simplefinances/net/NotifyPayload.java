package com.simplefinances.net;

import com.simplefinances.SimpleFinances;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Small server -> client notification shown as an advancement-like toast.
 */
public record NotifyPayload(Kind kind, long amount, String name, long until, int tier) implements CustomPacketPayload {
    public static final Type<NotifyPayload> TYPE = new Type<>(SimpleFinances.id("notify"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NotifyPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.kind.ordinal());
                buf.writeVarLong(payload.amount);
                buf.writeUtf(payload.name, 256);
                buf.writeLong(payload.until);
                buf.writeVarInt(payload.tier);
            },
            buf -> new NotifyPayload(
                    Kind.byIndex(buf.readVarInt()),
                    buf.readVarLong(),
                    buf.readUtf(256),
                    buf.readLong(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Kind {
        RECEIVED,
        DEPOSIT,
        BLOCKED,
        UNBLOCKED,
        COLLECTIBLE;

        public static Kind byIndex(int index) {
            Kind[] values = values();
            if (index < 0 || index >= values.length) {
                return RECEIVED;
            }
            return values[index];
        }
    }
}

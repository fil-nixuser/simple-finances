package com.simplefinances.net;

import com.simplefinances.SimpleFinances;
import com.simplefinances.economy.CollectionEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * One page of the shared, read-only collection of collectible coins donated by players.
 */
public record CollectionPayload(int page, int pageCount, int total, List<EntryView> entries)
        implements CustomPacketPayload {
    public static final Type<CollectionPayload> TYPE = new Type<>(SimpleFinances.id("collection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CollectionPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.page);
                buf.writeVarInt(payload.pageCount);
                buf.writeVarInt(payload.total);
                buf.writeVarInt(payload.entries.size());
                for (EntryView entry : payload.entries) {
                    buf.writeUtf(entry.tier, 16);
                    buf.writeUtf(entry.description, 200);
                    buf.writeUtf(entry.date, 64);
                    buf.writeUtf(entry.recipient, 64);
                    buf.writeUtf(entry.addedBy, 64);
                    buf.writeLong(entry.addedAt);
                }
            },
            buf -> {
                int page = buf.readVarInt();
                int pageCount = buf.readVarInt();
                int total = buf.readVarInt();
                int count = Math.min(buf.readVarInt(), 1000);
                List<EntryView> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    entries.add(new EntryView(
                            buf.readUtf(16),
                            buf.readUtf(200),
                            buf.readUtf(64),
                            buf.readUtf(64),
                            buf.readUtf(64),
                            buf.readLong()
                    ));
                }
                return new CollectionPayload(page, pageCount, total, entries);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record EntryView(String tier, String description, String date, String recipient,
                            String addedBy, long addedAt) {
        public static EntryView from(CollectionEntry entry) {
            return new EntryView(entry.tier(), entry.description(), entry.date(), entry.recipient(),
                    entry.addedBy(), entry.addedAt());
        }
    }
}

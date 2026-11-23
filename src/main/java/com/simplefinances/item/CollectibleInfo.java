package com.simplefinances.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data stored on a collectible coin: which event it was awarded for and when.
 */
public record CollectibleInfo(String tier, String description, String date, String recipient) {
    public static final Codec<CollectibleInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("tier").forGetter(CollectibleInfo::tier),
            Codec.STRING.optionalFieldOf("description", "").forGetter(CollectibleInfo::description),
            Codec.STRING.optionalFieldOf("date", "").forGetter(CollectibleInfo::date),
            Codec.STRING.optionalFieldOf("recipient", "").forGetter(CollectibleInfo::recipient)
    ).apply(instance, CollectibleInfo::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CollectibleInfo> STREAM_CODEC = StreamCodec.of(
            (buf, info) -> {
                buf.writeUtf(info.tier, 32);
                buf.writeUtf(info.description, 512);
                buf.writeUtf(info.date, 64);
                buf.writeUtf(info.recipient, 64);
            },
            buf -> new CollectibleInfo(
                    buf.readUtf(32),
                    buf.readUtf(512),
                    buf.readUtf(64),
                    buf.readUtf(64)
            )
    );

    public CollectibleTier tierEnum() {
        return CollectibleTier.byId(tier);
    }
}

package com.simplefinances.registry;

import com.simplefinances.SimpleFinances;
import com.simplefinances.item.CollectibleInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;

public final class ModComponents {
    public static final DataComponentType<CollectibleInfo> COLLECTIBLE_INFO = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            SimpleFinances.id("collectible_info"),
            DataComponentType.<CollectibleInfo>builder()
                    .persistent(CollectibleInfo.CODEC)
                    .networkSynchronized(CollectibleInfo.STREAM_CODEC)
                    .build()
    );

    private ModComponents() {
    }

    public static void init() {
        // Forces class initialization.
    }
}

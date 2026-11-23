package com.simplefinances.economy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class OfflineUuids {
    private OfflineUuids() {
    }

    /**
     * Same algorithm the vanilla server uses in offline mode.
     */
    public static UUID of(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }
}

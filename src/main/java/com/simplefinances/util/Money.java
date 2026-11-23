package com.simplefinances.util;

import java.util.Locale;

public final class Money {
    private Money() {
    }

    public static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
    }
}

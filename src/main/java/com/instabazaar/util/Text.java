package com.instabazaar.util;

import java.text.Normalizer;
import java.util.Locale;

public final class Text {
    private Text() {}

    public static String clean(String value) { return value == null ? "" : value.strip(); }
    public static String normalized(String value) {
        return Normalizer.normalize(clean(value), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}

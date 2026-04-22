package com.access.unpack.core;

import java.text.Normalizer;
import java.util.Locale;

public final class Slugifier {
    private Slugifier() {
    }

    public static String slug(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("(^-+|-+$)", "")
                .toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "unnamed" : normalized;
    }
}

package com.access.unpack.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SqlTranslator {
    public TranslationResult translate(String accessSql, SqlDialect dialect) {
        if (dialect == SqlDialect.NONE || accessSql == null || accessSql.isBlank()) {
            return new TranslationResult(null, 0.0, List.of("Translation disabled or empty SQL"));
        }

        String translated = accessSql;
        List<String> warnings = new ArrayList<>();
        double confidence = 0.45;
        String lower = accessSql.toLowerCase(Locale.ROOT);

        if (lower.contains("iif(") || lower.contains("nz(") || lower.contains("dlookup(")
                || lower.contains("format$(") || lower.contains("date()") || lower.contains("now()")) {
            warnings.add("Access-specific function detected; translation left conservative");
            confidence -= 0.2;
        }
        if (accessSql.contains("*") && lower.contains(" like ")) {
            warnings.add("Wildcard semantics may differ outside Access");
            confidence -= 0.1;
        }
        translated = translated.replace("[", "\"").replace("]", "\"");

        return new TranslationResult(translated, Math.max(0.0, confidence), warnings);
    }

    public record TranslationResult(String sql, double confidence, List<String> warnings) {
    }
}

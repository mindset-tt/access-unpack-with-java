package com.access.unpack.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OutputLayout {
    public static final String[] DIRECTORIES = {
            "schema/tables",
            "data",
            "queries/raw",
            "queries/sql",
            "queries/translated/ansi",
            "queries/translated/postgres",
            "queries/translated/mysql",
            "queries/translated/sqlserver",
            "queries/model",
            "vba/raw",
            "vba/modules",
            "vba/classes",
            "vba/forms",
            "vba/reports",
            "forms/raw",
            "forms/model",
            "forms/control-tree",
            "forms/event-map",
            "reports/raw",
            "reports/model",
            "reports/previews",
            "reports/jrxml",
            "reports/crystal",
            "reports/conversion-map",
            "macros/raw",
            "macros/model",
            "startup",
            "docs"
    };

    public void create(Path root) throws IOException {
        for (String dir : DIRECTORIES) {
            Files.createDirectories(root.resolve(dir));
        }
    }

    public void writeUnsupportedPlaceholder(Path root, String relativeFile, String category, String reason, String rawSourceHint)
            throws IOException {
        Path file = root.resolve(relativeFile);
        Files.createDirectories(file.getParent());
        Jsons.MAPPER.writeValue(file.toFile(), Map.of(
                "category", category,
                "status", "unsupported",
                "reason", reason,
                "rawSourceHint", rawSourceHint));
    }

    public Map<String, Object> sampleTree() {
        Map<String, Object> tree = new LinkedHashMap<>();
        for (String dir : DIRECTORIES) {
            tree.put(dir, "created");
        }
        return tree;
    }
}

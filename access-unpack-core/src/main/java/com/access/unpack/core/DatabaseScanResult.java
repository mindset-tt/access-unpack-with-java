package com.access.unpack.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record DatabaseScanResult(
        Path input,
        boolean openable,
        String classification,
        String headline,
        String recommendation,
        Path suggestedAlternative,
        int tableCount,
        int queryCount,
        List<TableScanEntry> tables,
        List<QueryScanEntry> queries,
        List<Diagnostic> diagnostics,
        Map<String, Object> metadata) {
}

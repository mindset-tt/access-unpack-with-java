package com.access.unpack.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record ExtractionResult(
        Path outputPath,
        Map<String, Object> manifest,
        List<CoverageEntry> coverage,
        List<Diagnostic> warnings,
        List<Diagnostic> errors) {
}

package com.access.unpack.core;

import java.util.Map;

public record CoverageEntry(
        String category,
        String name,
        ArtifactStatus status,
        double confidence,
        String reason,
        Map<String, Object> details) {
}

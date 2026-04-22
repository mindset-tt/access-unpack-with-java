package com.access.unpack.core;

import java.time.Instant;
import java.util.Map;

public record Diagnostic(
        String code,
        String severity,
        String message,
        String objectType,
        String objectName,
        Map<String, Object> details,
        Instant timestamp) {
}

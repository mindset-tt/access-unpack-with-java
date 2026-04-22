package com.access.unpack.core;

public record QueryScanEntry(
        String name,
        String slug,
        String type,
        int parameterCount,
        String sql) {
}

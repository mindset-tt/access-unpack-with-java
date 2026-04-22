package com.access.unpack.core;

public record TableScanEntry(
        String name,
        String slug,
        boolean systemObject,
        boolean linked,
        int rowCount,
        int columnCount) {
}

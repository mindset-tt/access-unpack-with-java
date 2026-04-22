package com.access.unpack.core;

public enum ExportDataFormat {
    CSV,
    JSON,
    PARQUET,
    ALL;

    public boolean includes(ExportDataFormat other) {
        return this == ALL || this == other;
    }
}

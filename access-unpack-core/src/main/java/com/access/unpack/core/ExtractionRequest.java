package com.access.unpack.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public record ExtractionRequest(
        Path input,
        Path output,
        List<String> targetReportFormats,
        FullFidelityMode fullFidelity,
        boolean includeSystemObjects,
        ExportDataFormat exportData,
        TextEncoding textEncoding,
        SqlDialect translateSql,
        boolean validate,
        boolean extractSchema,
        boolean extractData,
        boolean extractQueries,
        boolean writeDocs,
        Set<String> selectedTables,
        Set<String> selectedQueries) {

    public ExtractionRequest(
            Path input,
            Path output,
            List<String> targetReportFormats,
            FullFidelityMode fullFidelity,
            boolean includeSystemObjects,
            ExportDataFormat exportData,
            TextEncoding textEncoding,
            SqlDialect translateSql,
            boolean validate) {
        this(input, output, targetReportFormats, fullFidelity, includeSystemObjects, exportData, textEncoding, translateSql,
                validate, true, true, true, true, Set.of(), Set.of());
    }
}

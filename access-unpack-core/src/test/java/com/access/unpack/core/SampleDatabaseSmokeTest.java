package com.access.unpack.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class SampleDatabaseSmokeTest {
    @Test
    void extractsWorkspaceSampleMdbWhenPresent() throws Exception {
        Path workspace = Path.of("").toAbsolutePath().getParent();
        Path sample = workspace.resolve("akatahara").resolve("水利地益TBL.mdb");
        assumeTrue(Files.exists(sample), "Sample MDB not present in this workspace");

        Path output = Files.createTempDirectory("access-unpack-smoke-");
        ExtractionRequest request = new ExtractionRequest(
                sample,
                output,
                List.of("jrxml", "crystal"),
                FullFidelityMode.AUTO,
                false,
                ExportDataFormat.JSON,
                TextEncoding.UTF8,
                SqlDialect.NONE,
                true,
                true,
                true,
                true,
                true,
                Set.of(),
                Set.of());

        ExtractionResult result = new AccessUnpackService().extract(request);
        assertTrue(Files.exists(result.outputPath().resolve("manifest.json")));
        assertTrue(Files.exists(result.outputPath().resolve("coverage.json")));
        assertTrue(Files.exists(result.outputPath().resolve("schema/tables")));
    }
}

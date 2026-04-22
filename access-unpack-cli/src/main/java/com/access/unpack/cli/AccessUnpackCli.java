package com.access.unpack.cli;

import com.access.unpack.core.AccessUnpackService;
import com.access.unpack.core.ExportDataFormat;
import com.access.unpack.core.ExtractionRequest;
import com.access.unpack.core.FullFidelityMode;
import com.access.unpack.core.SqlDialect;
import com.access.unpack.core.TextEncoding;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(name = "access-unpack",
        mixinStandardHelpOptions = true,
        description = "Preservation-first unpacker for Microsoft Access databases")
public final class AccessUnpackCli implements Callable<Integer> {
    @Option(names = "--input", required = true, description = "Path to the Access database file")
    private Path input;

    @Option(names = "--output", required = true, description = "Output folder")
    private Path output;

    @Option(names = "--target-report-formats", defaultValue = "jrxml,crystal",
            description = "Comma-separated report targets")
    private String targetReportFormats;

    @Option(names = "--full-fidelity", defaultValue = "auto",
            description = "auto|on|off")
    private FullFidelityMode fullFidelity;

    @Option(names = "--include-system-objects", defaultValue = "false", arity = "1",
            description = "true|false")
    private boolean includeSystemObjects;

    @Option(names = "--export-data", defaultValue = "all",
            description = "csv|json|parquet|all")
    private ExportDataFormat exportData;

    @Option(names = "--text-encoding", defaultValue = "utf8",
            description = "utf8|utf8_bom|windows_31j|shift_jis")
    private TextEncoding textEncoding;

    @Option(names = "--translate-sql", defaultValue = "none",
            description = "ansi|postgres|mysql|sqlserver|none")
    private SqlDialect translateSql;

    @Option(names = "--validate", defaultValue = "true", arity = "1",
            description = "true|false")
    private boolean validate;

    public static void main(String[] args) {
        CommandLine commandLine = new CommandLine(new AccessUnpackCli());
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        List<String> reportFormats = Arrays.stream(targetReportFormats.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        ExtractionRequest request = new ExtractionRequest(
                input,
                output,
                reportFormats,
                fullFidelity,
                includeSystemObjects,
                exportData,
                textEncoding,
                translateSql,
                validate,
                true,
                true,
                true,
                true,
                Set.of(),
                Set.of());

        var service = new AccessUnpackService();
        var result = service.extract(request);
        System.out.printf("access-unpack completed. Output: %s%n", result.outputPath().toAbsolutePath());
        System.out.printf("Coverage entries: %d, warnings: %d, errors: %d%n",
                result.coverage().size(), result.warnings().size(), result.errors().size());
        return result.errors().isEmpty() ? 0 : 2;
    }

}

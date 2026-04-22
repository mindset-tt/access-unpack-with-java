package com.access.unpack.core;

import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Index;
import com.healthmarketscience.jackcess.PropertyMap;
import com.healthmarketscience.jackcess.Relationship;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;
import com.healthmarketscience.jackcess.query.Query;
import org.apache.avro.Schema;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.OutputFile;
import org.apache.parquet.io.PositionOutputStream;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class AccessUnpackService {
    private static final Pattern SQL_DEP_PATTERN = Pattern.compile("(?i)(from|join|update|into)\\s+\\[?([\\p{L}\\p{N}_ ]+)\\]?");

    private final EnvironmentProbe environmentProbe = new EnvironmentProbe();
    private final OutputLayout outputLayout = new OutputLayout();
    private final PlaceholderWriter placeholderWriter = new PlaceholderWriter();
    private final SqlTranslator sqlTranslator = new SqlTranslator();

    public DatabaseScanResult scanDatabase(Path input, boolean includeSystemObjects) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<TableScanEntry> tables = new ArrayList<>();
        List<QueryScanEntry> queries = new ArrayList<>();
        Map<String, Object> metadata = new LinkedHashMap<>();
        Path normalizedInput = input.toAbsolutePath().normalize();
        String fileName = normalizedInput.getFileName().toString();
        String upperFileName = fileName.toUpperCase(Locale.ROOT);
        String classification = upperFileName.contains("SYS") ? "likely-front-end"
                : (upperFileName.contains("TBL") ? "likely-data" : "unknown");
        Path suggestedAlternative = guessSiblingAlternative(normalizedInput, classification);
        String headline = switch (classification) {
            case "likely-front-end" -> "This file looks like a front-end/control database";
            case "likely-data" -> "This file looks like a data database";
            default -> "Database type is not obvious from the filename";
        };
        String recommendation = switch (classification) {
            case "likely-front-end" -> suggestedAlternative != null
                    ? "If extraction fails because of linked tables, try the sibling data file: " + suggestedAlternative.getFileName()
                    : "Front-end databases often point at linked tables. Keep an eye out for stale paths.";
            case "likely-data" -> "This is the best input when you want cross-platform schema and table export.";
            default -> "Use the preflight results below to decide whether this is a front-end shell or a data file.";
        };

        boolean openable = false;
        try (Database database = DatabaseBuilder.open(normalizedInput.toFile())) {
            database.setLinkResolver(new LinkedDatabaseResolver(normalizedInput, diagnostics));
            metadata.put("fileFormat", String.valueOf(database.getFileFormat()));
            metadata.put("charset", database.getCharset() != null ? database.getCharset().displayName() : null);
            metadata.put("queryCount", database.getQueries().size());
            for (Query query : database.getQueries()) {
                String sql = query.toSQLString();
                queries.add(new QueryScanEntry(
                        query.getName(),
                        Slugifier.slug(query.getName()),
                        query.getType().name().toLowerCase(Locale.ROOT),
                        query.getParameters().size(),
                        sql != null ? sql : ""));
            }
            for (Table table : database) {
                if (table.isSystem() && !includeSystemObjects) {
                    continue;
                }
                tables.add(new TableScanEntry(
                        table.getName(),
                        Slugifier.slug(table.getName()),
                        table.isSystem(),
                        database.isLinkedTable(table),
                        safeRowCount(table),
                        table.getColumnCount()));
            }
            openable = true;
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            Map<String, Object> details = new LinkedHashMap<>();
            if (message.contains("C:\\") && (message.contains(".mdb") || message.contains(".accdb"))) {
                details.put("linkedPathHint", message);
                if (suggestedAlternative != null) {
                    details.put("suggestedAlternative", suggestedAlternative.toString());
                }
                diagnostics.add(diag("PREFLIGHT_LINKED_PATH_UNAVAILABLE", "warning",
                        "This looks like a front-end database with a stale linked-table path",
                        "database", fileName, details));
            } else {
                diagnostics.add(diag("PREFLIGHT_SCAN_FAILED", "warning",
                        message, "database", fileName, details));
            }
        }

        tables.sort(Comparator.comparing(TableScanEntry::name, String.CASE_INSENSITIVE_ORDER));
        queries.sort(Comparator.comparing(QueryScanEntry::name, String.CASE_INSENSITIVE_ORDER));
        int queryCount = metadata.get("queryCount") instanceof Integer count ? count : 0;
        return new DatabaseScanResult(
                normalizedInput,
                openable,
                classification,
                headline,
                recommendation,
                suggestedAlternative,
                tables.size(),
                queryCount,
                tables,
                queries,
                diagnostics,
                metadata);
    }

    public ExtractionResult extract(ExtractionRequest request) throws IOException {
        List<CoverageEntry> coverage = new ArrayList<>();
        List<Diagnostic> warnings = new ArrayList<>();
        List<Diagnostic> errors = new ArrayList<>();

        Files.createDirectories(request.output());
        outputLayout.create(request.output());

        Map<String, Object> environment = environmentProbe.inspect(request);
        boolean windowsHelperAvailable = Boolean.TRUE.equals(environment.get("windowsAccessAvailable"));
        Jsons.MAPPER.writeValue(request.output().resolve("environment.json").toFile(), environment);

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("tool", "access-unpack");
        manifest.put("version", "0.1.0-SNAPSHOT");
        manifest.put("generatedAt", Instant.now());
        manifest.put("input", request.input().toAbsolutePath().toString());
        manifest.put("output", request.output().toAbsolutePath().toString());
        manifest.put("textEncoding", request.textEncoding().name());
        manifest.put("selectedTables", request.selectedTables());
        manifest.put("selectedQueries", request.selectedQueries());
        manifest.put("selectedScopes", Map.of(
                "schema", request.extractSchema(),
                "data", request.extractData(),
                "queries", request.extractQueries(),
                "docs", request.writeDocs()));
        manifest.put("sampleTree", outputLayout.sampleTree());
        manifest.put("phases", List.of(
                phase("Phase 1", "implemented", "schema, table metadata, query inventory, data export"),
                phase("Phase 2", windowsHelperAvailable ? "available" : "requires Windows helper", "raw forms/reports/macros/modules via Access automation"),
                phase("Phase 3", "partial", "normalized IR and dependency summaries"),
                phase("Phase 4", "partial", "JRXML scaffolding; Crystal only with supported SDK/runtime"),
                phase("Phase 5", "in-progress", "tests, hardening, retry, secure logging")));

        String extension = extension(request.input().getFileName().toString());
        Map<String, Object> dbInfo = new LinkedHashMap<>();
        dbInfo.put("fileName", request.input().getFileName().toString());
        dbInfo.put("fileExtension", extension);
        dbInfo.put("inputSha256", Hashes.sha256(request.input()));
        dbInfo.put("sizeBytes", Files.size(request.input()));

        Map<String, Object> inventory = new LinkedHashMap<>();
        inventory.put("tables", 0);
        inventory.put("queries", 0);
        inventory.put("relationships", 0);
        inventory.put("forms", 0);
        inventory.put("reports", 0);
        inventory.put("macros", 0);
        inventory.put("modules", 0);
        manifest.put("inventory", inventory);

        try (Database database = DatabaseBuilder.open(request.input().toFile())) {
            database.setLinkResolver(new LinkedDatabaseResolver(request.input(), warnings));
            dbInfo.put("fileFormat", String.valueOf(database.getFileFormat()));
            dbInfo.put("charset", database.getCharset() != null ? database.getCharset().displayName() : null);
            dbInfo.put("databaseProperties", readPropertyMap(database.getDatabaseProperties()));
            dbInfo.put("summaryProperties", readPropertyMap(database.getSummaryProperties()));
            dbInfo.put("userProperties", readPropertyMap(database.getUserDefinedProperties()));
            Jsons.MAPPER.writeValue(request.output().resolve("dbinfo.json").toFile(), dbInfo);

            if (request.extractSchema() || request.extractData()) {
                extractTables(database, request, coverage, warnings, errors, inventory);
            }
            if (request.extractSchema()) {
                extractRelationships(database, request.output(), coverage, warnings, request, inventory);
            }
            if (request.extractQueries()) {
                extractQueries(database, request, coverage, warnings, inventory);
            }
        } catch (Exception e) {
            String message = e.getMessage();
            Map<String, Object> details = new LinkedHashMap<>();
            if (message != null && message.contains("C:\\") && (message.contains(".mdb") || message.contains(".accdb"))) {
                message = "Database open failed because a linked Access file points to a Windows-only path that is not present here";
                details.put("linkedPathHint", e.getMessage());
                details.put("suggestion", "Run against the data .mdb/.accdb directly, or use the Windows helper on a machine where the original linked path is available.");
                warnings.add(diag("LINKED_DB_PATH_UNAVAILABLE", "warning",
                        "Detected a stale Windows linked-table path during open",
                        "database", request.input().getFileName().toString(), details));
            }
            errors.add(diag("DATABASE_OPEN_FAILED", "error", message, "database", request.input().getFileName().toString(), details));
            coverage.add(new CoverageEntry("database", request.input().getFileName().toString(), ArtifactStatus.FAILED, 0.0,
                    "Database could not be opened", Map.of("exception", e.toString())));
        }

        String helperReason = request.fullFidelity() == FullFidelityMode.OFF
                ? "Helper-backed recovery was disabled by request; cross-platform file parsing still ran"
                : "Cross-platform file parsing ran, but these artifacts were not recoverable without helper-backed extraction";
        if (request.fullFidelity() == FullFidelityMode.ON && !windowsHelperAvailable) {
            warnings.add(diag("FULL_FIDELITY_UNAVAILABLE", "warning",
                    "Full-fidelity extraction requested but Microsoft Access automation is unavailable",
                    "environment", "windows-helper", Map.of()));
        }
        coverage.add(new CoverageEntry("forms", "*", windowsHelperAvailable ? ArtifactStatus.PARTIAL : ArtifactStatus.UNSUPPORTED,
                windowsHelperAvailable ? 0.4 : 0.0, helperReason, Map.of()));
        coverage.add(new CoverageEntry("reports", "*", windowsHelperAvailable ? ArtifactStatus.PARTIAL : ArtifactStatus.UNSUPPORTED,
                windowsHelperAvailable ? 0.4 : 0.0, helperReason, Map.of()));
        coverage.add(new CoverageEntry("macros", "*", windowsHelperAvailable ? ArtifactStatus.PARTIAL : ArtifactStatus.UNSUPPORTED,
                windowsHelperAvailable ? 0.3 : 0.0, helperReason, Map.of()));
        coverage.add(new CoverageEntry("vba", "*", windowsHelperAvailable ? ArtifactStatus.PARTIAL : ArtifactStatus.UNSUPPORTED,
                windowsHelperAvailable ? 0.3 : 0.0, helperReason, Map.of()));
        placeholderWriter.writeCoverageBoundPlaceholders(request.output(), windowsHelperAvailable);

        if (request.writeDocs()) {
            writeDocs(request.output(), inventory, coverage, warnings, environment, dbInfo);
        }
        writeCoverage(request.output(), coverage);
        Jsons.MAPPER.writeValue(request.output().resolve("warnings.json").toFile(), warnings);
        Jsons.MAPPER.writeValue(request.output().resolve("errors.json").toFile(), errors);
        Jsons.MAPPER.writeValue(request.output().resolve("manifest.json").toFile(), manifest);

        if (request.validate()) {
            validate(request.output(), coverage, warnings);
        }
        return new ExtractionResult(request.output(), manifest, coverage, warnings, errors);
    }

    private void extractTables(Database database, ExtractionRequest request, List<CoverageEntry> coverage, List<Diagnostic> warnings,
                               List<Diagnostic> errors, Map<String, Object> inventory) throws IOException {
        List<Table> tables = new ArrayList<>();
        for (Table table : database) {
            tables.add(table);
        }
        tables.sort(Comparator.comparing(Table::getName, String.CASE_INSENSITIVE_ORDER));

        List<Map<String, Object>> linkedTables = new ArrayList<>();
        List<Map<String, Object>> systemObjects = new ArrayList<>();

        int tableCount = 0;
        for (Table table : tables) {
            boolean system = table.isSystem();
            if (system && !request.includeSystemObjects()) {
                continue;
            }
            if (!shouldExtractTable(table.getName(), request.selectedTables())) {
                continue;
            }

            tableCount++;
            String slug = Slugifier.slug(table.getName());
            List<Map<String, Object>> columns = table.getColumns().stream()
                    .map(column -> {
                        Map<String, Object> columnInfo = new LinkedHashMap<>();
                        columnInfo.put("name", column.getName());
                        columnInfo.put("slug", Slugifier.slug(column.getName()));
                        columnInfo.put("type", String.valueOf(column.getType()));
                        columnInfo.put("length", column.getLength());
                        columnInfo.put("variableLength", column.isVariableLength());
                        columnInfo.put("autoNumber", column.isAutoNumber());
                        return columnInfo;
                    })
                    .toList();

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Row row : table) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (String columnName : columnNames(table)) {
                    normalized.put(columnName, normalizeValue(row.get(columnName)));
                }
                rows.add(normalized);
            }

            Map<String, Object> tableSchema = new LinkedHashMap<>();
            tableSchema.put("name", table.getName());
            tableSchema.put("slug", slug);
            tableSchema.put("systemObject", system);
            tableSchema.put("rowCount", rows.size());
            tableSchema.put("columns", columns);
            tableSchema.put("indexes", table.getIndexes().stream().map(this::indexInfo).toList());
            tableSchema.put("createdDate", table.getCreatedDate());
            tableSchema.put("updatedDate", table.getUpdatedDate());
            Path tableSchemaPath = request.output().resolve("schema/tables").resolve(slug + ".json");
            if (request.extractSchema()) {
                Jsons.MAPPER.writeValue(tableSchemaPath.toFile(), tableSchema);
            }

            Map<String, Object> schemaMeta = new LinkedHashMap<>();
            schemaMeta.put("name", table.getName());
            schemaMeta.put("slug", slug);
            schemaMeta.put("rowCount", rows.size());
            if (request.extractSchema()) {
                schemaMeta.put("schemaPath", request.output().relativize(tableSchemaPath).toString());
            }

            if (request.extractData() && request.exportData().includes(ExportDataFormat.CSV)) {
                Path csvPath = request.output().resolve("data").resolve(slug + ".csv");
                exportCsv(table, rows, csvPath, request.textEncoding());
                schemaMeta.put("csvSha256", Hashes.sha256(csvPath));
            }
            if (request.extractData() && request.exportData().includes(ExportDataFormat.JSON)) {
                Path jsonPath = request.output().resolve("data").resolve(slug + ".json");
                Jsons.MAPPER.writeValue(jsonPath.toFile(), rows);
                schemaMeta.put("jsonSha256", Hashes.sha256(jsonPath));
            }
            if (request.extractData() && request.exportData().includes(ExportDataFormat.PARQUET)) {
                Path parquetPath = request.output().resolve("data").resolve(slug + ".parquet");
                try {
                    exportParquet(table, rows, parquetPath);
                    schemaMeta.put("parquetSha256", Hashes.sha256(parquetPath));
                } catch (Exception e) {
                    warnings.add(diag("PARQUET_EXPORT_PARTIAL", "warning",
                            "Parquet export failed for table " + table.getName(),
                            "table", table.getName(), Map.of("exception", e.toString())));
                    Jsons.MAPPER.writeValue(request.output().resolve("data").resolve(slug + ".parquet.metadata.json").toFile(), Map.of(
                            "status", "partial",
                            "reason", e.getMessage(),
                            "rawSourceHint", "data/" + slug + ".json"));
                }
            }

            coverage.add(new CoverageEntry("table", table.getName(), ArtifactStatus.SUCCESS, 0.95,
                    "Schema and row export completed", schemaMeta));

            if (database.isLinkedTable(table)) {
                Map<String, Object> linkInfo = new LinkedHashMap<>();
                linkInfo.put("name", table.getName());
                linkInfo.put("type", "linked");
                linkInfo.put("properties", readPropertyMap(table.getProperties()));
                linkedTables.add(linkInfo);
            }
            if (system) {
                systemObjects.add(Map.of("name", table.getName(), "type", "table"));
            }
        }

        inventory.put("tables", tableCount);
        if (request.extractSchema()) {
            Jsons.MAPPER.writeValue(request.output().resolve("schema/linked-tables.json").toFile(), linkedTables);
            Jsons.MAPPER.writeValue(request.output().resolve("schema/system-objects.json").toFile(), systemObjects);
        }
    }

    private void extractRelationships(Database database, Path output, List<CoverageEntry> coverage, List<Diagnostic> warnings,
                                      ExtractionRequest request, Map<String, Object> inventory) throws IOException {
        List<Map<String, Object>> relationships = new ArrayList<>();
        List<Map<String, Object>> indexes = new ArrayList<>();
        List<Map<String, Object>> constraints = new ArrayList<>();

        try {
            for (Relationship relationship : database.getRelationships()) {
                if (!shouldExtractTable(relationship.getFromTable().getName(), request.selectedTables())
                        || !shouldExtractTable(relationship.getToTable().getName(), request.selectedTables())) {
                    continue;
                }
                Map<String, Object> relation = new LinkedHashMap<>();
                relation.put("name", relationship.getName());
                relation.put("fromTable", relationship.getFromTable().getName());
                relation.put("toTable", relationship.getToTable().getName());
                relation.put("fromColumns", relationship.getFromColumns().stream().map(c -> c.getName()).toList());
                relation.put("toColumns", relationship.getToColumns().stream().map(c -> c.getName()).toList());
                relation.put("cascadeDeletes", relationship.cascadeDeletes());
                relation.put("cascadeUpdates", relationship.cascadeUpdates());
                relationships.add(relation);
                constraints.add(relation);
                coverage.add(new CoverageEntry("relationship", relationship.getName(), ArtifactStatus.SUCCESS, 0.9,
                        "Relationship extracted", relation));
            }
        } catch (Exception e) {
            warnings.add(diag("RELATIONSHIP_EXTRACTION_PARTIAL", "warning", e.getMessage(), "relationship", "*", Map.of()));
        }

        for (Table table : database) {
            if (!shouldExtractTable(table.getName(), request.selectedTables())) {
                continue;
            }
            for (Index index : table.getIndexes()) {
                Map<String, Object> item = indexInfo(index);
                item.put("table", table.getName());
                indexes.add(item);
            }
        }

        inventory.put("relationships", relationships.size());
        Jsons.MAPPER.writeValue(output.resolve("schema/relationships.json").toFile(), relationships);
        Jsons.MAPPER.writeValue(output.resolve("schema/indexes.json").toFile(), indexes);
        Jsons.MAPPER.writeValue(output.resolve("schema/constraints.json").toFile(), constraints);
    }

    private void extractQueries(Database database, ExtractionRequest request, List<CoverageEntry> coverage, List<Diagnostic> warnings,
                                Map<String, Object> inventory) throws IOException {
        List<Map<String, Object>> graph = new ArrayList<>();
        int queryCount = 0;
        for (Query query : database.getQueries()) {
            if (!shouldExtractQuery(query.getName(), request.selectedQueries())) {
                continue;
            }
            queryCount++;
            String name = query.getName();
            String slug = Slugifier.slug(name);
            String sql = query.toSQLString();
            String rawSql = sql != null ? sql : "";

            writeTextFile(request.output().resolve("queries/raw").resolve(slug + ".access.txt"), rawSql, request.textEncoding());
            writeTextFile(request.output().resolve("queries/sql").resolve(slug + ".sql"), rawSql, request.textEncoding());

            Set<String> dependencies = parseDependencies(rawSql);
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("name", name);
            model.put("slug", slug);
            model.put("type", classifyQuery(rawSql));
            model.put("sql", rawSql);
            model.put("parameters", List.of());
            model.put("dependencies", dependencies);
            model.put("accessSpecificWarnings", accessSqlWarnings(rawSql));
            Jsons.MAPPER.writeValue(request.output().resolve("queries/model").resolve(slug + ".json").toFile(), model);

            if (request.translateSql() != SqlDialect.NONE) {
                SqlTranslator.TranslationResult translated = sqlTranslator.translate(rawSql, request.translateSql());
                if (translated.sql() != null) {
                    String dialectDir = request.translateSql().name().toLowerCase(Locale.ROOT);
                    Path translatedPath = request.output().resolve("queries/translated").resolve(dialectDir).resolve(slug + ".sql");
                    Files.createDirectories(translatedPath.getParent());
                    writeTextFile(translatedPath, translated.sql(), request.textEncoding());
                }
                for (String warning : translated.warnings()) {
                    warnings.add(diag("QUERY_TRANSLATION_WARNING", "warning", warning, "query", name, Map.of()));
                }
                coverage.add(new CoverageEntry("query-translation", name,
                        translated.confidence() >= 0.5 ? ArtifactStatus.SUCCESS : ArtifactStatus.PARTIAL,
                        translated.confidence(), "SQL translation generated conservatively", Map.of("dialect", request.translateSql().name())));
            }

            coverage.add(new CoverageEntry("query", name, ArtifactStatus.SUCCESS, 0.85,
                    "Access SQL preserved and normalized query model written",
                    Map.of("dependencies", dependencies, "type", classifyQuery(rawSql))));

            for (String dependency : dependencies) {
                graph.add(Map.of("from", name, "to", dependency, "type", "query-depends-on"));
            }
        }
        inventory.put("queries", queryCount);
        Jsons.MAPPER.writeValue(request.output().resolve("queries/dependency-graph.json").toFile(), graph);
    }

    private void validate(Path output, List<CoverageEntry> coverage, List<Diagnostic> warnings) throws IOException {
        int missing = 0;
        for (CoverageEntry entry : coverage) {
            Object schemaPath = entry.details() != null ? entry.details().get("schemaPath") : null;
            if (schemaPath instanceof String rel && !Files.exists(output.resolve(rel))) {
                missing++;
            }
        }
        if (missing > 0) {
            warnings.add(diag("VALIDATION_MISSING_ARTIFACTS", "warning",
                    "Validation found missing referenced artifacts: " + missing,
                    "validation", "artifacts", Map.of("count", missing)));
        }
        Jsons.MAPPER.writeValue(output.resolve("coverage.json").toFile(), Map.of(
                "generatedAt", Instant.now(),
                "entries", coverage,
                "summary", summarizeCoverage(coverage)));
    }

    private void writeCoverage(Path output, List<CoverageEntry> coverage) throws IOException {
        Jsons.MAPPER.writeValue(output.resolve("coverage.json").toFile(), Map.of(
                "generatedAt", Instant.now(),
                "entries", coverage,
                "summary", summarizeCoverage(coverage)));
    }

    private void writeDocs(Path output, Map<String, Object> inventory, List<CoverageEntry> coverage, List<Diagnostic> warnings,
                           Map<String, Object> environment, Map<String, Object> dbInfo) throws IOException {
        String summary = """
                # access-unpack summary

                Build mode: preservation first.

                Object counts:
                %s

                Platform:
                - OS: %s
                - Java: %s
                - Access automation available: %s
                """.formatted(
                inventory.entrySet().stream().map(e -> "- " + e.getKey() + ": " + e.getValue()).collect(Collectors.joining("\n")),
                environment.get("osName"),
                environment.get("javaVersion"),
                environment.get("windowsAccessAvailable"));
        writeTextFile(output.resolve("docs/summary.md"), summary, TextEncoding.UTF8);

        String inventoryDoc = coverage.stream()
                .sorted(Comparator.comparing(CoverageEntry::category).thenComparing(CoverageEntry::name))
                .map(entry -> "- `%s` `%s` `%s` confidence=%.2f".formatted(
                        entry.category(), entry.name(), entry.status(), entry.confidence()))
                .collect(Collectors.joining("\n", "# object inventory\n\n", "\n"));
        writeTextFile(output.resolve("docs/object-inventory.md"), inventoryDoc, TextEncoding.UTF8);

        String migrationNotes = """
                # migration notes

                - Use `/schema` and `/data` as the canonical source for rebuilding relational models.
                - Use `/queries/model` and `/queries/dependency-graph.json` to map report and screen data sources.
                - Treat `/forms`, `/reports`, `/macros`, and `/vba` placeholders as explicit evidence that Windows helper extraction is still required for full behavioral recovery.
                - Rebuild entry points from `/startup` once helper-backed metadata is available.
                """;
        writeTextFile(output.resolve("docs/migration-notes.md"), migrationNotes, TextEncoding.UTF8);

        String logic = """
                # business logic summary

                This run can infer business logic from saved queries and naming patterns only.
                VBA, embedded macros, event handlers, and report expressions need the Windows helper or editable source text.
                """;
        writeTextFile(output.resolve("docs/business-logic-summary.md"), logic, TextEncoding.UTF8);

        String gaps = """
                # conversion gaps

                - Access object text export depends on Microsoft Access automation via `SaveAsText`.
                - Report previews depend on Access automation via `OutputTo`.
                - Crystal `.rpt` generation is intentionally not claimed unless a supported Crystal SDK/runtime is present and validated.
                - Translation output is conservative and leaves Access-only expressions flagged instead of rewritten incorrectly.
                """;
        writeTextFile(output.resolve("docs/conversion-gaps.md"), gaps, TextEncoding.UTF8);

        String priorities = """
                # rebuild priorities

                1. Recreate the database schema from `/schema`.
                2. Validate exported data from `/data`.
                3. Port saved queries from `/queries/sql` and `/queries/model`.
                4. Run the Windows helper to recover forms, reports, macros, and VBA.
                5. Use recovered UI and logic artifacts to rebuild workflows in a web, API, or desktop target.
                """;
        writeTextFile(output.resolve("docs/rebuild-priorities.md"), priorities, TextEncoding.UTF8);
    }

    private void exportCsv(Table table, List<Map<String, Object>> rows, Path path, TextEncoding encoding) throws IOException {
        writeBomIfNeeded(path, encoding);
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(path, java.nio.file.StandardOpenOption.APPEND), encoding.charset());
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(columnNames(table).toArray(String[]::new)))) {
            for (Map<String, Object> row : rows) {
                List<Object> values = columnNames(table).stream()
                        .map(name -> row.get(name))
                        .toList();
                printer.printRecord(values);
            }
        }
    }

    private void writeTextFile(Path path, String text, TextEncoding encoding) throws IOException {
        writeBomIfNeeded(path, encoding);
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(path, java.nio.file.StandardOpenOption.APPEND), encoding.charset())) {
            writer.write(text);
        }
    }

    private void writeBomIfNeeded(Path path, TextEncoding encoding) throws IOException {
        Files.deleteIfExists(path);
        Files.createDirectories(path.getParent());
        try (OutputStream out = Files.newOutputStream(path)) {
            if (encoding.bom()) {
                out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            }
        }
    }

    private void exportParquet(Table table, List<Map<String, Object>> rows, Path path) throws IOException {
        Schema schema = buildAvroSchema(table);
        try (var writer = AvroParquetWriter.<GenericRecord>builder(new PathOutputFile(path))
                .withSchema(schema)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .build()) {
            for (Map<String, Object> row : rows) {
                GenericRecord record = new GenericData.Record(schema);
                for (var field : schema.getFields()) {
                    String originalName = field.doc() != null ? field.doc() : field.name();
                    Object value = row.get(originalName);
                    record.put(field.name(), value == null ? null : String.valueOf(value));
                }
                writer.write(record);
            }
        }
    }

    private Schema buildAvroSchema(Table table) {
        List<Schema.Field> fields = new ArrayList<>();
        Map<String, String> usedNames = new LinkedHashMap<>();
        for (String columnName : columnNames(table)) {
            String avroName = avroSafeName(columnName, usedNames);
            fields.add(new Schema.Field(avroName, Schema.createUnion(List.of(
                    Schema.create(Schema.Type.NULL),
                    Schema.create(Schema.Type.STRING))), columnName, Schema.NULL_VALUE));
        }
        Schema schema = Schema.createRecord(avroSafeName(table.getName(), new LinkedHashMap<>()), null,
                "com.access.unpack.parquet", false);
        schema.setFields(fields);
        return schema;
    }

    private Map<String, Object> indexInfo(Index index) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", index.getName());
        info.put("primaryKey", index.isPrimaryKey());
        info.put("unique", index.isUnique());
        info.put("columns", index.getColumns().stream().map(c -> c.getName()).toList());
        return info;
    }

    private List<String> columnNames(Table table) {
        return table.getColumns().stream().map(column -> column.getName()).toList();
    }

    private Map<String, Object> readPropertyMap(PropertyMap propertyMap) {
        Map<String, Object> properties = new TreeMap<>();
        if (propertyMap == null) {
            return properties;
        }
        for (PropertyMap.Property property : propertyMap) {
            properties.put(property.getName(), normalizeValue(property.getValue()));
        }
        return properties;
    }

    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof Instant instant) {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC).format(instant);
        }
        if (value instanceof java.util.Date date) {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC).format(date.toInstant());
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::normalizeValue).toList();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((k, v) -> normalized.put(String.valueOf(k), normalizeValue(v)));
            return normalized;
        }
        return value;
    }

    private String classifyQuery(String sql) {
        String lower = sql == null ? "" : sql.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("select")) return "select";
        if (lower.startsWith("insert")) return "append";
        if (lower.startsWith("update")) return "update";
        if (lower.startsWith("delete")) return "delete";
        if (lower.startsWith("transform")) return "crosstab";
        if (lower.startsWith("create table")) return "make-table";
        if (lower.startsWith("parameter")) return "parameter";
        if (lower.contains(" union ")) return "union";
        if (lower.startsWith("pass-through")) return "pass-through";
        if (lower.startsWith("create ") || lower.startsWith("alter ") || lower.startsWith("drop ")) return "ddl";
        return "unknown";
    }

    private List<String> accessSqlWarnings(String sql) {
        List<String> warnings = new ArrayList<>();
        String lower = sql == null ? "" : sql.toLowerCase(Locale.ROOT);
        if (lower.contains("iif(") || lower.contains("nz(") || lower.contains("switch(")) {
            warnings.add("Access-specific conditional function detected");
        }
        if (lower.contains("dlookup(") || lower.contains("dsum(") || lower.contains("dcount(")) {
            warnings.add("Domain aggregate function detected");
        }
        if (sql != null && sql.contains("#")) {
            warnings.add("Access date literal syntax detected");
        }
        if (lower.contains(" like ") && sql != null && sql.contains("*")) {
            warnings.add("Access wildcard syntax detected");
        }
        return warnings;
    }

    private Set<String> parseDependencies(String sql) {
        Set<String> dependencies = new LinkedHashSet<>();
        Matcher matcher = SQL_DEP_PATTERN.matcher(Objects.requireNonNullElse(sql, ""));
        while (matcher.find()) {
            dependencies.add(matcher.group(2).trim());
        }
        return dependencies;
    }

    private String redactSecrets(Object candidate) {
        if (candidate == null) {
            return null;
        }
        return String.valueOf(candidate)
                .replaceAll("(?i)(password|pwd)=([^;]+)", "$1=<redacted>")
                .replaceAll("(?i)(user id|uid)=([^;]+)", "$1=<redacted>");
    }

    private Diagnostic diag(String code, String severity, String message, String objectType, String objectName,
                            Map<String, Object> details) {
        return new Diagnostic(code, severity, message, objectType, objectName, details, Instant.now());
    }

    private Map<String, Object> summarizeCoverage(List<CoverageEntry> coverage) {
        Map<String, Long> counts = coverage.stream()
                .collect(Collectors.groupingBy(entry -> entry.status().name().toLowerCase(Locale.ROOT), LinkedHashMap::new, Collectors.counting()));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", coverage.size());
        summary.putAll(counts);
        return summary;
    }

    private Map<String, Object> phase(String name, String status, String scope) {
        return Map.of("name", name, "status", status, "scope", scope);
    }

    private String extension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx >= 0 ? fileName.substring(idx + 1).toLowerCase(Locale.ROOT) : "unknown";
    }

    private boolean shouldExtractTable(String tableName, Set<String> selectedTables) {
        return selectedTables == null || selectedTables.isEmpty() || selectedTables.contains(tableName);
    }

    private boolean shouldExtractQuery(String queryName, Set<String> selectedQueries) {
        return selectedQueries == null || selectedQueries.isEmpty() || selectedQueries.contains(queryName);
    }

    private int safeRowCount(Table table) {
        try {
            return table.getRowCount();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private Path guessSiblingAlternative(Path input, String classification) {
        String fileName = input.getFileName().toString();
        String candidateName = switch (classification) {
            case "likely-front-end" -> fileName.replace("SYS", "TBL").replace("sys", "tbl");
            case "likely-data" -> fileName.replace("TBL", "SYS").replace("tbl", "sys");
            default -> null;
        };
        if (candidateName == null || candidateName.equals(fileName)) {
            return null;
        }
        Path candidate = input.resolveSibling(candidateName);
        return Files.exists(candidate) ? candidate : null;
    }

    private String avroSafeName(String input, Map<String, String> usedNames) {
        String candidate = Objects.requireNonNullElse(input, "field").replaceAll("[^A-Za-z0-9_]", "_");
        if (candidate.isBlank()) {
            candidate = "field";
        }
        if (!Character.isLetter(candidate.charAt(0)) && candidate.charAt(0) != '_') {
            candidate = "_" + candidate;
        }
        String unique = candidate;
        int suffix = 2;
        while (usedNames.containsKey(unique)) {
            unique = candidate + "_" + suffix++;
        }
        usedNames.put(unique, input);
        return unique;
    }

    private static final class PathOutputFile implements OutputFile {
        private final Path path;

        private PathOutputFile(Path path) {
            this.path = path;
        }

        @Override
        public PositionOutputStream create(long blockSizeHint) throws IOException {
            Files.deleteIfExists(path);
            Files.createDirectories(path.getParent());
            OutputStream out = Files.newOutputStream(path);
            return new PositionStream(out);
        }

        @Override
        public PositionOutputStream createOrOverwrite(long blockSizeHint) throws IOException {
            return create(blockSizeHint);
        }

        @Override
        public boolean supportsBlockSize() {
            return false;
        }

        @Override
        public long defaultBlockSize() {
            return 0;
        }
    }

    private static final class PositionStream extends PositionOutputStream {
        private final OutputStream delegate;
        private long position;

        private PositionStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public long getPos() {
            return position;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            position++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            position += len;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}

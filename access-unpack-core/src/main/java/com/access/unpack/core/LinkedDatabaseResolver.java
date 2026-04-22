package com.access.unpack.core;

import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.util.LinkResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class LinkedDatabaseResolver implements LinkResolver {
    private final Path inputPath;
    private final List<Diagnostic> warnings;

    LinkedDatabaseResolver(Path inputPath, List<Diagnostic> warnings) {
        this.inputPath = inputPath.toAbsolutePath().normalize();
        this.warnings = warnings;
    }

    @Override
    public Database resolveLinkedDatabase(Database linkerDb, String linkeeFileName) throws IOException {
        Path linkedPath = Path.of(linkeeFileName);
        if (Files.exists(linkedPath)) {
            return DatabaseBuilder.open(linkedPath);
        }

        Path fileNameOnly = linkedPath.getFileName();
        List<Path> candidates = new ArrayList<>();
        if (fileNameOnly != null) {
            candidates.add(inputPath.getParent().resolve(fileNameOnly).normalize());
            candidates.add(inputPath.resolveSibling(fileNameOnly).normalize());
        }

        for (Path candidate : candidates) {
            if (candidate != null && Files.exists(candidate)) {
                warnings.add(new Diagnostic(
                        "LINKED_DB_REMAP",
                        "warning",
                        "Linked database path was remapped to a local sibling file",
                        "linked-table",
                        fileNameOnly != null ? fileNameOnly.toString() : linkeeFileName,
                        java.util.Map.of(
                                "originalPath", linkeeFileName,
                                "resolvedPath", candidate.toString()),
                        java.time.Instant.now()));
                return DatabaseBuilder.open(candidate);
            }
        }

        throw new IOException("Linked database could not be resolved: " + linkeeFileName);
    }
}

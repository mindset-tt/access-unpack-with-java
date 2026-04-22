package com.access.unpack.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class PlaceholderWriter {
    public void writeCoverageBoundPlaceholders(Path outputRoot, boolean windowsHelperAvailable) throws IOException {
        String reason = windowsHelperAvailable
                ? "This artifact was not recovered by file parsing alone; the optional helper can add more raw object detail"
                : "This artifact was not recoverable from file parsing alone in cross-platform mode; the optional Windows helper may recover more if available";

        create(outputRoot, "forms/model/_placeholder.json", "forms", reason, "forms/raw");
        create(outputRoot, "forms/control-tree/_placeholder.json", "forms-control-tree", reason, "forms/raw");
        create(outputRoot, "forms/event-map/_placeholder.json", "forms-event-map", reason, "forms/raw");
        create(outputRoot, "reports/model/_placeholder.json", "reports", reason, "reports/raw");
        create(outputRoot, "reports/previews/_placeholder.json", "report-previews", reason, "reports/raw");
        create(outputRoot, "reports/jrxml/_placeholder.json", "jrxml", reason, "reports/model");
        create(outputRoot, "reports/crystal/_placeholder.json", "crystal", reason, "reports/model");
        create(outputRoot, "reports/conversion-map/_placeholder.json", "report-conversion", reason, "reports/model");
        create(outputRoot, "macros/model/_placeholder.json", "macros", reason, "macros/raw");
        create(outputRoot, "startup/autoexec.json", "startup-autoexec", reason, "macros/raw");
        create(outputRoot, "startup/navigation.json", "startup-navigation", reason, "forms/raw");
        create(outputRoot, "vba/call-graph.json", "vba-call-graph", reason, "vba/raw");
        create(outputRoot, "vba/references.json", "vba-references", reason, "vba/raw");
    }

    private void create(Path outputRoot, String relativePath, String category, String reason, String rawSourceHint)
            throws IOException {
        Path path = outputRoot.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Jsons.MAPPER.writeValue(path.toFile(), Map.of(
                "status", "placeholder",
                "category", category,
                "reason", reason,
                "rawSourceHint", rawSourceHint));
    }
}

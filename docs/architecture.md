# System architecture

`access-unpack` uses a two-engine architecture:

1. Cross-platform core engine in Java 21
   - Detects file type and environment capabilities
   - Extracts database metadata, tables, indexes, relationships, and saved queries using Jackcess
   - Exports table data to CSV, JSON, and Parquet
   - Produces deterministic manifests, coverage, diagnostics, and migration docs
   - Emits placeholder artifacts when full-fidelity Access automation is not available

2. Optional Windows full-fidelity helper
   - PowerShell automation around Microsoft Access COM
   - Uses `CurrentProject` enumeration and `Application.SaveAsText`
   - Intended for forms, reports, macros, modules, startup settings, and previews

Shared artifact strategy:

- Preserve raw artifacts exactly when available
- Never silently drop unsupported objects
- Record partial or missing extraction in `coverage.json` and `warnings.json`
- Keep outputs useful for downstream rebuilds as web, API, desktop, or reporting systems

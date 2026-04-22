# access-unpack

`access-unpack` is a preservation-first CLI, library, and desktop UI for unpacking Microsoft Access databases into reusable modernization artifacts.

Current implementation status:

- Cross-platform Java 21 core: implemented for file detection, schema extraction, data export, relationships, indexes, query inventory, manifests, deterministic packaging, warnings, and coverage accounting.
- Windows Access helper: implemented as a PowerShell automation contract using the real Access application when available.
- Desktop UI: implemented as a cross-platform Swing launcher around the same extraction pipeline.
- Forms, reports, macros, and VBA normalization: scaffolded with explicit placeholders when the Windows helper or source text is unavailable.

This repository prefers honest partial output over invented reconstruction.

## Build

```bash
./scripts/build.sh
```

## CLI example

```bash
./scripts/run-cli.sh \
  --input akatahara/水利地益TBL.mdb \
  --output generated-runs/tbl-mdb \
  --target-report-formats jrxml,crystal \
  --full-fidelity auto \
  --include-system-objects false \
  --export-data all \
  --translate-sql postgres \
  --validate true
```

## UI

Run:

```bash
./scripts/run-ui.sh
```

The plain `target/*.jar` is not bundled with dependencies yet, so the launcher script is the reliable way to open the UI from this repo.

## Git-friendly structure

- Keep Java source under `access-unpack-core/`, `access-unpack-cli/`, and `access-unpack-ui/`.
- Keep generated extraction output under `generated-runs/` or another dedicated artifacts folder.
- `.gitignore` excludes the default generated output paths so the repo stays source-focused.

# Implementation plan

## Phase 1
- Detect file type and environment
- Extract tables, schema, indexes, relationships, queries
- Export data and write manifests, coverage, warnings, and docs

## Phase 2
- Execute Windows helper when Access automation is available
- Export raw forms, reports, macros, modules, startup metadata, and previews

## Phase 3
- Normalize helper output to JSON IR
- Build dependency graphs across queries, forms, reports, macros, and VBA

## Phase 4
- Generate JRXML from normalized report IR
- Enable Crystal generation only when validated SDK/runtime support is present

## Phase 5
- Resume/retry behavior
- Regression fixtures
- More redaction and secure logging
- Round-trip and compile validation where prerequisites exist

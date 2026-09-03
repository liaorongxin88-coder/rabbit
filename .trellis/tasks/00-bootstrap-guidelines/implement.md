# Implementation Plan

1. Inventory repository-wide and module-local convention sources.
2. Analyze backend structure, data access, error handling, logging, and tests; persist findings under `research/`.
3. Analyze React admin structure, UI composition, data access, state, types, design rules, and checks; persist findings under `research/`.
4. Analyze Flutter architecture, routing, repositories, state, persistence, UI, and tests; persist findings under `research/`.
5. Rewrite `.trellis/spec/backend/` from backend evidence.
6. Replace the generated `.trellis/spec/frontend/` layer with `.trellis/spec/admin/` and source-backed React guidance.
7. Add `.trellis/spec/flutter/` with source-backed Dart and Flutter guidance.
8. Review shared guides and change them only where Rabbit has a concrete cross-application rule.
9. Update layer indexes and the task checklist.
10. Validate links, placeholders, examples, indexes, and discovered Trellis layers.
11. Run a final Trellis quality review and record whether any further spec update is needed.

## Validation Commands

```bash
python3 ./.trellis/scripts/get_context.py --mode packages
rg -n "To fill|TODO|TBD|your project|placeholder" .trellis/spec
python3 -m pytest .trellis/tests -q
```

The Trellis test command is conditional on `.trellis/tests/` existing. Product tests are not required because this task changes documentation only.

## Rollback Point

Before restructuring the generated frontend layer, confirm that all React guidance has a destination under `.trellis/spec/admin/`. If validation reveals broken references or a consumer that requires the old layer name, restore the directory name and keep Flutter separate.

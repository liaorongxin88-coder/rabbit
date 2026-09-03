# Technical Design

## Boundary Model

The repository has three maintained applications with distinct languages and ownership rules:

- `backend/`: Java 21 Spring Boot modular monolith with MyBatis and Flyway.
- `admin/`: React 19 and TypeScript administrative web client.
- `app/`: Flutter and Dart Android client.

The generated `backend/frontend` split is too broad because React and Flutter do not share component, state, type, or test conventions. The final spec tree will use `backend`, `admin`, and `flutter` as independent layers, plus shared guides.

## Evidence Sources

Each layer will draw from sources in this order:

1. Repository and module rule files.
2. Architecture and operational documentation.
3. Tests that enforce structural or behavioral contracts.
4. Representative production code and repeated local patterns.

Conflicts are resolved in favor of enforced tests and current production structure. The specs will call out exceptions when the repository intentionally differs from a general pattern.

## Spec Shape

Each layer has an `index.md` with:

- a short architecture summary;
- a Pre-Development Checklist that routes readers to topic files;
- a guideline index;
- a Quality Check with project commands and structural checks.

Topic files state concrete rules, cite paths, and list prohibited patterns only when the repository or its tests support them.

## Compatibility

This work changes documentation under `.trellis/` only. Product builds and runtime behavior remain unchanged. Renaming the generated React-oriented `frontend` layer to `admin` prevents those rules from being injected into Flutter work.

## Verification

- Search the final spec tree for template markers and placeholder language.
- Verify every Markdown link points to an existing file.
- Check that each layer index lists the files present in that layer.
- Run `get_context.py --mode packages` and confirm the expected layer names.
- Review representative claims against their cited source files.

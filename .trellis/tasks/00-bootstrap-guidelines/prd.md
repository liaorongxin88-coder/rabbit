# Bootstrap Rabbit Project Guidelines

## Goal

Replace the generated Trellis spec scaffolding with source-backed development guidelines for every maintained Rabbit application.

## Scope

- Document backend conventions under `.trellis/spec/backend/`.
- Document React admin conventions under `.trellis/spec/admin/`.
- Document Flutter client conventions under `.trellis/spec/flutter/`.
- Keep cross-application reasoning guidance under `.trellis/spec/guides/`.
- Use `AGENTS.md`, module-local rules, architecture docs, production code, and tests as evidence.

## Constraints

- Describe current repository practice rather than an aspirational architecture.
- Cite real repository paths and representative examples.
- Record meaningful anti-patterns and enforced boundaries.
- Keep each spec index synchronized with the files in its layer.
- Do not modify product source code, build configuration, or runtime behavior.
- Write the generated project guidelines in English.

## Out of Scope

- Refactoring backend, admin, or Flutter code.
- Introducing new lint rules, dependencies, or build steps.
- Resolving existing technical debt.
- Changing the general Trellis workflow or task runtime.

## Acceptance Criteria

- [x] Backend guidelines cover module ownership, HTTP/service/data layering, database migrations, errors, logging, and tests.
- [x] Admin guidelines cover directory ownership, components, hooks and data access, state, types, design rules, and verification.
- [x] Flutter guidelines cover layered architecture, routing, repositories and networking, Riverpod state, UI conventions, persistence, and tests.
- [x] Every important rule is supported by a repository document, source file, test, or repeated code pattern.
- [x] Layer indexes contain useful Pre-Development Checklist and Quality Check sections.
- [x] No template placeholders, empty headings, or generic boilerplate remain in project spec files.
- [x] Trellis reports the final backend, admin, and flutter spec layers.

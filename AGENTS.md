# Repository Guidelines

## Project Structure & Module Organization

This repository contains three maintained applications:

- `backend/`: Spring Boot 3.5, Java 21, MyBatis, and Flyway. Production code is under `src/main/java/com/rabbit/app`; mapper XML and migrations are under `src/main/resources/`.
- `admin/`: React 19, TypeScript, Vite, Tailwind, and Radix. Put routes in `src/pages`, shared UI in `src/components`, API calls in `src/api`, and domain types in `src/types`.
- `app/`: Flutter Android client. Code follows `lib/src/{config,data,domain,routing,ui}`; unit/widget tests live in `test/`, and device flows in `integration_test/`.

Architecture and operational documentation lives in `docs/`. Read the nearest `.rule` or `.rules` file before modifying a module; for visible admin changes, also read `admin/DESIGN.md`.

## Build, Test, and Development Commands

- `docker compose up -d --build`: start MySQL and the backend after configuring `.env` from `.env.example`.
- `mvn --file backend/pom.xml test`: run backend unit and architecture tests.
- `mvn --file backend/pom.xml -Pe2e verify`: run backend `*IT.java` integration tests.
- `pnpm --dir admin dev`: run the admin UI with its backend proxy.
- `pnpm --dir admin lint && pnpm --dir admin build`: lint, type-check, and build admin.
- `cd app && ./rabbit check`: bootstrap-aware Flutter analysis and tests; use `./rabbit apk dev --debug` for an Android build.

## Coding Style & Naming Conventions

Use four-space indentation in Java and two spaces in TypeScript/Dart. Java classes use `PascalCase`; methods use `camelCase`. TypeScript follows the existing single-quote, semicolon-free style and uses kebab-case filenames such as `merchant-detail-page.tsx`. Dart files use `snake_case.dart`; format them with `dart format`. Run Oxlint for admin and `flutter analyze` through `./rabbit` for Flutter. Do not commit generated `target/`, `dist/`, `build/`, or dependency directories.

## Testing Guidelines

Name backend unit tests `*Test.java` and E2E tests `*IT.java`. Name Flutter tests `*_test.dart`. Add regression coverage for business rules, providers, repositories, API contracts, and permission changes. Admin currently relies on lint/build plus browser verification; include desktop and narrow-screen checks for UI changes.

## Commit & Pull Request Guidelines

Use Conventional Commits, preferably with a module scope: `feat(backend): add audit export` or `fix(flutter): retain cage context`. Keep commits focused. PRs should explain behavior and risk, link the issue, list validation commands, and include screenshots for UI work. Call out schema/config changes explicitly.

## Security & Configuration

Never commit `.env`, tokens, passwords, or production connection strings. Database changes require a new Flyway migration. Preserve JWT separation, `X-House-Id` tenant checks, permission enforcement, and `requestId` idempotency for writes.

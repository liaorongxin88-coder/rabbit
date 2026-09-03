# Flutter conventions research

This report records the Flutter Android client's current conventions. It uses the repository rules, current source, and tests as evidence. Where they disagree, executable architecture tests and current production code describe what the repository does today.

## Evidence and authority

- `AGENTS.md` defines the repository-wide Flutter boundary, two-space Dart indentation, `snake_case.dart` names, `dart format`, and the `cd app && ./rabbit check` entry point.
- `app/.rule` is the canonical module rule. `app/AGENTS.md` and `app/CLAUDE.md` are compatibility links, so module guidance should cite `.rule` rather than duplicating it.
- `app/lib/src/README.md` and `app/test/README.md` explain source and test ownership. `app/test/architecture/layer_dependencies_test.dart` enforces a subset of those rules.
- `app/README.md`, `app/config/env/README.md`, and `docs/app/README.md` describe supported workflows and build usage.

One part of `app/.rule` still describes NFC and offline pending work as outside the first release. The current README and source include NFC, outbound drafts, and pending synchronization. Flutter specs should describe the implemented code rather than repeat that older scope statement. Evidence: `app/.rule`, `app/README.md`, `app/lib/src/app.dart`.

## Package and toolchain baseline

The package is private and Android-only. `pubspec.yaml` pins Flutter 3.24.3 and Dart `^3.5.3`. Runtime dependencies are intentionally small: Dio, Riverpod, go_router, secure storage, shared preferences, UUIDs, NFC, image picking, and internationalization. There is no Hive dependency, Riverpod code generation, Freezed, or a separate JSON serialization package.

`analysis_options.yaml` only includes `package:flutter_lints/flutter.yaml`; it adds no project-specific lint rules. Repository-specific architecture rules therefore live in tests and documentation, not analyzer plugins.

Representative paths:

- `app/pubspec.yaml`
- `app/analysis_options.yaml`
- `app/test/architecture/layer_dependencies_test.dart`

## Layering, ownership, and names

Production code is rooted at `lib/src/{config,data,domain,routing,ui}`:

- `config/` owns compile-time configuration and static application copy.
- `data/repositories/<business>/` owns backend operations and response-to-model conversion. Repositories are grouped by business terms such as `rabbits`, `houses`, and `reproduction`.
- `data/services/<capability>/` owns technical capabilities such as `network`, `auth`, `storage`, and `nfc`.
- `domain/<business>/` contains models and pure or mostly pure business rules shared by UI and data code.
- `routing/` owns the application route table, redirects, and shell structure.
- `ui/<feature>/` separates complete destinations in `screens/`, temporary flows in `sheets/`, reusable fragments in `widgets/`, and coordinated state in `view_models/`.

Lower layers use business vocabulary. `home` and `dashboard` are valid UI surfaces but are forbidden repository or domain namespaces. Shared code belongs to the nearest owner: transport helpers in `data/services/network/`, cross-feature widgets in `ui/core/widgets/`, and business-aware widgets in their feature. Extraction is expected only after at least two real consumers share a stable contract.

Parent directories are part of the namespace. Use names such as `data/repositories/rabbits/repository.dart`, `ui/rabbits/screens/detail.dart`, and `domain/cages/layout.dart`. Role suffixes such as `_screen.dart`, `_repository.dart`, `_provider.dart`, and `_model.dart` are prohibited. Collection destinations use `list.dart`; module landing pages use `overview.dart`; concrete entities retain useful names such as `rabbit.dart`.

The intended dependency direction is UI to repositories and domain, then repositories to services and domain. The architecture test mechanically prevents `config`, `data`, and `domain` from importing the UI package path. It does not enforce every documented edge.

Current exceptions:

- Several UI files import low-level NFC, storage, auth, or exception services. Examples include `ui/auth/screens/login.dart`, `ui/outbound/screens/flow.dart`, and `ui/cages/screens/detail.dart`. These are current implementation exceptions to the broad "pages do not call services" rule.
- Screens and sheets often call repositories directly for simple mutations. Controllers are used for session state and multi-step workflows, not as a mandatory command layer for every write.
- There is no general `use_cases/` layer. `OutboundController` is a large workflow coordinator, but it remains under UI view models.

Representative paths:

- `app/lib/src/README.md`
- `app/lib/src/data/repositories/rabbits/repository.dart`
- `app/lib/src/ui/outbound/view_models/controller.dart`

## Configuration and startup

`main()` initializes Flutter bindings, awaits `AppConfig.load()`, then creates the root `ProviderScope`. Application restore work begins from `RabbitManagerApp.initState()`: local settings, authentication, NFC state, pending NFC synchronization, and app update checks.

`AppConfig` uses compile-time `String.fromEnvironment` values. Base URL precedence is:

1. `RABBIT_API_BASE_URL` passed through `--dart-define` or `--dart-define-from-file`.
2. The bundled `config/env/dev.env` or `config/env/test.env` asset.
3. A code default: `http://10.0.2.2:8080` for non-production builds or `https://api.dzht.top` for `prod` and legacy `release`.

Only dev and test env files are packaged as assets. Production configuration must be supplied at build time; `prod.env` and signing material stay local. Shell environment variables alone do not populate `String.fromEnvironment`. A changed env file requires a full stop and rerun, not hot reload.

`RABBIT_CARRIER_AUTH_ENABLED` defaults to false. Carrier login also requires a secure HTTPS base URL and an available native adapter. SMS remains the fallback.

Keep these three axes separate:

- Script environment: `dev`, `test`, `prod`, with `release` as a compatibility alias.
- Android flavor: `dev`, `staging`, `prod`; script `test` maps to `staging`.
- Flutter mode: `debug`, `profile`, or `release`.

Representative paths:

- `app/lib/main.dart`
- `app/lib/src/config/app.dart`
- `app/config/env/README.md`

## Domain models and business rules

The domain layer is lightweight. Pure policies such as farm-time conversion and reproduction reminder calculation live beside their business models. Add a domain service or use case only when repository or view-model code can no longer carry the rule clearly.

There is no separate DTO layer. Repositories validate the response envelope, convert the outer value to a map or list, and call domain `fromJson` factories. Domain models also expose presentation-oriented labels such as `typeLabel`, `genderLabel`, and `weightLabel`.

Parsing strictness is mixed. `Rabbit.fromJson` accepts numeric strings, defaults invalid required IDs to `0`, defaults missing `isActive` to true, and maps legacy `JUVENILE` to `ADAPTATION`. Auth response parsing is stricter about tokens and user identity. Specs should preserve this current mixed behavior instead of claiming that every model is strict.

The "pure domain" boundary also has a concrete exception: `domain/settings/local.dart` imports Flutter's `ThemeMode` and returns Chinese UI labels. Treat it as existing practice, not the model for new business entities.

Representative paths:

- `app/lib/src/domain/reproduction/date_policy.dart`
- `app/lib/src/domain/rabbits/rabbit.dart`
- `app/lib/src/domain/settings/local.dart`

## Repositories, networking, and errors

Repositories are concrete classes exposed through plain Riverpod `Provider`s. They receive the shared `ApiClient`; UI code must not construct page-local Dio clients. Some workflow-heavy areas define gateway interfaces for substitution, including outbound, NFC, and feed, while most repositories are concrete and tests subclass or replace them directly.

`ApiClient` owns base URL, timeouts, headers, response envelopes, and Dio error translation:

- Connect timeout is 10 seconds; send and receive timeouts are 30 seconds.
- Every request reads the current session. It sends `Authorization: Bearer <token>` when authenticated and `X-House-Id` for house-scoped calls.
- A response must be a map. A nonzero `code` becomes `ApiException` even when HTTP succeeds.
- HTTP or business 401 invalidates the session. A 403 with the exact message `账号已停用` also invalidates it.
- Dio failures become Chinese timeout, connectivity, cancellation, certificate, or status messages.

Repository decoders use `requireJsonObject` and `requireJsonObjectList`, then domain factories. There are two current sharp edges:

- `_request` catches `DioException`, not arbitrary decoder failures. A repository decoder can leak `FormatException` or a type error.
- `requireJsonObjectList` uses `whereType<Map>()`, so malformed non-map list elements are discarded instead of failing the whole response.

Reads may expose a `CancelToken`; writes generally do not. Long lists explicitly paginate until a short page, as `RabbitRepository` does with page size 200. Tests verify request headers, query parameters, full pagination, decoding, and filtering with custom Dio adapters.

Writes that need idempotency create a UUID `requestId`. Multi-step outbound submission persists the request ID before sending and distinguishes a confirmed failure from an unknown result that may have reached the server.

Representative paths:

- `app/lib/src/data/services/network/client.dart`
- `app/lib/src/data/services/network/response.dart`
- `app/lib/src/data/repositories/rabbits/repository.dart`

## Riverpod state

Providers are handwritten. Technical services and repositories use `Provider`; read models usually use `FutureProvider` or `FutureProvider.family`; coordinated workflows use `StateNotifierProvider`.

Query conventions include:

- Watch `authenticatedUserIdProvider` so session changes invalidate user-scoped data.
- Return an empty result or a clear argument/state error for missing user, house, or entity identifiers.
- Use immutable family request objects with value equality and `hashCode` when a query has several parameters.
- For disposable network reads, create a Dio `CancelToken` and cancel it from `ref.onDispose`.
- Let widgets render loading, data, empty, error, and retry branches from `AsyncValue`.

`houseRabbitsProvider` and rabbit detail providers use `FutureProvider.autoDispose.family` with cancellation. Houses, permissions, and members deliberately use non-auto-dispose providers and remain cached until an auth dependency changes or code invalidates them.

Controllers retain stack traces in `AsyncValue.error`. Auth restore validates the persisted session against the backend. Reauthentication restores the previous authenticated state after emitting an error, then rethrows so the calling UI can show the failure.

Mutation cache coherence is manual. Screens, sheets, and controllers call `ref.invalidate` for each affected provider. Outbound completion invalidates rabbits, cages, batches, home events, and reports. This distributed invalidation is current practice and a regression risk; specs should tell implementers to inspect all affected read models after writes.

Representative paths:

- `app/lib/src/ui/rabbits/view_models/providers.dart`
- `app/lib/src/ui/houses/view_models/providers.dart`
- `app/lib/src/ui/auth/view_models/controller.dart`

## Persistence and source of truth

The app uses `flutter_secure_storage` and `shared_preferences`; it has no local database or general entity cache.

- Session tokens live in secure storage. User ID, user name, current house, local theme, and start route live in shared preferences.
- House selection is stored per user, with a legacy global house key retained as fallback.
- `SessionStore` coalesces concurrent first reads and memoizes a snapshot. After that first read, its in-memory snapshot is authoritative until save or clear replaces it.
- Local settings update Riverpod state before awaiting the preference write. A storage failure can therefore leave optimistic in-memory state visible until the next restore.
- Houses, rabbits, batches, reports, and similar records are network-sourced. Riverpod provides only process-local caching.

Two workflows persist more than settings:

- Outbound stores a user-and-house-scoped draft and pending request ID. Writes are serialized by scope. Corrupt snapshots are deleted. Startup checks pending server status before choosing a server task, a newer matching local revision, or an offline local fallback.
- NFC stores write sessions, pending bindings, and pending launch events. Failed bindings remain queued; business code `4606` becomes a conflict that can be retried or force-replaced with a new UUID. The app retries every 30 seconds and when it resumes.

Do not store secrets in shared preferences. Do not add an offline cache for ordinary network data unless the task defines conflict, freshness, and ownership behavior.

Representative paths:

- `app/lib/src/data/services/auth/session.dart`
- `app/lib/src/data/services/storage/outbound.dart`
- `app/lib/src/data/services/storage/nfc.dart`

## Routing and navigation

`MaterialApp.router` consumes one `GoRouter` from `appRouterProvider`. The route provider owns the root navigator key, shell navigator, full route table, and redirects.

Redirect order matters. The router first waits for local settings, then authentication restore. It preserves a `from` URL while auth is loading, accepts only internal relative protected locations, sends unauthenticated users to `/login`, and prevents authenticated users from remaining there.

The `ShellRoute` owns the four main destinations: home, houses, dashboard, and profile. Bottom navigation uses `context.go` because switching branches replaces the current location. Detail flows generally use `context.push`. `AppPage` supplies a labeled 48dp back target and explicit parent or fallback routes so Android back remains useful when a child URL was opened directly.

Legal documents are the intentional current exception to route centralization. Login and settings open `LegalDocumentScreen` with `Navigator.push(MaterialPageRoute)`; those screens are not deep-linkable GoRouter destinations.

Representative paths:

- `app/lib/src/routing/routes.dart`
- `app/lib/src/ui/core/widgets/shell.dart`
- `app/lib/src/ui/core/widgets/page.dart`

## UI, theme, and responsive behavior

The UI is a restrained farm-management tool, not a marketing surface. It uses Material 3, a fixed `zh_CN` locale, centered app-bar titles, Material icons, border-based cards without elevation, 8px default radii, 48dp primary controls, and a 74dp four-item navigation bar.

`AppColors` contains the documented light constants. `AppPalette`, a `ThemeExtension`, supplies semantic light and dark values for background, surface, text, primary, success, warning, and danger. Business widgets should read the theme or `AppPalette` instead of adding one-off hex colors. `AppSpacing` centralizes page-level padding; smaller component spacing remains local.

The root app clamps effective text scaling to 200 percent. Long titles and contextual labels use bounded lines and ellipsis. `AppPage` allows two title lines and computes a larger toolbar at elevated text scale.

There is no central breakpoint type. Widgets use local `LayoutBuilder`, `MediaQuery`, `Wrap`, scroll views, and thresholds suited to their content. Tests treat 360x800, 393x852, and 412x915 as the core Android sizes, with 200 percent text scaling. Forms and sheets must use current `MediaQuery.viewInsets.bottom`, remain scrollable, dismiss the keyboard on drag, and keep fields and submit actions reachable with 180, 300, and 420 logical-pixel keyboard insets.

The cage map has stricter spatial rules:

- Display one layer at a time, starting at layer 1, with layers ordered upward.
- Keep each row on one horizontal line and scroll it instead of wrapping.
- Preserve empty coordinate slots and dim filtered cells rather than removing them.
- Grow cells with text scale; do not use `FittedBox` to erase the user's font setting.
- Keep primary blue for selection, not cage status. Every status also needs an icon, text, legend entry, and semantics label.

`FittedBox(scaleDown)` still appears in a few fixed-format controls, including login verification controls, dashboard numbers, and NFC labels. Some existing components also use radii above 8px. Treat these as local exceptions, not global patterns.

Representative paths:

- `app/lib/src/ui/core/theme.dart`
- `app/lib/src/ui/core/widgets/page.dart`
- `app/lib/src/ui/cages/widgets/map.dart`

## Tests and enforcement

Tests mirror production ownership. Unit and widget tests live under `test/{app,config,data,domain,routing,ui,architecture}`. UI tests repeat the `screens`, `sheets`, `widgets`, and `view_models` interface directories. Device tests live under `integration_test/<business>/`. Cross-layer tests belong to the outermost behavior under test.

Typical test styles are:

- Pure tests for date policy, model parsing, labels, and business projections.
- Repository contract tests with custom Dio `HttpClientAdapter`s that assert URLs, headers, pagination, request bodies, and response decoding.
- Provider tests with `ProviderContainer` and `overrideWithValue` or `overrideWith`.
- Widget tests that replace repositories/providers and assert behavior, navigation, permissions, loading/error/empty states, touch dimensions, overflow absence, text scaling, and keyboard reachability. The suite uses behavioral assertions rather than golden-image files.

The architecture suite enforces nine rules:

1. Unit and widget tests follow source ownership.
2. Integration tests are grouped by business workflow.
3. Test support does not use generic shared/common/utils directories.
4. Config, data, and domain do not import UI package paths.
5. Repository and domain roots contain business directories, not loose Dart files.
6. Lower layers do not use generic `models`, `home`, or `dashboard` namespaces.
7. Shared production code keeps an explicit owner.
8. Feature UI uses the four recognized interface directories.
9. Filenames do not repeat directory roles.

Enforcement has limits. The lower-layer check searches the exact package import prefix. The shared-production test checks selected `shared` directories but does not mechanically cover every `common` or `utils` path named in `.rule`. It does not require routes to be centralized, prohibit UI service imports, enforce secure token storage, validate colors, or require idempotency. Those remain review rules.

There are nine Android integration test files covering auth, batches, cages, houses, NFC, outbound, and settings. Their scripts prepare MySQL fixtures, check host and device reachability, invoke `flutter drive`, save logs and screenshots, and often assert database state. These tests require a backend, Docker/MySQL, Android tooling, and a device or emulator. They are not part of `./rabbit test`.

The Android source also has a native JUnit test for OTA install permission behavior. The documented `test/routing/` owner currently has no test files, so route behavior is tested indirectly through app and widget tests.

Representative paths:

- `app/test/architecture/layer_dependencies_test.dart`
- `app/test/data/repositories/rabbits/pagination_test.dart`
- `app/integration_test/outbound/batch_android_test.dart`

## Android flavors and the wrapper

`app/rabbit` is a five-line executable shim that resolves the app directory and delegates to `scripts/rabbit.sh`. The dispatcher is the normal interface for bootstrap, diagnostics, dependency fetching, analysis, tests, builds, Gradle, releases, and selected E2E flows. Make targets are optional aliases.

`./rabbit test` defaults to the script `test` environment and passes Dart defines without an Android flavor. `./rabbit check` runs analyze plus tests. `./rabbit verify` adds a dev debug APK. Android commands resolve Flutter, JDK 21, and the Android SDK dynamically. Machine-specific paths belong in the ignored `config/env/toolchain.local.env`, not tracked Gradle properties.

Android has one `environment` flavor dimension:

| Flavor | Application ID | Label | Cleartext |
| --- | --- | --- | --- |
| `dev` | `com.rabbit.app.flutter.dev` | `鸿兔智管 Dev` | allowed |
| `staging` | `com.rabbit.app.flutter.test` | `鸿兔智管 Test` | allowed |
| `prod` | `com.rabbit.app.flutter` | `鸿兔智管` | disabled |

`pubspec.yaml` sets `dev` as the default flavor. The build uses compile SDK 35, NDK 25.1.8937393, AGP 8.2.2, Kotlin 1.9.22, Gradle 8.4, and Java 8 bytecode targets. Gradle itself must run on JDK 21.

Release builds enable R8 and resource shrinking. Four `RABBIT_ANDROID_*` environment variables supply signing. Without all four, a release build falls back to debug signing and is only a local validation artifact.

The main manifest declares internet, NFC, optional NFC hardware, unknown-source package installation, the OTA `FileProvider`, `adjustResize`, and the NFC NDEF filter. Flavor labels and cleartext behavior use manifest placeholders.

The wrapper exposes outbound and batch-lifecycle E2E scenarios. Other maintained device flows, such as cage operations, farm setup, identity, additional batch modifications, and NFC, use standalone scripts under `app/scripts/`.

Representative paths:

- `app/rabbit`
- `app/scripts/rabbit.sh`
- `app/android/app/build.gradle`

## Recommended Flutter spec files and checks

### Spec files

- `index.md`: architecture summary, pre-development checklist, topic index, and quality checks.
- `architecture.md`: layer ownership, business grouping, naming, dependency direction, and enforced anti-patterns.
- `configuration.md`: startup, env precedence, package baseline, Android-only boundary, and secret handling.
- `data-access.md`: repositories, ApiClient contracts, decoding, errors, headers, pagination, and idempotency.
- `riverpod.md`: provider types, family keys, cancellation, controllers, AsyncValue states, and invalidation.
- `persistence.md`: secure session data, preferences, network source of truth, outbound drafts, and NFC queues.
- `routing.md`: GoRouter table, redirect order, shell navigation, deep links, and back behavior.
- `ui.md`: theme tokens, Chinese product language, responsive rules, accessibility, sheets, and cage-map constraints.
- `testing.md`: ownership, architecture checks, provider/repository/widget patterns, Android E2E prerequisites, and evidence boundaries.
- `android.md`: environment/flavor/mode mapping, wrapper commands, toolchain versions, signing, manifests, and release checks.

### Validation checks

- For documentation-only edits, verify links, index entries, and placeholders; Flutter builds are not required.
- Run `cd app && ./rabbit analyze` for Flutter source or state changes.
- Run `cd app && ./rabbit test` for models, repositories, providers, business rules, widgets, and architecture ownership.
- Run `cd app && ./rabbit check` before merging ordinary Flutter work.
- Run `cd app && ./rabbit apk dev --debug` for dependency, manifest, Gradle, flavor, or Android configuration changes.
- Run `cd app && ./rabbit gradle testDevDebugUnitTest` when native Android behavior changes.
- Use the relevant `app/scripts/android_*_e2e.sh` flow for device workflows; preserve its logs, screenshots, and database assertions. Real NFC, TalkBack, landscape behavior, and carrier login still need targeted device verification.

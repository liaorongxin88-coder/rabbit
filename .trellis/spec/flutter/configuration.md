# Flutter configuration

`app/lib/main.dart` initializes Flutter bindings, awaits `AppConfig.load()`, then creates the root `ProviderScope`. `RabbitManagerApp.initState()` starts restoration for local settings, authentication, NFC state, pending NFC synchronization, and update checks.

## Environment precedence

`app/lib/src/config/app.dart` reads compile-time `String.fromEnvironment` values. API base URL precedence is:

1. `RABBIT_API_BASE_URL` from `--dart-define` or `--dart-define-from-file`.
2. Bundled `app/config/env/dev.env` or `test.env`.
3. The code default: Android emulator localhost for nonproduction, or `https://api.dzht.top` for `prod` and the `release` alias.

Only dev and test env files are assets. Supply production configuration at build time; keep `prod.env`, signing files, and machine-specific toolchain paths local. A shell variable alone does not populate `String.fromEnvironment`. Stop and rerun after changing an env file because hot reload does not rebuild compile-time values. `app/config/env/README.md` documents this behavior.

Keep script environment, Android flavor, and Flutter mode distinct:

- Script environments are `dev`, `test`, and `prod`; `release` is a compatibility alias.
- Android flavors are `dev`, `staging`, and `prod`; script `test` maps to `staging`.
- Flutter modes are `debug`, `profile`, and `release`.

`RABBIT_CARRIER_AUTH_ENABLED` defaults to false. Carrier login also requires HTTPS and an available native adapter; SMS remains the fallback.

## Package boundary

`app/pubspec.yaml` pins Flutter 3.24.3 and Dart `^3.5.3`. Use the existing Dio, Riverpod, go_router, secure storage, shared preferences, UUID, NFC, image picker, and localization packages before adding a dependency. There is no Hive, Riverpod generation, Freezed, or JSON serialization package.

`app/analysis_options.yaml` includes `flutter_lints` without custom analyzer rules. Repository-specific architecture constraints therefore belong to tests and documentation rather than assumed lint behavior.

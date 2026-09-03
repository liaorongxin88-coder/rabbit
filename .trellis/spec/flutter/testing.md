# Flutter testing

Tests follow production ownership under `app/test/` through the current `app`, `config`, `data`, `domain`, `ui`, and `architecture` directories. `app/test/README.md` also assigns route definitions and guards to `test/routing/`, but that directory has no test files today. UI tests repeat `screens`, `sheets`, `widgets`, and `view_models`. Device tests live under `app/integration_test/` grouped by business workflow. Put a cross-layer test with the outermost behavior it verifies.

## Test styles

- Pure tests cover date policy, parsing, labels, and business projections.
- Repository tests use custom Dio `HttpClientAdapter`s to assert URLs, headers, pagination, bodies, errors, and decoding. See `app/test/data/repositories/rabbits/pagination_test.dart`.
- Provider tests use `ProviderContainer` and overrides.
- Widget tests replace repositories or providers and cover navigation, permissions, loading/error/empty states, touch size, overflow, text scaling, and keyboard reachability.

The suite uses behavioral assertions, not golden-image files.

## Architecture enforcement

`app/test/architecture/layer_dependencies_test.dart` checks source/test ownership, business grouping, explicit shared owners, lower-layer imports, recognized UI directories, and filenames that do not repeat directory roles. It also keeps integration tests grouped by workflow and rejects generic test support directories.

The enforcement is partial. The import check searches the exact UI package prefix, and shared-owner checks cover selected paths. It does not mechanically centralize routes, ban every UI service import, check secure token storage, validate theme colors, or require idempotency. Review those rules against the cited source.

## Device tests

Android integration scripts prepare MySQL fixtures, verify host and device reachability, run `flutter drive`, save logs and screenshots, and often assert final database state. They require a backend, Docker/MySQL, Android tools, and a device or emulator. They are not part of `./rabbit test`.

Standalone `app/scripts/android_*_e2e.sh` launchers cover the current batches, cages, houses, NFC, outbound, and settings workflows. `app/integration_test/auth/online_role_acceptance_android_test.dart` has no dedicated launcher; treat that as an explicit verification gap rather than assuming a matching script exists. Real NFC, TalkBack, landscape, and carrier login need targeted device verification. Native OTA behavior also has an Android JUnit test.

## Commands

```bash
cd app
./rabbit analyze
./rabbit test
./rabbit check
```

Run `./rabbit check` before merging ordinary Flutter work. Add `./rabbit apk dev --debug` for Android configuration and `./rabbit gradle testDevDebugUnitTest` for native behavior.

# Flutter guidelines

`app/` is Rabbit's Android-only Flutter client. Production code is split into `config`, `data`, `domain`, `routing`, and feature-owned `ui` directories. Repositories use the shared Dio client, handwritten Riverpod providers own state, GoRouter owns application routes, and secure storage plus shared preferences hold only selected local state.

`app/.rule` is the module contract. `app/lib/src/README.md`, `app/test/README.md`, current source, and `app/test/architecture/layer_dependencies_test.dart` provide more specific evidence. The older first-release exclusions in `.rule` no longer describe NFC and offline outbound behavior; current code and `app/README.md` take precedence there.

## Pre-Development Checklist

- Read [Architecture](./architecture.md) before choosing a layer, business owner, or filename.
- Read [Configuration](./configuration.md) before changing startup, environment values, secrets, or package boundaries.
- Read [Data access](./data-access.md) before changing repositories, Dio, decoding, errors, pagination, or write retries.
- Read [Riverpod](./riverpod.md) before adding providers, queries, controllers, or invalidation.
- Read [Persistence](./persistence.md) before storing sessions, settings, drafts, or NFC work.
- Read [Routing](./routing.md) before changing redirects, shell navigation, deep links, or back behavior.
- Read [UI](./ui.md) for any widget, screen, sheet, responsive, or accessibility change.
- Read [Testing](./testing.md) before placing tests or choosing a device flow.
- Read [Android](./android.md) before changing flavors, manifests, Gradle, signing, or release builds.
- Read [Rabbit cross-application contracts](../guides/rabbit-cross-application-contracts.md) for shared wire and security behavior.

## File index

| File | Scope |
| --- | --- |
| [Architecture](./architecture.md) | Layers, business ownership, naming, dependencies, and exceptions |
| [Configuration](./configuration.md) | Startup, environment precedence, package baseline, and secrets |
| [Data access](./data-access.md) | Repositories, network envelopes, headers, decoding, and idempotency |
| [Riverpod](./riverpod.md) | Provider kinds, family keys, cancellation, controllers, and cache coherence |
| [Persistence](./persistence.md) | Secure sessions, preferences, network source of truth, outbound, and NFC |
| [Routing](./routing.md) | GoRouter redirects, shell branches, deep links, and back navigation |
| [UI](./ui.md) | Theme, layout, text scaling, sheets, semantics, and cage maps |
| [Testing](./testing.md) | Ownership, architecture rules, unit/widget patterns, and device tests |
| [Android](./android.md) | Wrapper commands, flavors, toolchain, manifests, signing, and release checks |

## Quality Check

For ordinary Flutter changes, run:

```bash
cd app
./rabbit check
```

Use `./rabbit apk dev --debug` for dependency, manifest, Gradle, flavor, or Android configuration changes. Use `./rabbit gradle testDevDebugUnitTest` for native Android behavior, and the matching `app/scripts/android_*_e2e.sh` workflow for device behavior. Device tests require their documented backend, MySQL, Android toolchain, and emulator or hardware setup.

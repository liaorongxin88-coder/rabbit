# Android

`app/rabbit` is the supported wrapper. It resolves the application directory and delegates to `app/scripts/rabbit.sh` for bootstrap, diagnostics, dependencies, analysis, tests, builds, Gradle, releases, and selected device workflows. Make targets are optional aliases.

## Flavor model

Android uses one `environment` dimension:

| Flavor | Application ID | Label | Cleartext |
| --- | --- | --- | --- |
| `dev` | `com.rabbit.app.flutter.dev` | `鸿兔智管 Dev` | allowed |
| `staging` | `com.rabbit.app.flutter.test` | `鸿兔智管 Test` | allowed |
| `prod` | `com.rabbit.app.flutter` | `鸿兔智管` | disabled |

`app/pubspec.yaml` sets `dev` as the default flavor. Script environment `test` maps to Android `staging`; Flutter build mode remains a separate debug, profile, or release choice.

## Toolchain and signing

`app/android/app/build.gradle` uses compile SDK 35, NDK 25.1.8937393, AGP 8.2.2, Kotlin 1.9.22, Gradle 8.4, and Java 8 bytecode targets. Gradle itself runs on JDK 21. The wrapper resolves Flutter, Java, and the Android SDK dynamically. Put machine paths in the ignored local toolchain environment file described by `app/config/env/README.md`, not tracked Gradle properties.

Release builds enable R8 and resource shrinking. Four `RABBIT_ANDROID_*` environment variables provide signing. If any are missing, a release build falls back to debug signing and is only a local validation artifact. Do not publish it as a production-signed build.

The main manifest declares internet, NFC, optional NFC hardware, unknown-source installation, the OTA `FileProvider`, `adjustResize`, and the NFC NDEF filter. Gradle supplies the flavor labels and cleartext values to the manifest. Preserve optional hardware declaration so non-NFC devices remain installable.

## Commands

```bash
cd app
./rabbit doctor
./rabbit check
./rabbit apk dev --debug
./rabbit gradle testDevDebugUnitTest
```

Use `./rabbit verify` when a change needs analysis, tests, and a dev debug APK together. The wrapper exposes outbound and batch-lifecycle device flows. Standalone `app/scripts/android_*_e2e.sh` launchers cover the other maintained batches, cages, houses, NFC, and settings scenarios. The auth integration test at `app/integration_test/auth/online_role_acceptance_android_test.dart` is the current exception: it has no dedicated wrapper or standalone launcher.

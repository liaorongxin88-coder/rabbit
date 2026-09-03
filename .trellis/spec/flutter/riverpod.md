# Riverpod

Providers are handwritten. Technical services and repositories use `Provider`; read models usually use `FutureProvider` or `FutureProvider.family`; coordinated workflows use `StateNotifierProvider`. `app/lib/src/ui/rabbits/view_models/providers.dart` and `ui/auth/view_models/controller.dart` show the main patterns.

## Query providers

- Watch `authenticatedUserIdProvider` for user-scoped reads so session changes invalidate them.
- Return an empty result or a clear state/argument error when a required user, house, or entity ID is absent.
- Use an immutable family request object with value equality and `hashCode` when a query has several inputs.
- For disposable network reads, create a Dio `CancelToken` and cancel it from `ref.onDispose`.
- Let widgets render loading, data, empty, error, and retry branches from `AsyncValue`.

Rabbit detail and house-rabbit queries use `FutureProvider.autoDispose.family` with cancellation. Houses, permissions, and members intentionally use non-auto-dispose providers and stay cached until an authentication dependency changes or code invalidates them. Choose lifecycle based on that existing ownership, not a blanket auto-dispose rule.

## Controllers and errors

Use a controller for state that coordinates a session or multi-step workflow. Preserve stack traces with `AsyncValue.error(error, stackTrace)`. Auth restoration validates a stored session against the backend. Reauthentication temporarily reports an error, restores the prior authenticated state, and rethrows so the calling UI can show the failure.

Simple screen or sheet mutations may call a repository directly. Do not add a controller that only forwards one method and owns no state transition.

## Cache coherence

Mutation coherence is manual. Screens, sheets, and controllers call `ref.invalidate` for every affected read provider. Outbound completion invalidates rabbit, cage, batch, home-event, and report data. There is no central dependency graph.

After a write, list all affected snapshots before coding and invalidate each owner. Missing one can leave a stale screen until the process or authentication state changes. Provider tests use `ProviderContainer` with `overrideWithValue` or `overrideWith` to verify dependency and state behavior.

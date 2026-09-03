# Flutter architecture

Production code is rooted under `app/lib/src/` in the `config`, `data`, `domain`, `routing`, and `ui` directories.

- `config/` owns compile-time configuration and static application copy.
- `data/repositories/<business>/` owns backend operations and response-to-domain conversion.
- `data/services/<capability>/` owns network, auth, storage, NFC, and other technical capabilities.
- `domain/<business>/` owns models and reusable business rules.
- `routing/` owns the route table, redirects, and shell structure.
- `ui/<feature>/` owns destinations in `screens/`, temporary flows in `sheets/`, reusable fragments in `widgets/`, and coordinated state in `view_models/`.

The intended direction is UI to repositories and domain, then repositories to services and domain. `app/test/architecture/layer_dependencies_test.dart` mechanically prevents config, data, and domain from importing the UI package path. It does not enforce every documented edge.

## Business ownership and names

Group lower layers by business vocabulary. `home` and `dashboard` are UI surfaces, not repository or domain namespaces. Put transport helpers in `data/services/network/`, cross-feature widgets in `ui/core/widgets/`, and business-aware widgets with their feature. Extract shared behavior after two consumers have the same stable contract.

Parent directories carry the role, so use paths such as:

```text
data/repositories/rabbits/repository.dart
ui/rabbits/screens/detail.dart
domain/cages/layout.dart
```

Do not repeat roles in names such as `_screen.dart`, `_repository.dart`, `_provider.dart`, or `_model.dart`. Collections use `list.dart`; module landing pages use `overview.dart`; concrete entities keep meaningful names. `layer_dependencies_test.dart` enforces root grouping, recognized UI directories, explicit shared owners, and filename rules.

## Current exceptions

Several UI files import low-level services, including `app/lib/src/ui/auth/screens/login.dart`, `ui/outbound/screens/flow.dart`, and `ui/cages/screens/detail.dart`. Screens and sheets also call repositories directly for simple writes. Controllers are reserved for session state and coordinated workflows, not required for every mutation. `app/lib/src/ui/outbound/view_models/controller.dart` is a large workflow coordinator and there is no general `use_cases/` layer.

Treat these as current exceptions. New code should keep technical capabilities behind repositories or focused controllers unless the feature has a concrete reason to follow an existing local exception.

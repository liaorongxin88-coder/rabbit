# Flutter client architecture

This client follows the Flutter architecture guide's lightweight split of UI,
data, domain models, routing, and configuration.

## Directory layout

- `config/`: app-level constants and runtime configuration.
- `data/repositories/`: feature-facing repositories that expose backend data.
- `data/services/`: low-level services such as HTTP and local session storage.
- `domain/models/`: app data models shared across UI and repositories.
- `routing/`: app route definitions and navigation guards.
- `ui/`: screens, widgets, themes, and view models.

## UI modules

Feature UI is grouped under `ui/<feature>/`:

- `widgets/`: screens and reusable widgets for that feature.
- `view_models/`: UI state coordinators when the feature needs one.

Only add a `domain/` service or use-case layer when business rules become too
complex to keep in repositories or view models.

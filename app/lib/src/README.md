# Flutter client architecture

This client follows the Flutter architecture guide's lightweight split of UI,
data, domain models, routing, and configuration.

## Directory layout

- `config/`: app-level constants, runtime configuration, and static app copy.
- `data/repositories/`: backend and feature data access. Repository files only
  expose repository implementations and their dependency-injection providers.
- `data/services/`: shared low-level HTTP and session services.
- `data/services/device/`: device capabilities that are independent of a
  business feature.
- `data/services/nfc/`: NFC hardware integration and tag encoding.
- `data/services/storage/`: feature-specific local persistence.
- `domain/models/`: pure app data models shared across UI and repositories.
- `routing/`: app route definitions and navigation guards.
- `ui/`: screens, widgets, themes, and view models grouped by feature.

## UI modules

Feature UI is grouped under `ui/<feature>/`:

- `widgets/`: screens, sheets, and reusable widgets for that feature.
- `view_models/`: controllers and Riverpod query providers that coordinate UI
  state with one or more repositories.

Keep dependencies pointing inward from UI to data and domain. Data code must
not import UI code. A repository should not own page loading, selection, or
session-aware query state; place that state in the consuming feature's
`view_models/` directory.

Only add a domain service or use-case layer when business rules become too
complex to keep in repositories or view models.

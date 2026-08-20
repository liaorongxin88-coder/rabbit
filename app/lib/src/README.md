# Flutter client architecture

The client keeps the lightweight UI, data, and domain layers, then applies the
same business vocabulary inside each layer. Code for a rabbit, cage, batch, or
reproduction flow should be discoverable by that business name without mixing
complete screens with reusable widgets.

## Directory layout

- `config/`: app-level constants, runtime configuration, and static app copy.
- `data/repositories/<business>/`: backend access grouped by business module.
- `data/services/<capability>/`: low-level `auth`, `network`, `nfc`, and
  `storage` capabilities.
- `domain/<business>/`: pure models grouped by business module.
- `routing/`: app route definitions and navigation guards.
- `ui/<feature>/`: feature UI grouped again by visible interface type.

The business groups are `auth`, `houses`, `cages`, `rabbits`, `batches`,
`reproduction`, `reports`, `outbound`, `settings`, `nfc`, and `profile`.
`dashboard` and `home` name UI surfaces, so they stay in the UI layer rather
than becoming data or domain namespaces. A layer only contains the groups it
actually needs; do not add empty mirror directories.

## UI interface types

Feature UI uses these directories:

- `screens/`: complete destinations opened by GoRouter or Navigator.
- `sheets/`: bottom sheets and dialogs that temporarily cover a screen.
- `widgets/`: reusable feature fragments that are not navigation destinations.
- `view_models/`: controllers and Riverpod query providers that coordinate UI
  state with one or more repositories.

Shared shells, state views, and theme tokens stay under `ui/core/`. A screen
whose route starts with `/houses/:houseId` still belongs to the business it
manages: for example, cage and rabbit lists live under `ui/cages/screens/` and
`ui/rabbits/screens/`, not under `ui/houses/`.

Shared code stays at its nearest ownership boundary:

- Transport-wide response validation lives in `data/services/network/`.
- UI primitives used by multiple features live in `ui/core/widgets/`.
- Business-aware widgets remain in their owning feature even when another
  screen looks similar.

Do not create generic `shared/`, `common/`, or `utils/` dumping directories.
Extract only behavior with multiple real consumers and one stable contract.

Keep dependencies pointing inward from UI to data and domain. Data code must
not import UI code. A repository should not own page loading, selection, or
session-aware query state; place that state in the consuming feature's
`view_models/` directory.

## Namespace-aware file names

Every parent directory is part of a file's namespace. File names only carry
the meaning needed to distinguish siblings:

- `data/repositories/rabbits/repository.dart`, not
  `rabbits/rabbit_repository.dart`.
- `ui/rabbits/screens/detail.dart`, not
  `rabbits/screens/rabbit_detail_screen.dart`.
- `ui/rabbits/sheets/move.dart`, not
  `rabbits/sheets/rabbit_move_cage_sheet.dart`.
- `domain/cages/layout.dart`, not `cages/cage_layout.dart`.

Do not repeat a directory role with suffixes such as `_screen`, `_sheet`,
`_repository`, `_service`, `_store`, `_provider`, `_controller`, `_config`,
`_theme`, or `_model`. Use `list.dart` for collection destinations and
`overview.dart` for a module landing screen. Keep a concrete domain entity's
natural singular name, such as `rabbits/rabbit.dart`; `model.dart` would hide
useful meaning rather than remove redundancy.

Only add a domain service or use-case layer when business rules become too
complex to keep in repositories or view models.

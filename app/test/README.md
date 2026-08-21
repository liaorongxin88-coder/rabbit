# Flutter test architecture

Tests use the same ownership rules as `lib/src`. A test belongs to the layer,
business module, and UI interface that owns the behavior it verifies.

## Directory layout

- `app/`: application assembly, startup, session expiry, and router lifecycle.
- `config/`: application configuration and static legal copy.
- `data/repositories/<business>/`: repository requests, decoding, pagination,
  and backend contracts grouped by business module.
- `data/services/<capability>/`: low-level `auth`, `network`, `nfc`, and storage
  behavior grouped by technical capability.
- `domain/<business>/`: pure model parsing and business rules.
- `routing/`: route definitions, guards, and navigation structure.
- `ui/<feature>/<interface>/`: widget and provider tests grouped into `screens`,
  `sheets`, `widgets`, and `view_models`, matching the production UI.
- `architecture/`: dependency and directory rules for source and tests.

Device workflows stay under `integration_test/<business>/`. They are grouped by
the business flow they prove rather than by the script that launches them.

When one test crosses layers, use the highest-level behavior as its owner. A
screen test that replaces a repository still belongs to the screen. A repository
contract test that parses domain objects belongs to the repository. Keep shared
test support beside its nearest owner, and do not add `shared/`, `common/`, or
`utils/` directories.

Parent directories are part of a test's namespace. Use concise names such as
`ui/rabbits/screens/detail_test.dart` and
`data/repositories/rabbits/pagination_test.dart`. Do not create empty directories
only to mirror a production folder that has no tests.

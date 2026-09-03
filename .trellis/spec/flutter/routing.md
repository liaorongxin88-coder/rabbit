# Flutter routing

`MaterialApp.router` consumes the single `GoRouter` from `appRouterProvider`. `app/lib/src/routing/routes.dart` owns the root navigator key, shell navigator, full route table, and redirect logic.

## Redirect order

The router waits for local settings, then authentication restore. It preserves a `from` URL during auth loading, accepts only internal relative protected locations, sends unauthenticated users to `/login`, and prevents authenticated users from staying there. Keep this order when adding a guard; an early redirect can lose the intended destination or race restored state.

Do not accept an external or protocol-relative `from` target. Protected return locations must stay inside the application.

## Shell and navigation

`ShellRoute` owns the four primary destinations: home, houses, dashboard, and profile. `app/lib/src/ui/core/widgets/shell.dart` uses `context.go` for bottom-navigation branch switches because they replace the current location. Detail and temporary flows normally use `context.push`.

`app/lib/src/ui/core/widgets/page.dart` supplies a labeled 48dp back target plus explicit parent or fallback routes. Keep direct child URLs useful under Android back instead of assuming a navigation stack always exists.

Centralize application routes in `routes.dart`. Legal documents are the intentional current exception: login and settings open `LegalDocumentScreen` with `Navigator.push(MaterialPageRoute)`, so those screens are not deep-linkable GoRouter destinations. Do not copy that exception for ordinary features.

## Verification

Test redirect behavior around cold startup, authenticated startup, invalid `from` values, direct child URLs, and shell branch changes. Route coverage currently comes mainly from app and widget tests. The routing test owner documented in `app/test/README.md` has no dedicated test files, so record the gap when a routing change lacks focused coverage.

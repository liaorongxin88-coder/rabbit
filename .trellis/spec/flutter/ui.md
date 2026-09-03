# Flutter UI

The client is a restrained farm-management tool using Material 3 and fixed `zh_CN` locale. `app/lib/src/ui/core/theme.dart` owns `AppColors`, the light/dark `AppPalette` extension, and shared spacing. Business widgets should read the theme or `AppPalette` instead of adding one-off hex colors.

## Visual and interaction rules

Use centered app-bar titles, Material icons, border-based cards without elevation, 8px default radii, 48dp primary controls, and the existing 74dp four-item navigation bar. `AppSpacing` owns page padding; small component spacing may remain local. A few current controls use larger radii or `FittedBox(scaleDown)`. These are local exceptions.

The root app permits text scaling up to 200 percent. Bound long titles and contextual labels with sensible line counts and ellipsis. `app/lib/src/ui/core/widgets/page.dart` supports two title lines and enlarges the toolbar at higher scales. Do not use `FittedBox` to erase a user's font setting in ordinary content.

There is no global breakpoint type. Use `LayoutBuilder`, `MediaQuery`, `Wrap`, or scrolling based on the component. Core test sizes are 360x800, 393x852, and 412x915 with 200 percent text scaling.

## Forms and sheets

Forms and sheets must react to `MediaQuery.viewInsets.bottom`, remain scrollable, dismiss the keyboard on drag, and keep fields and submit actions reachable. Widget tests exercise 180, 300, and 420 logical-pixel keyboard insets. Keep tap targets at least 48dp where the shared controls establish that contract.

Render distinct loading, data, empty, and error states. Add retry where the request is recoverable. Use semantics labels for icon-only actions and for dense business cells whose visual abbreviations are not sufficient.

## Cage map

The cage map has enforced spatial behavior in `app/lib/src/ui/cages/widgets/map.dart` and related tests:

- Show one layer at a time, starting at layer 1 and ordering upward.
- Keep each row on one horizontal line and scroll instead of wrapping.
- Preserve empty coordinate slots and dim filtered cells instead of removing them.
- Grow cells with text scale.
- Reserve primary blue for selection, not status.
- Pair every status with an icon, text, legend entry, and semantics label.

Do not collapse malformed or empty coordinates in a way that changes the physical map. Check selection, filtering, overflow, semantics, and 200 percent text scale after changes.

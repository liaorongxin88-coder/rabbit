# Design and accessibility

`admin/DESIGN.md` is the visible-behavior contract. The console is a compact operational tool. Use the semantic tokens and primitives already defined in `admin/src/index.css` and `admin/src/components/ui/`.

## Visual system

Use semantic utilities such as `bg-background`, `bg-card`, `bg-secondary`, `text-muted-foreground`, `border`, `bg-primary`, `text-warning`, and `text-destructive`. Primary teal indicates intent or selection; green, amber, and red indicate positive, warning, and destructive states. Do not scatter raw Tailwind status colors through pages.

Keep cards at 8px radius or less, controls at 6px, body text at 14px by default, and letter spacing at zero. Use `lucide-react` icons. Hide decorative icons from assistive technology and give icon-only controls an accessible name.

Avoid gradients, blobs, illustration backgrounds, oversized heroes, broad tinted bands, nested cards, floating page sections, and marketing-style empty space. `admin/src/components/metric-card.tsx` is a restrained domain component, not a reason to wrap every section in a card.

Two current files drift from these rules. `admin/src/components/workspace-outbound-dialog.tsx` uses raw emerald border, background, and text utilities for its completed state. `admin/src/pages/workspace-account-page.tsx` uses `tracking-wider` for the account identifier. Treat both as existing exceptions, not patterns for new UI.

## Layout and motion

Desktop uses a fixed 16rem sidebar, `lg:pl-64`, and content up to `max-w-7xl`. Mobile replaces the sidebar with a compact header or workspace navigation. Stack headers, filters, and actions before switching to horizontal layouts. At 320px, controls may wrap and tables may scroll, but identity, actions, dialog controls, and text must remain clear.

Use the shared 140ms, 220ms, and 340ms motion tokens for page or section entry, dialogs, small hover lift, and press feedback. Avoid looping motion, bounce, parallax, and movement over 2px on layout-critical surfaces. `prefers-reduced-motion` removes transforms and shortens transitions in `admin/src/index.css`.

## States and semantics

Keep visible focus rings. Mark invalid controls with `aria-invalid`; retain Radix dialog title and description semantics. Do not communicate state by color alone. Loading uses skeletons or a spinner, pending writes disable their controls, and empty results should differ from recoverable failure when the workflow needs that distinction.

`admin/src/components/batch-statistics.tsx` uses `aria-live="polite"` for loading and `role="alert"` for failure. `admin/src/components/cage-map.tsx` adds icon and text equivalents, `aria-pressed`, and full cage labels. Older pages such as `farms-page.tsx` can clear failed data and fall through to an empty view after a toast. This is a known error-state gap.

## Cage map exception

The cage map may use low-opacity semantic status colors for spatial scanning. Primary teal remains selection only. Each status also needs an icon and Chinese label. Show one prioritized attention state per cage, dim filtered cells rather than removing them, preserve missing positions, keep rows separate, and show one layer at a time in ascending order. Keep the table view available for dense lookup.

Malformed coordinates and duplicates go to an unplaced bucket instead of disappearing. Pure layout behavior lives in `admin/src/lib/cage-map.ts` and is pinned by `admin/test/cage-map.test.mjs`.

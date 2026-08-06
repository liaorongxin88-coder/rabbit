---
name: Rabbit SaaS Admin
description: Operational platform console and merchant rabbit-farm workspace with isolated identities, permissions, and business scope.
status: canonical
owners: admin frontend
framework: React + TypeScript + Vite
styling: Tailwind CSS + shadcn/ui-style source components
request_layer: alova
tone:
  product: calm
  density: compact
  motion: restrained
  copy: operational Chinese
colors:
  background: "#FFFFFF"
  foreground: "#0F172A"
  primary: "#117A88"
  accent: "#298E4E"
  secondary: "#F1F5F9"
  muted_foreground: "#64748B"
  border: "#E2E8F0"
  destructive: "#DC2828"
radii:
  card: 8px
  control: 6px
  small: 4px
motion:
  fast: 140ms
  normal: 220ms
  slow: 340ms
layout:
  max_content_width: 80rem
  desktop_sidebar_width: 16rem
  mobile_min_width: 320px
---

# Rabbit SaaS Admin Design

This document is the source of truth for the `admin/` frontend visual and interaction design. Keep new screens consistent with these rules before adding new patterns. The durable implementation rules live in `.rules`; this file explains how the product should look, feel, and behave.

## Overview

Rabbit SaaS Admin contains two operational surfaces that share one restrained design system but not one identity or permission model. The platform console manages merchants, account memberships, merchant enablement state, rabbit-house governance policies, and read-only business summaries. The merchant workspace manages the selected merchant and rabbit house through existing business APIs.

The interface should feel like a quiet control room: clear hierarchy, low visual noise, stable controls, and enough density for repeated daily work. It is not a marketing site. Platform routes are not business data editors; merchant routes are production tools constrained by merchant and rabbit-house permissions.

## Design Principles

- **Operational clarity first.** Every surface should help an operator answer one of three questions: which merchant is this, what state is it in, and what action is safe here?
- **Hierarchy through structure.** Use layout, spacing, table grouping, and muted metadata instead of decorative panels or bright color blocks.
- **One memorable accent.** The teal primary color is the product signature. Keep everything around it neutral and disciplined.
- **Read-only means read-only in platform scope.** Rabbit houses, cages, rabbits, and audit previews remain inspection surfaces in platform routes.
- **Business actions follow selected scope.** Merchant pages must make the active merchant, active rabbit house, and effective role visible before write actions.
- **Motion confirms state.** Animation should help the eye follow navigation, table updates, dialogs, and button presses; it must not compete with data readability.

## Audience And Jobs

Primary users are internal platform operators and merchant business users. Platform operators work across merchants. Merchant users work inside one selected merchant and rabbit house at a time. Both need quick scanning, low ambiguity, and visible permission boundaries.

Core jobs:

- Find a merchant by name, contact, phone, status, or ID.
- Create a merchant together with its initial login account, or update merchant profile information.
- Add merchant login accounts, configure membership roles, and keep one owner for every merchant.
- Configure whether a merchant can create rabbit houses or manage house members, plus capacity limits.
- Enable or disable a merchant with clear risk signaling.
- Inspect global accounts and edit their membership inside a merchant.
- Inspect merchant scale through counts, rabbit house previews, user lists, and audit previews.
- Switch among merchants and rabbit houses available to the signed-in business account.
- Manage houses, cages, rabbits, production batches, and member roles when the effective permission allows it.

Non-goals:

- Package, billing, invoice, or plan management.
- Merchant self-registration.
- Customer support impersonation.
- Exposing platform-wide business editing.
- NFC tag writing/resolution, Bluetooth, camera, MQTT, or hardware-triggered controls in the PC workspace.
- Landing pages, dashboards built for storytelling, or decorative hero layouts.

## Color System

Use semantic Tailwind tokens in code. The hex values below are documentation anchors that correspond to the current HSL tokens in `src/index.css`.

| Token | Hex | Usage |
| --- | --- | --- |
| `background` | `#FFFFFF` | Main canvas and page background |
| `foreground` | `#0F172A` | Primary text and strong icons |
| `primary` | `#117A88` | Main actions, focus rings, active navigation |
| `accent` | `#298E4E` | Positive summaries and enabled business state |
| `secondary` | `#F1F5F9` | Quiet fills, navigation active background, table hover |
| `muted-foreground` | `#64748B` | Descriptions, metadata, secondary table text |
| `border` | `#E2E8F0` | Hairline borders and input outlines |
| `destructive` | `#DC2828` | Errors and destructive actions only |

Rules:

- Use `bg-background`, `bg-card`, `bg-secondary`, `text-muted-foreground`, `border`, `bg-primary`, and `text-destructive` before raw Tailwind color utilities.
- Amber is reserved for warnings. Red is reserved for errors and destructive actions.
- Do not use broad tinted sections. If emphasis is needed, use a badge, status cell, icon, or border.
- Avoid one-note palettes. Teal should mark intent, not flood the page.

## Typography

Use the system sans stack defined in `src/index.css`. The product should read as a professional management tool, not editorial content.

Type scale:

- Page title: `text-2xl font-semibold tracking-normal`
- Section and card title: `text-base font-semibold`
- Body and form controls: `text-sm`
- Metadata, table headers, badge text: `text-xs`
- Numeric metrics: `text-2xl font-semibold`

Rules:

- Letter spacing stays `0` or `tracking-normal`.
- Do not scale font size with viewport width.
- Keep headings short and literal. Avoid clever slogans.
- Long values such as merchant names, OpenID, phone, and IDs must truncate or wrap without overlapping actions.

## Layout

Primary layout:

```text
Desktop
┌─────────────── fixed sidebar 16rem ───────────────┬───────────────────────────────┐
│ Rabbit SaaS                                       │ page header + actions          │
│ nav                                               │ filters / tabs / content       │
│ session + logout                                  │ max width 80rem                │
└───────────────────────────────────────────────────┴───────────────────────────────┘

Mobile
┌──────────────── compact header ────────────────┐
│ Rabbit SaaS                         logout     │
├────────────────────────────────────────────────┤
│ stacked page header                            │
│ wrapped filters                                │
│ scrollable tables / reachable actions          │
└────────────────────────────────────────────────┘
```

Rules:

- Desktop uses a fixed sidebar and `lg:pl-64` content offset.
- Content width is constrained to `max-w-7xl`.
- Page sections are unframed layouts. Cards are reserved for contained tools, repeated metrics, tables, dialogs, and empty states.
- Do not put cards inside cards.
- Do not style page-level sections as floating cards.
- Responsive controls may wrap, but must not overlap.
- Tables may scroll horizontally when needed, but primary row identity and actions must remain understandable.

## Spacing And Shape

- Main content stack gap: `gap-6`.
- Card internal padding: `p-5`.
- Field and filter group gap: `gap-3` to `gap-5` depending on density.
- Card radius: 8px or less.
- Control radius: 6px.
- Small badge or tab radius: 4px to 6px.

Rules:

- Prefer stable dimensions for tables, icon buttons, metric cards, tabs, counters, and dialogs.
- Hover, loading, focus, and disabled states must not resize components.
- Avoid oversized whitespace. This is a repeated-use console, not a brochure.

## Elevation

Elevation should be almost flat.

- Base cards use a semantic border and restrained shadow.
- Hover cards may lift by 1px and add a soft shadow.
- Dialogs may use stronger shadow because they block the workflow.
- Do not use stacked shadows, blurred backgrounds, or floating decorative panels.

## Motion

Motion tokens live in `src/index.css`:

- `--motion-fast: 140ms`
- `--motion-normal: 220ms`
- `--motion-slow: 340ms`
- `--motion-ease`
- `--motion-spring`

Use these global classes:

- `motion-page`: route and page-shell entry.
- `motion-section`: page header, loading group, metric, and tab content entry.
- `motion-list`: subtle table row entry.
- `motion-card`: small hover elevation for contained surfaces.
- `motion-press`: button press feedback.
- `motion-dialog-overlay`: dialog overlay fade.
- `motion-dialog-content`: dialog content fade and scale.

Rules:

- Motion should clarify hierarchy and state changes, not decorate the product.
- Page entry may animate once with a small vertical offset and fade.
- Table row stagger must stay short enough that data remains immediately readable.
- Cards may lift by 1px on hover. Do not move layout-critical surfaces by more than 2px.
- Dialogs fade and scale in. They must remain centered and must not animate after opening.
- Avoid looping, bouncing, parallax, large translation, and long staged sequences.
- Always preserve `prefers-reduced-motion`; new animation must be covered by the existing reduced-motion media query.

## Navigation

Desktop navigation is a fixed left sidebar with product identity, primary sections, and the current session at the bottom. Merchant routes place compact merchant and rabbit-house selectors above their business navigation.

Mobile navigation uses a compact sticky top header. Merchant routes use a horizontally scrollable business-navigation row and an unframed selector block below it so every route and tenant control remains reachable without a drawer.

Rules:

- Active navigation should use quiet secondary fill and stronger foreground text.
- Product identity should stay compact: `Rabbit SaaS / 平台管理端` for platform routes and `Rabbit Farm / 商户工作台` for merchant routes.
- Do not add duplicate routes that point to the same page unless the label exposes a real distinct workflow.

## Components

Use shadcn-style source components under `src/components/ui` before adding custom markup.

Expected primitives:

- Forms: `FieldGroup`, `Field`, `FieldLabel`, `Input`, `Textarea`, `Select`.
- Dialogs: `Dialog`, `DialogHeader`, `DialogTitle`, `DialogDescription`, `DialogFooter`.
- Data: `Table`, `Card`, `Badge`, `MetricCard`, `Skeleton`, `Empty`.
- Feedback: `Spinner`, `sonner` toast.
- Icons: `lucide-react`.

Rules:

- Buttons use `variant`, `size`, and `asChild`.
- Icons inside buttons use `data-icon`; do not manually size them in page code.
- Dialogs must include a useful description, especially for create, edit, bind, and destructive flows.
- Forms should group labels, inputs, validation hints, and descriptions consistently.
- Toasts should name the completed action, such as `商户已启用` or `账号已创建`.

## Tables And Data Display

Tables are the primary data surface. They should be compact, scannable, and predictable.

Rules:

- First column should identify the object and include useful secondary metadata, such as `ID`.
- Status is shown with a badge, not raw text only.
- Action column sits on the right and uses concise verbs.
- Table headers use small muted text.
- Empty states replace tables only when the list is truly empty after loading.
- Loading states use skeleton rows that preserve table/card rhythm.
- Dates should be consistently formatted and fall back to `-` when absent.

## Forms And Dialogs

Rules:

- Create and edit forms open in dialogs for first-version merchant management.
- In the fixed-sidebar shell, desktop dialogs are centered inside the main content pane, not the full viewport. The dialog layout viewport uses `left: var(--admin-sidebar-width)` and `right: 0`; do not combine `left/top` centering with `translate(-50%, -50%)` motion transforms.
- Dialog forms should reset state when opened for a new target.
- Long dialog forms keep the header and footer fixed inside the card; only the form body scrolls.
- Required fields should be visibly labeled and validated before submit.
- Filter forms with inline actions must align action buttons to the input/select control row, not to helper text. Put helper text in a separate row below the controls when it would change grid row height.
- Search and filter action buttons should use the same visual height as adjacent inputs and selects.
- Async submit buttons must disable during saving and show loading feedback when available.
- Keep destructive operations explicit. Do not hide merchant disable behind a generic edit form.

## Status, Risk, And Permissions

Merchant status is the most important platform state in the first version.

Rules:

- `ENABLED` should read as normal/healthy and may use the accent green.
- `DISABLED` should read as restricted/high attention, but red should be reserved for the destructive action itself or error state.
- `停用商户` uses destructive styling.
- `启用商户` uses primary/default styling.
- Merchant account creation and role changes happen in merchant context; a global account may have memberships in multiple merchants.
- Ownership transfer must remain explicit and must never leave a merchant without an enabled owner.
- Platform admin screens must not imply merchant-level permission controls unless the backend supports them.

## Copy

Use plain operational Chinese.

Rules:

- Prefer precise verbs over promotional copy.
- Labels name the object being edited, not the backend concept.
- A control should say exactly what happens: `新增商户`, `新增账号`, `查询`, `编辑`, `停用商户`, `启用商户`.
- Toast vocabulary should match the action vocabulary.
- Empty copy should explain what makes data appear and provide an action only when the operator can perform it here.
- Error copy should direct the operator to the next practical step.
- Do not add in-app text explaining visual style, animations, keyboard shortcuts, or implementation details.

## Accessibility

Rules:

- All icon-only buttons need accessible labels.
- Focus states must remain visible and use the semantic ring color.
- Dialogs must have title and description.
- Inputs must be associated with labels.
- Do not communicate state with color alone.
- Respect reduced motion.
- Preserve readable contrast for muted text on white and secondary surfaces.

## Do

- Build dense but calm operational workflows.
- Keep admin APIs and merchant APIs visually and technically separate.
- Use the existing request layer and semantic UI primitives.
- Add new design tokens only when a repeated need appears.
- Verify desktop and narrow mobile layouts for text overlap and action reachability.
- Update this document when introducing a durable new visual pattern.

## Do Not

- Do not create a landing page or hero-first admin home.
- Do not add decorative gradients, blobs, bokeh, or illustration backgrounds.
- Do not add business data edit controls to platform read-only overview sections.
- Do not show NFC, Bluetooth, MQTT, camera, or hardware-trigger controls in merchant web navigation while those capabilities are pending. Business-only production transitions remain available and must submit with hardware triggering disabled.
- Do not place cards inside cards.
- Do not scatter raw Tailwind colors through page code.
- Do not call `fetch` directly from components.
- Do not introduce another UI library, CSS framework, request library, or state manager for routine work.

## Implementation Guardrails

- Request code belongs in `src/lib/request.ts` and business API files under `src/api/`.
- Components must not call raw `fetch`.
- Keep animation and design tokens in `src/index.css`.
- New reusable UI behavior belongs in `src/components`, not duplicated inside pages.
- Run `pnpm --dir admin lint` and `pnpm --dir admin build` after UI changes.
- For design-only document changes, at least run `git diff --check -- admin/DESIGN.md`.

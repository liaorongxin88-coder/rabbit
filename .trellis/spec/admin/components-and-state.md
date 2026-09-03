# Components and state

Pages own route-level workflows. Shared layout and navigation belong in shells, domain display belongs in `src/components/`, generic mechanics belong in `src/components/ui/`, and pure calculations or request-ID logic belong in `src/lib/`. `admin/src/pages/farms-page.tsx` and `admin/src/components/admin-account-form-dialog.tsx` are representative.

## Components and forms

Compose shared primitives instead of recreating focus, disabled, spacing, or dialog behavior in a page. `admin/src/components/ui/button.tsx` exposes `variant`, `size`, and `asChild` through CVA. Dialog, Select, Tabs, Label, and Separator wrap Radix.

Forms use `FieldGroup`, `Field`, `FieldLabel`, `Input`, `Textarea`, and the shared Select. Keep labels associated with controls, include dialog titles and descriptions, and keep long dialog headers and footers reachable while the field area scrolls. Some current Select labels lack matching trigger IDs. That is an accessibility gap, not a pattern to repeat.

Disable write controls while pending. Initialize or reset dialog drafts when opening. After success, close the dialog and reload through the owning loader or `workspace.refresh()`. The application does not use optimistic server-cache updates.

## State ownership

Most server snapshots, filters, forms, selection, pagination, and dialogs use component `useState`. `admin/src/components/workspace-context.tsx` shares accessible houses, selected house, and permissions. Sessions and per-user selected house are the persistent browser state. There is no Redux, TanStack Query, SWR, or shared server cache.

`WorkspaceProvider` retains a house-loading `error`, but `WorkspaceShell` does not render it. Feature components such as `OperationEventStream` have stronger local failure and retry states. Treat the unrendered provider error and older page loaders that turn failures into empty results as current gaps, not preferred state handling.

The common loader pattern is:

```tsx
const load = useCallback(async () => {
  setLoading(true)
  try {
    setItems(await listItems())
  } catch (error) {
    // Report or render the failure according to the owning view.
  } finally {
    setLoading(false)
  }
}, [])

useEffect(() => {
  void load()
}, [load])
```

Separate draft filters from submitted criteria when keystrokes should not refetch. Guard concurrent loaders when an older request could overwrite new filters, route params, or selected-house data. Request-generation counters exist in newer components, but many older pages lack race protection.

## Mutations and request IDs

Helper-backed retryable writes bind `requestId` to the logical draft. Keep the ID for an unchanged retry, rotate it when meaningful input changes, and clear the draft after success. `admin/src/lib/farm-request.ts`, `admin/src/lib/invitation-request.ts`, `admin/src/lib/rabbit-sale.ts`, and `admin/src/lib/batch-workflow.ts` implement this behavior outside React so Node tests can verify it.

Do not generate a fresh request ID for each retry click. After a successful mutation, inspect every affected local snapshot and reload it explicitly because no central cache tracks dependencies.

This rule is not implemented by every current mutation. `admin/src/components/workspace-outbound-dialog.tsx` generates a new ID for each submit attempt. Several methods in `admin/src/api/workspace.ts`, including house creation, member role changes, member removal, and some rabbit or batch writes, also generate an ID inside the API call, so a later click cannot reuse it after an unknown result. Do not copy those patterns into a new retryable flow. A task that fixes outbound submission must preserve one ID for the logical submission and use the backend request-status contract before deciding whether to retry.

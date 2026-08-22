# Batch outbound implementation notes

This document records the pragmatic decisions used for the first production slice described by
[`product-requirements.md`](product-requirements.md). The PRD remains the product authority; these notes make the current
model mapping and rollout boundaries explicit.

## Model and business assumptions

- A PRD rabbitry scope maps to one existing `rabbit_houses.id`. All task, request, candidate, and
  final-submit queries are constrained by that house and existing `view`/`edit` house permissions.
- The Flutter selection page shows the task's source scope, chooses its initial cage/row/house mode
  from the entry type, persists later mode changes with the local draft, and can filter the list to
  the exact selected rabbit set without changing that set.
- Existing rabbit `type = "2"` is the commodity-rabbit discriminator. The active
  `batch_rabbits.current_status` and its next event supply the commodity stage.
- Stages containing `可出售` or `待出售`, or an already-due `出售` event, are normal outbound.
  `适应`, `生长`, and `育肥` stages, or a future `出售` event, require per-rabbit early-sale
  confirmation and a reason.
- Isolation and an open treatment are absolute blockers. An unresolved abnormal condition is a
  needs-action result. Missing cage/stage data, disabled cages, non-commodity rabbits, and inactive
  rabbits are blockers.
- The existing house `edit` permission authorizes ordinary eligible-rabbit draft changes and
  submit. Selecting or freezing an `EARLY_SALE` item requires `control`, and final submit derives
  that requirement again from the server-side frozen items. Marking a rabbit for breeding uses the
  existing replacement conversion endpoint, which also requires `control`.
- One task may combine active production batches into one sale order. Each sale item keeps the
  batch, cage coordinates, rabbit type/stage/version, weight, early-sale reason, and parallel-state
  snapshot that existed at submit time.
- Historical outbound dates are limited to today and the preceding 30 days. Drafts are resumable
  for seven days. Both are fixed first-slice values pending role/configuration support.
- Existing cages are backfilled into the `LEGACY` row. Newly generated house cages receive stable
  row/layer/position coordinates; existing cage edit clients retain coordinates when they omit the
  new fields.
- Before applying V11 to a database with historical sales, run the following preflight SQL against
  the exact target schema. The first result set identifies the rows to review; the second must return
  `duplicate_groups = 0`. V11's `uk_sale_order_rabbit` DDL intentionally fails otherwise. Duplicate
  business history must be repaired from its source records, not deleted by the migration:

  ```sql
  -- V11 duplicate preflight: safe, read-only, and executable in MySQL 8.
  SELECT sale_order_id, rabbit_id, COUNT(*) AS duplicate_count
  FROM sale_order_items
  GROUP BY sale_order_id, rabbit_id
  HAVING COUNT(*) > 1
  ORDER BY sale_order_id, rabbit_id;

  SELECT COUNT(*) AS duplicate_groups
  FROM (
    SELECT sale_order_id, rabbit_id
    FROM sale_order_items
    GROUP BY sale_order_id, rabbit_id
    HAVING COUNT(*) > 1
  ) AS duplicate_sale_rabbits;
  ```

  A shell gate suitable for the documented local E2E database is:

  ```bash
  duplicate_groups="$(mysql --host=127.0.0.1 --port=3306 --user=root \
    --password --database=rabbit_app_e2e --batch --skip-column-names \
    --execute="SELECT COUNT(*) FROM (SELECT sale_order_id, rabbit_id FROM sale_order_items GROUP BY sale_order_id, rabbit_id HAVING COUNT(*) > 1) AS d")"
  test "$duplicate_groups" -eq 0
  ```

### V11 rollback and failed-DDL recovery

- Take a schema plus data backup before V11. MySQL DDL implicitly commits, so Flyway cannot roll
  back a partially applied V11 as one transaction.
- If V11 stops while adding `uk_sale_order_rabbit`, do not run `flyway repair` first. Inspect
  `information_schema.columns`, `information_schema.statistics`, and `information_schema.tables`
  to record which V11 statements already committed; reconcile the duplicate sales; then make the
  schema match V11 and run `flyway repair` followed by `flyway migrate`.
- If the application must be rolled back after a successful V11, disable the batch-outbound entry
  points and deploy the previous application while leaving the additive schema in place. The old
  application ignores the new columns and tables, and completed outbound sales remain canonical.
- A physical down-migration is only acceptable before any outbound task/request/sale has been
  written and after restoring a verified backup. Drop foreign-key child tables before parent tables
  (`outbound_task_items`, `outbound_requests`, then `outbound_tasks`), then remove the sale-item
  unique key and added snapshot columns, rabbit `state_version`, and cage coordinate index/columns.
  Do not use that procedure after rollout: it would discard idempotency history and state versions.

## Transaction and concurrency contract

- Entering confirmation persists the exact rabbit IDs, state versions, selection types, reasons,
  and location snapshots on the task.
- Submit first claims a unique `requestId` and validates a canonical payload hash. Reusing that ID
  with different input is rejected; retrying an identical completed request returns the same sale.
- The claim and each finalization run in separate `REQUIRES_NEW` transactions around the inner
  business transaction. A business rollback therefore cannot erase its idempotency request row.
- A confirmed `BizException` rollback is finalized as `FAILED` with a durable error code/message;
  retry and status return that same result. An unexpected transaction/connection outcome remains
  `PROCESSING` until status can reconcile it from the task and sale state.
- Submit and status request IDs must use the lowercase canonical UUID representation emitted by the
  Flutter client.
- The transaction locks the task, rabbits, and cages in stable ID order, then re-runs eligibility,
  version, and location checks. Eligibility-changing rabbit, batch-stage, treatment, and abnormal
  mutations advance the rabbit state version.
- A successful transaction creates the order and immutable items, deactivates rabbits and active
  batch relationships, records departures and status history, completes empty batches, updates cage
  counts, and completes the task/request. A conflict writes none of those business records and
  restores the draft to confirmation.
- Reports and reminders in this slice continue to derive from the canonical order/rabbit/batch data.
  The Flutter client invalidates those providers after success; no disconnected async event stub is
  introduced.

## API surface

- `POST /api/outbound/tasks` creates or discovers a resumable task.
- `GET /api/outbound/tasks/{taskId}` reads a task.
- `POST /api/outbound/tasks/{taskId}/precheck` refreshes server-owned eligibility.
- `PUT /api/outbound/tasks/{taskId}` revision-checks and saves the draft/frozen snapshot.
- `POST /api/outbound/tasks/{taskId}/cancel` cancels an editable draft.
- `POST /api/outbound/tasks/{taskId}/submit` performs idempotent atomic outbound.
- `GET /api/outbound/requests/{requestId}` resolves an ambiguous submit result.

## Deferred rollout work

- Dedicated outbound list/query/edit permission codes are enforced at the API boundary. Early sale
  additionally requires the house `control` capability. A feature flag, product analytics, and a
  separate append-only audit-event stream should still be added before broad rollout. This slice
  retains durable task/request/order/departure histories.
- Very large houses should add paged or incremental precheck responses and list virtualization based
  on measured field sizes. The first slice returns one complete house scope so selection totals are
  authoritative.
- Automated reconciliation and a reliable outbox are still needed if reminders or reports move from
  derived reads to asynchronous materialized data.
- Customer history selection, hardware weighing/scanning, exports, and reversal/credit workflows
  remain outside this slice.

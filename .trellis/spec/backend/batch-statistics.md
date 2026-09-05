# Batch statistics contract

## Scenario: Batch statistics and immutable write snapshots

### 1. Scope / Trigger

Read this spec before changing batch statistics, feed allocation, weaning weight,
sales or outbound allocation, replacement conversion weight, carcass yield, or
the batch statistics workbook. These paths share one versioned contract across
the backend, Admin, and Flutter. A change in one writer can invalidate metrics
that appear unrelated in the UI.

The production batch domain owns calculation and status decisions. The domain
that performs a business write owns its immutable snapshot in the same
transaction. Reporting consumes the completed statistics snapshot and must not
query business tables or duplicate formulas.

### 2. Signatures

House-scoped HTTP signatures:

```text
GET  /api/batches/{batchId}/statistics
     permission: rabbit:batches:query
     response: { code, message, data: BatchStatistics }

GET  /api/reports/batches/{batchId}/statistics.xlsx
     permission: rabbit:reports:export
     response: XLSX bytes, not the JSON envelope

POST /api/batches/{batchId}/carcass-yields
     permission: rabbit:batches:edit
     body: yieldRate, sourceUnit, measuredDate, reportNumber?,
           evidenceFileId?, remark?, changeReason, requestId

GET  /api/batches/{batchId}/carcass-yields?page={page}&pageSize={pageSize}
     permission: rabbit:audit:list

POST /api/feed-logs/allocation-preview
POST /api/feed-logs
PUT  /api/outbound/tasks/{taskId}
POST /api/outbound/tasks/{taskId}/submit
GET  /api/outbound/requests/{requestId}
```

Database signatures:

```text
V55__batch_statistics_write_snapshots.sql
  litters.weaning_total_weight_kg DECIMAL(12,3)
  feed_log_batch_allocations
  sale_order_batch_allocations
  replacement_batch_allocations
  batch_carcass_yield_versions

V56__outbound_draft_batch_allocations.sql
  outbound_task_batch_allocations
```

Every child-to-parent or child-to-batch reference is tenant-safe. Use a
composite foreign key such as `(parent_id, house_id)` to a matching unique key;
a separate `house_id` foreign key does not prevent a cross-house combination.

### 3. Contracts

`BatchStatistics.schemaVersion` is `1`. The response contains `batchId`,
`houseName`, `batchCode`, UTC `calculatedAt`, the four legacy totals, and the
fixed metrics in orders 10 through 280:

```text
MATING_DATE, MATED_DOE_COUNT, CONCEPTION_RATE, DOE_BUCK_RATIO,
PREGNANT_DOE_COUNT, ABORTION_RATE, DELIVERED_LITTER_COUNT,
TOTAL_KIT_COUNT, AVERAGE_KITS_PER_LITTER, LIVE_KIT_COUNT,
LIVE_BIRTH_RATE, KEPT_LITTER_COUNT, KEPT_KIT_COUNT, KEPT_LIVE_RATE,
AVERAGE_KEPT_PER_LITTER, WEANED_KIT_COUNT, AVERAGE_WEANING_WEIGHT,
WEANING_SURVIVAL_RATE, SOLD_RABBIT_COUNT, OUTBOUND_SURVIVAL_RATE,
SOLD_WEIGHT, AVERAGE_SOLD_WEIGHT, TOTAL_SALES_AMOUNT,
SALES_PRICE_PER_KG, SALES_PRICE_PER_RABBIT,
FULL_FEED_CONVERSION_RATIO, FATTENING_FEED_CONVERSION_RATIO,
CARCASS_YIELD_RATE
```

Each metric always carries `code`, `name`, `stage`, `stageName`, `order`,
`excelColumnName`, `valueType`, `unit`, `format`, `formula`, `status`, nullable
raw/date/display values, nullable numerator and denominator, components, and
ordered missing causes. Consumers may ignore additive unknown codes, but they
must reject missing fixed metrics, changed version-1 metadata, unknown statuses
or value types, invalid nullable shapes, non-UTC `calculatedAt`, and non-finite
numbers.

The statuses are `AVAILABLE`, `NOT_APPLICABLE`, `NOT_RECORDED`, and
`DATA_MISSING`; precedence is `DATA_MISSING > NOT_RECORDED > NOT_APPLICABLE >
AVAILABLE`. Zero is available when it is a valid count. A zero denominator is
`NOT_APPLICABLE`. Negative finishing gain is `DATA_MISSING` with
`INVALID_FATTENING_GAIN`. Only mating date and carcass yield use
`NOT_RECORDED`.

Calculate with `BigDecimal` at raw precision and format with `HALF_UP`.
Allocations use two decimal kg for feed, three decimal kg for weights, two
decimals for unit price and money, and six decimals in the 0-1 range for
carcass yield. A missing immutable source suppresses every dependent aggregate;
never return a partial total.

Mating queries use the persisted Chinese result `怀孕`. `PREGNANT_DOE_COUNT`
deduplicates `mother_rabbit_id`; conception and abortion rates deduplicate
cycles. Feed windows start at the earliest mating date and use a half-open end
boundary: the day after a completed batch `end_date`, or query time for an
active batch.

Outbound confirmation is server-authoritative. `PUT` persists sale fields,
frozen rabbit items, and `batchAllocations`; final submit must flush the draft,
compare the submitted values with the locked snapshot, then create sales and
departures from the persisted values. Store the logical `requestId` before
sending, retain it across unknown results, and query request status before
starting a new operation.

Legacy payloads are controlled by
`app.batch-statistics.legacy-write-enabled`. While enabled, incomplete writes
preserve the old operation without inventing snapshots and emit one of:

```text
LEGACY_FEED_ALLOCATION_GAP
LEGACY_WEANING_WEIGHT_GAP
LEGACY_SALE_ALLOCATION_GAP
LEGACY_SALE_PRICE_GAP
LEGACY_REPLACEMENT_WEIGHT_GAP
```

The event shares the parent transaction and stores `clientBuild`, using
`UNKNOWN` when `X-App-Build` is absent or unavailable. When compatibility is
disabled, reject incomplete legacy payloads before durable request claim or
business writes.

### 4. Validation & Error Matrix

| Condition | Required behavior |
| --- | --- |
| Missing or unauthorized `X-House-Id` | Deny before returning or mutating tenant data |
| Parent and child belong to different houses | Reject in service checks and composite database foreign keys |
| Feed unit is not case-normalized kg | `400`; do not convert it implicitly |
| Allocation row is invalid, duplicated, or does not total the parent amount | `400` or domain conflict; no partial writes |
| Same `requestId`, different normalized payload | Idempotency conflict |
| Same logical payload after unknown result | Replay or recover the existing operation |
| Historical snapshot is absent | Dependent metric is `DATA_MISSING`; do not infer from current state |
| Denominator is zero | `NOT_APPLICABLE` with `ZERO_DENOMINATOR` |
| Finishing gain is negative | `DATA_MISSING` with `INVALID_FATTENING_GAIN` |
| Carcass yield is absent | `NOT_RECORDED` with `CARCASS_YIELD_NOT_RECORDED` |
| Compatibility is disabled and a legacy snapshot is missing | Upgrade conflict before request claim, gap event, or parent write |
| XLSX request lacks export permission or owns another house's batch | Reject before streaming bytes |

### 5. Good / Base / Bad Cases

- Good: a mixed-batch outbound draft stores every actual group weight and one
  positive `unitPricePerKg`; final submit reuses those persisted fields and
  allocates rounded money deterministically.
- Base: an old sale has batch membership but no batch weight snapshot. Rabbit
  count can remain available when independently supported, while weight- and
  amount-dependent metrics return the ordered missing causes.
- Bad: deriving a replacement's source batch or measured weight from its
  current rabbit record after conversion. The write-time allocation is the only
  valid statistics source.
- Bad: adding a child `house_id` column and a separate batch foreign key. This
  still permits a child from house A to reference a batch in house B.

### 6. Tests Required

- `BatchStatisticsServiceTest`: all 28 exact values, metadata, order, formula,
  precision, status precedence, repeated pregnancy cycles, zero and negative
  denominators, and the four legacy totals.
- `BatchStatisticsIT`: house-scoped mapper behavior, Chinese pregnancy result,
  half-open feed window, historical missing snapshots, and empty batches.
- `BatchStatisticsWritePathIT` and `OutboundDraftAllocationIT`: V55/V56
  constraints, tenant-safe foreign keys, exact allocations, authoritative
  drafts, retries, conflicts, and rollback atomicity.
- `BatchStatisticsLegacyWriteDisabledIT`: pre-claim rejection with zero durable
  writes when compatibility is disabled.
- `BatchStatisticsWorkbookWriterTest` and `BatchStatisticsExportIT`: exactly
  two visible sheets, 28 ordered cells/rows, numeric and status cell types,
  filenames, permissions, and house isolation.
- Admin and Flutter contract tests: independently frozen version-1 metadata,
  unknown additive code tolerance, strict fixed metric validation, permission
  visibility, stable request IDs, draft restoration, and protected downloads.
- Visible clients: desktop/narrow/200% text checks for the fixed 16-row layout;
  row 14 contains three independent sales metrics.

Run MySQL tests on a fresh schema. A reused schema with an edited, unapplied
migration checksum is not valid evidence.

### 7. Wrong vs Correct

#### Wrong

```text
client total -> infer each batch weight from rabbit count/current weight
child.house_id FK + child.batch_id FK -> assume the pair is tenant-safe
final submit body -> create sales without loading the persisted draft
```

#### Correct

```text
measured batch weights -> persist immutable allocation -> calculate metrics
(batch_id, house_id) -> composite FK -> batches(id, house_id)
locked persisted draft + frozen items -> compare submit -> create sales
```

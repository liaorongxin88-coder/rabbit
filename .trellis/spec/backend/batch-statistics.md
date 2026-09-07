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
boundary. For a completed batch, every feed predicate uses
`logged_at < DATE_ADD(DATE(end_date), INTERVAL 1 DAY)` so the complete natural
end day is included and the next midnight is excluded. An active batch uses
query time as its upper bound.

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

## Scenario: Shared real-data cross-client acceptance

### 1. Scope / Trigger

Use this scenario when changing the 28-metric query, workbook, Admin batch page,
Flutter batch page, or any immutable write source used by those consumers. It
proves one attachment-scale MySQL dataset across the API, XLSX, Admin, and a
physical Android device. Mocked client tests remain required for failure,
permission, narrow-screen, and text-scale boundaries.

### 2. Signatures

```text
backend/src/test/resources/fixtures/batch_statistics_acceptance_fixture.sql
backend/src/test/resources/fixtures/batch_statistics_acceptance_fixture_cleanup.sql

RABBIT_ANDROID_E2E_DEVICE_ID=<ready-device> \
  bash scripts/batch-statistics-cross-client-e2e.sh

pnpm --dir admin e2e:browser:batch-statistics:real
flutter drive \
  --driver=test_driver/android_e2e_driver.dart \
  --target=integration_test/batches/statistics_android_test.dart \
  --flavor=dev \
  --dart-define-from-file=<0600-runtime-file>
```

### 3. Contracts

The SQL fixture is the only executable definition of the populated dataset. A
20-character lowercase hex `run_id` owns the user, target/isolation houses,
batches, cycles, litters, allocations, sale rows, and carcass version. Its JSON
manifest returns IDs and a credential profile, never a password or token.

The runner requires Flyway V56 or newer and one ready portrait Android device.
It rebuilds the current backend with captcha disabled, LAN binding, and the one
random Vite origin appended to the existing CORS list. It must capture and
restore the prior running state, captcha setting, bind address, CORS list,
rotation, and stay-awake setting. Docker Compose and podman-compose must be
resolved without hard-coded container names.

Client credentials live in one mode-0600 temporary JSON file. Admin reads its
fixture fields through `RABBIT_E2E_DEFINES_FILE`; Flutter receives the same file
through `--dart-define-from-file`. Login bodies and bearer headers go to curl
through stdin, and the MySQL password goes through process environment. The
runtime file is deleted on success and every catchable exit path.

Artifacts are written under
`artifacts/batch-statistics-cross-client/<run_id>/`. Keep the API response,
validation proof, real workbook and headers, database assertions, Admin and
Android logs, one desktop image, eight group images, action images,
`manifest.json`, and `SHA256SUMS`. Do not keep tokens, passwords, Docker secret
environment, or screenshot byte arrays inside the Android result JSON.

Admin must select the target house through the real workspace selector before
opening the batch. Direct localStorage replacement races `WorkspaceProvider`
and can be overwritten by its first house load. The live workspace may create
`reminder_preferences` rows; cleanup owns those rows along with the static
fixture rows.

### 4. Validation & Error Matrix

| Condition | Required behavior |
| --- | --- |
| Flyway version is below 56 or not numeric | Stop before loading the fixture |
| Captcha endpoint is not business code `501` during the run | Stop before login or UI execution |
| Temporary Admin Origin is absent from CORS | Fail the real browser login; do not bypass CORS |
| Fixture manifest has the wrong run, profile, IDs, or scale totals | Stop before API and clients |
| Any API metric is missing, reordered, unavailable, or numerically different | Stop before launching either client |
| XLSX is JSON, empty, wrong MIME, wrong filename, or not a valid ZIP | Stop before launching either client |
| Admin uses the isolation/default house | Fail the `X-House-Id` assertion |
| Android wait exceeds its explicit deadline | Fail with visible text; never use default ten-minute `pumpAndSettle` waits |
| Client, redaction, or log pipeline stage fails | Fail the run even if the client process returned zero |
| Cleanup or backend/device restoration fails | Turn an otherwise successful run into failure |
| `RABBIT_BATCH_STATISTICS_KEEP_FIXTURE=1` | Keep rows only for debugging and print a password-free cleanup command |

### 5. Good / Base / Bad Cases

- Good: one run-scoped fixture feeds the API precheck, workbook, Admin, and
  Android; every layer reports the same 28 values before cleanup.
- Base: a client assertion fails. Capture available database evidence, delete
  fixture and runtime-created reminder preferences, restore the environment,
  and keep a failed manifest.
- Bad: point Admin at intercepted JSON while calling the run a backend test.
- Bad: write the target house to localStorage after login and navigate before
  `WorkspaceProvider` finishes loading.
- Bad: terminate only the pnpm parent process; start Vite through its Node entry
  so the owned child can be awaited and stopped.

### 6. Tests Required

- Fresh-schema `BatchStatisticsIT` and `BatchStatisticsExportIT`: all 28 exact
  API values, two workbook sheets, formats, filenames, permissions, and tenant
  isolation from the shared SQL fixture.
- Admin real-browser test: captcha fallback, business login, real house
  selection, proxied CORS, 28 display values, eight groups, `X-House-Id`,
  workbook download, overflow, console errors, and Vite process cleanup.
- Android physical-device test: bounded waits, 28 display values, eight group
  screenshots, export entrance, carcass form/history, and no fixture mutation.
- Runner postconditions: zero fixture user/house/batch/reminder residue, restored
  captcha/bind/CORS/device state, no runtime credential file or Vite listener,
  no secret patterns in text artifacts, and valid hashes for every artifact.
- Keep the mocked Admin browser suite and focused Flutter widget/repository
  suites; the populated happy path does not replace their boundary coverage.

### 7. Wrong vs Correct

#### Wrong

```text
independent synthetic payloads -> API, Admin, Flutter, and XLSX all "pass"
random Vite port -> unchanged backend CORS -> Invalid CORS request
pumpAndSettle() -> continuous frame scheduling -> ten-minute device-test stall
static fixture cleanup only -> runtime reminder_preferences -> FK failure
```

#### Correct

```text
one run-scoped MySQL fixture -> exact API -> XLSX + Admin + Android
selected Vite origin -> temporary exact CORS entry -> restore original list
bounded frame pumps + condition waits -> deterministic Android deadline
fixture rows + runtime-owned rows -> FK-ordered cleanup -> zero residue
```

## Scenario: Complex cross-client interaction matrix

### 1. Scope / Trigger

Use this scenario after a change can alter interactions among metric grain,
missing-source propagation, cross-batch allocation, completed-batch time
windows, permissions, or idempotent writes. It extends the attachment-scale
all-available fixture; it does not replace that baseline and is not a load or
capacity test.

The matrix must run through the real API, XLSX writer, Admin, and a physical
Android device. Keep expected values independent of production responses and
workbooks. A reproducible formula or authorization mismatch is a product defect;
do not edit the frozen expectation to make the test pass.

### 2. Signatures

```text
backend/src/test/resources/fixtures/batch_statistics_complex_matrix_fixture.sql
backend/src/test/resources/fixtures/batch_statistics_complex_matrix_cleanup.sql
backend/src/test/resources/fixtures/batch_statistics_complex_matrix.json

BatchStatisticsComplexMatrixIT
BatchStatisticsComplexMatrixExportIT

RABBIT_BATCH_STATISTICS_SUITE=complex \
RABBIT_ANDROID_E2E_DEVICE_ID=<ready-device> \
  bash scripts/batch-statistics-cross-client-e2e.sh
```

The mode-0600 client define file uses scalar values only:

```text
RABBIT_E2E_SUITE=complex
RABBIT_E2E_SCENARIOS_JSON=<JSON string>
RABBIT_E2E_USERS_JSON=<JSON string>
```

User entries use the canonical `userName` field. Do not add a parallel
`username` alias for one client.

### 3. Contracts

One run owns one target house, one isolation house, five primary batches, one
same-house rounding-support batch, and exactly three users: `OWNER`,
`READ_ONLY`, and `UNRELATED_HOUSE`. Resolve cleanup ownership from the run ID,
fixture actor IDs, and owned house IDs; a colliding `request_id` from another
actor must survive cleanup.

The independent catalog contains six ordered scenarios and exactly 28 metrics
per scenario. Each expected metric fixes its code, order, nullable numeric/date
raw value, nullable backend `displayValue`, status, and ordered missing causes.
Clients also record `visibleValue`: it equals `displayValue` for `AVAILABLE`
and otherwise equals the localized status text. This distinction prevents a
correct UI state such as `DATA_MISSING` from being compared with API
`displayValue: null`.

`E2eApiClient` enables Jackson `USE_BIG_DECIMAL_FOR_FLOATS`. Baseline and
complex fixtures must therefore store nonterminating calculated values as exact
`MathContext.DECIMAL128` decimal strings and compare them with
`BigDecimal.compareTo`. A 16-digit decimal copied from a `double` is not the raw
metric value, even when both values round to the same two-decimal display text.

The named scenarios are:

```text
complex-available
mixed-data-quality
mixed-batch-rounding
mixed-batch-rounding-support
security-and-retry
time-and-cycle-boundaries
```

Every completed-batch feed aggregate uses this exclusive upper bound:

```sql
feed.logged_at < DATE_ADD(DATE(batches.end_date), INTERVAL 1 DAY)
```

Do not use `DATE_ADD(batches.end_date, INTERVAL 1 DAY)`: if `end_date` contains
a non-midnight time, that expression leaks part of the next day into the
statistics window.

`READ_ONLY` preserves the production VIEWER contract: statistics query and
XLSX export are allowed; carcass-yield edit and full history are denied.
`security-and-retry` writes 58%, replays the same request ID and payload without
a second version, then submits 59% under that request ID and requires a conflict
with no extra write. `UNRELATED_HOUSE` cannot read target-house data.

Android screenshots must remain in `IntegrationTestWidgetsFlutterBinding`
report data until the driver has called `onScreenshot`. After standalone PNGs
exist, the root runner compares embedded screenshot names with
`screenshotNames`, removes the byte arrays from the result JSON, and then runs
the client validator. Removing `reportData.screenshots` inside the widget test
prevents the driver from writing any PNG.

The retained proof distinguishes workbook locations:

```text
scenarioWorkbookCount = 6   # direct API/XLSX validation
securityWorkbookCount = 1   # backend READ_ONLY export
adminWorkbookCount = 7      # six scenarios plus READ_ONLY export
```

A complex manifest records `scenarioValidations`, `security`, and `secretScan`.
A baseline manifest omits those complex-only validation fields instead of
writing empty objects or misleading `false` values.

### 4. Validation & Error Matrix

| Condition | Required behavior |
| --- | --- |
| Catalog is missing a scenario, metric, status, or cause | Stop before clients; never derive the missing expectation from API output |
| Expected ratio was copied from `double` output | Reject the truncated value; store and compare the exact DECIMAL128 result |
| Completed-batch feed occurs at end-day 23:59:59 | Include it |
| Feed occurs at the next natural day 00:00:00 | Exclude it |
| Mixed sale amount has a rounding remainder | Apply the production deterministic group order and conserve the order total |
| UI metric is unavailable | API `displayValue` remains null; client `visibleValue` is the localized status text |
| Same request ID replays the same 58% payload | Return the existing success and keep one version |
| Same request ID changes 58% to 59% | Return conflict and keep one version |
| VIEWER requests statistics or XLSX | Allow with the target `X-House-Id` |
| VIEWER requests carcass edit or full history | Deny in the backend and hide the client action |
| Unrelated-house user requests the target batch | Deny without rendering target data |
| Screenshot name is unsafe, missing, reordered, or has no PNG | Fail artifact validation |
| Android result still contains screenshot bytes after sanitization | Fail artifact validation |
| Cleanup leaves fixture users, houses, batches, dedup rows, or events | Fail the run after attempting environment restoration |
| Baseline suite is selected | Preserve the original single-scenario contract and omit complex-only manifest stages |

### 5. Good / Base / Bad Cases

- Good: load all six batches once, validate every API and workbook, traverse one
  Admin session and one Flutter installation, then validate database state and
  clean the run-owned rows.
- Base: `mixed-data-quality` combines valid values with `NOT_APPLICABLE`,
  `NOT_RECORDED`, and `DATA_MISSING`; each client shows every ordered cause
  without replacing null with numeric zero.
- Bad: use a global text finder for a cause that can appear under several
  expanded metrics. Scope the assertion to the selected metric detail.
- Bad: use `find.text('账号')` for repeated role login. Scope the account mode to
  `ValueKey('login-mode-selector')` because the account field has the same text.
- Bad: remove embedded screenshots immediately after `takeScreenshot`; the host
  driver has not received them yet.

### 6. Tests Required

- Run `BatchStatisticsComplexMatrixIT` and
  `BatchStatisticsComplexMatrixExportIT` on a fresh V56-or-newer schema. Assert
  six ordered scenarios, 28 metrics each, exact `BigDecimal` values, XLSX cell
  types and formats, collision-safe cleanup, VIEWER permissions, 58% replay,
  and 59% conflict. Keep the attachment-scale expectations at the same
  DECIMAL128 precision so `BatchStatisticsIT` and `BatchStatisticsExportIT`
  validate all 28 raw values after BigDecimal JSON decoding.
- Run the complex cross-client command on an unlocked physical device. Require
  six Admin scenario workbooks, one Admin VIEWER workbook, five full-page Admin
  scenario images, at least 40 Android group images, support-batch evidence,
  OWNER history/actions, READ_ONLY export bytes, and unrelated-house denial.
- Validate `metricDisplayValues`, `metricVisibleValues`, statuses, ordered causes,
  screenshot names, PNG existence, database topology, zero residue, environment
  restoration, secret scanning, and SHA-256 checksums.
- After complex passes, rerun the default baseline command, the mocked Admin
  browser suite, full Admin gates, full Flutter check, and full backend gates.

### 7. Wrong vs Correct

#### Wrong

```text
end_date timestamp + 1 day -> partial next-day feed included
16-digit double literal -> exact BigDecimal API value mismatch
API displayValue null == rendered UI text -> client evidence mismatch
takeScreenshot -> delete reportData screenshots -> driver has no PNG
passed baseline + security:false -> ambiguous manifest
```

#### Correct

```text
DATE(end_date) + 1 day -> exclusive next-midnight boundary
DECIMAL128 string + BigDecimal.compareTo -> exact raw-value evidence
nullable displayValue + explicit visibleValue -> exact API and UI evidence
takeScreenshot -> driver writes PNG -> verify names -> sanitize result JSON
passed baseline -> omit complex-only validation keys
```

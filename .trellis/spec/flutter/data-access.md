# Flutter data access

Repositories are concrete classes grouped under `app/lib/src/data/repositories/<business>/` and exposed through plain Riverpod `Provider`s. They receive the shared `ApiClient`; UI code must not construct a page-local Dio client. Workflow-heavy areas may define gateway interfaces for substitution, while most tests replace or subclass concrete repositories.

## Network contract

`app/lib/src/data/services/network/client.dart` owns base URL, timeouts, headers, envelope handling, and Dio error translation. Connect timeout is 10 seconds; send and receive timeouts are 30 seconds. Its API `get`, `post`, `postMultipart`, `put`, and `delete` methods read the current session, send a bearer token when authenticated, and add `X-House-Id` for house-scoped calls. `downloadProtected` is the authenticated binary path: it sends the same bearer and house headers, preserves `Content-Type` and `Content-Disposition`, converts JSON error envelopes into `ApiException`, and removes partial files on failure. The older public `download` path remains appropriate only for unauthenticated artifacts such as OTA packages.

An API response must be a map. A nonzero business `code` becomes `ApiException`, even with HTTP success. HTTP or business 401 invalidates the session. A 403 invalidates it only when the trimmed message equals `账号已停用`. Timeout, connectivity, cancellation, and HTTP status failures have project-owned Chinese messages. Bad-certificate and unknown Dio failures return Dio's `error.message` when it is present, so do not claim every network failure is normalized or user-safe.

Flutter currently accepts a missing `code` as success, unlike admin. This is a compatibility gap recorded in [Rabbit cross-application contracts](../guides/rabbit-cross-application-contracts.md), not a rule to spread to another client.

## Decoding and pagination

Repository decoders use `requireJsonObject` and `requireJsonObjectList`, then domain `fromJson` factories. There is no separate DTO layer. Parsing strictness is contract-specific: `Rabbit.fromJson` accepts numeric strings and legacy values, while auth parsing requires valid tokens and identity.

Two decoder limits are current behavior. `_request` catches `DioException`, not arbitrary decoder errors, so `FormatException` or a type error may escape. `requireJsonObjectList` discards non-map list members through `whereType<Map>()`. Do not claim malformed payloads always become `ApiException`.

Follow each endpoint's pagination shape. `app/lib/src/data/repositories/rabbits/repository.dart` requests pages of 200 until a short page. Cursor endpoints keep cursors opaque. Do not add a generic adapter that guesses field names.

## Writes

Writes needing idempotency create a UUID request ID. Keep the same ID for an unchanged retry. Repository methods that generate an ID when none is supplied are convenient for one attempt; retrying UI must pass and retain its own stable ID. Multi-step outbound submission persists its request ID before sending and distinguishes confirmed failure from an unknown result that may have reached the server. Read [Persistence](./persistence.md) before changing that lifecycle.

Every request sends `X-App-Build`. If the platform version lookup fails, send the literal `UNKNOWN`; omitting the header makes legacy-write telemetry ambiguous. The header is diagnostic metadata, never an authorization signal.

Repository contract tests use custom Dio adapters to assert URL, headers, query parameters, bodies, pagination, and decoding. Add those assertions when changing a wire contract.

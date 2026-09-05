# Flutter persistence

Rabbit uses `flutter_secure_storage` and `shared_preferences`. It has no local database and no general offline entity cache.

## Stored values

Session tokens belong in secure storage. User ID, display name, current house, theme, and start route use shared preferences. Never put a token or other secret in shared preferences.

House selection is stored per user with a legacy global key as fallback. A restored selection remains a preference until the accessible-house response confirms it. `app/lib/src/data/services/auth/session.dart` coalesces concurrent first reads and memoizes the session snapshot; after that, save and clear operations replace the in-memory source of truth.

Local settings update Riverpod before awaiting the preference write. A storage failure can leave optimistic state visible until the next restore. This is current behavior, not proof that every local write is durable before UI update.

Network data such as houses, rabbits, batches, and reports remains server-owned. Riverpod caching is process-local. Do not add ordinary offline caching without explicit freshness, conflict, and ownership rules.

## Outbound drafts

`app/lib/src/data/services/storage/outbound.dart` stores drafts and pending request IDs by user and house. It serializes writes within that scope and deletes corrupt snapshots. Startup checks pending server status, then selects a server task, a newer matching local revision, or an offline fallback.

Preserve the stored request ID across an unknown result. Clearing or rotating it too early can turn one logical write into two server operations.

The local outbound snapshot includes `batchAllocationWeights` and a marker that distinguishes a current snapshot from a legacy snapshot that never stored the field. The server task remains authoritative for frozen rabbit membership, sale fields, `unitPricePerKg`, and batch allocations. While the confirmation form is editable, persist every meaningful change to both local and server drafts. Before final submit, cancel any debounce, force-save the latest draft, recapture the acknowledged task revision and values, then assign or reuse the submission request ID. An immediate tap after editing must not race an older server snapshot.

When selected rabbits or their source groups change, clear incompatible weights and the derived total. Do not silently reuse a measured group weight for a different membership set. Empty or partial allocations may be saved in `WAITING_CONFIRMATION`; completeness, exact totals, positive common price, and membership drift are enforced atomically at final submit.

## NFC queues

`app/lib/src/data/services/storage/nfc.dart` stores a write session, a list of pending bindings, and one pending launch event. Failed bindings remain queued. Business code `4606` becomes a conflict that can be retried or force-replaced with a new UUID. The app retries binding synchronization every 30 seconds and on resume.

Corrupt-data handling is limited. The NFC read methods return an empty value for a `FormatException`, but they do not remove the stored value, and unexpected decoded shapes or type errors may escape. The app also clears the single pending launch event after either successful routing or a processing error; this differs from the retryable binding queue.

Do not discard a pending binding merely because one sync attempt fails. Test serialization, malformed stored values, scope isolation, conflict transitions, and restart recovery when changing these stores.

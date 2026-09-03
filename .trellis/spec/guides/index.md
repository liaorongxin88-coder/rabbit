# Shared Engineering Guides

These guides cover decisions that span more than one Rabbit application. Layer-specific conventions remain under `backend/`, `admin/`, and `flutter/`.

## Pre-Development Checklist

Read the relevant guide before changing code:

- [Code Reuse](./code-reuse-thinking-guide.md) when adding helpers, constants, domain mappings, request wrappers, or similar behavior that may already exist.
- [Cross-Layer Changes](./cross-layer-thinking-guide.md) when a change crosses HTTP, service, persistence, or client boundaries.
- [Rabbit Cross-Application Contracts](./rabbit-cross-application-contracts.md) for authentication, permissions, house scope, API envelopes, idempotency, pagination, or date/time behavior.

## Guide Index

| Guide | Use it for |
| --- | --- |
| [Code Reuse](./code-reuse-thinking-guide.md) | Finding existing ownership before adding another helper or mapping |
| [Cross-Layer Changes](./cross-layer-thinking-guide.md) | Tracing a contract through backend, admin, and Flutter layers |
| [Rabbit Cross-Application Contracts](./rabbit-cross-application-contracts.md) | Preserving Rabbit's shared security and API semantics |

## Quality Check

- Confirm shared behavior has one owner within each application.
- Compare changed backend DTOs, enums, and response shapes with both client consumers.
- Verify authorization at the backend boundary even when a client hides a control.
- Run the checks required by every affected application, not only the layer where editing started.
- Add focused integration or contract coverage for authentication, permissions, tenant isolation, idempotency, and API shape changes.

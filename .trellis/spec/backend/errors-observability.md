# Errors and observability

Business failures use `BizException(code, message)` and JSON responses use `ApiResponse<T>`, both under `backend/rabbit-platform/src/main/java/com/rabbit/app/common/`. Success is `code = 0` with message `ok`.

`backend/rabbit-platform/src/main/java/com/rabbit/app/common/GlobalExceptionHandler.java` maps business exceptions, malformed JSON, bean-validation failures, missing inputs, type mismatches, and uncaught exceptions into the envelope. The handler returns objects rather than `ResponseEntity`, so envelope failures normally retain HTTP 200. Preserve this client contract unless a coordinated API migration changes both consumers.

Infrastructure failures do not all have dedicated mappings. Persistence errors, upload-size errors, non-MVC constraint failures, and asynchronous stream errors can reach the generic 500 path. Do not document or depend on a specific business code unless the handler actually defines it. Raw downloads and CSV streams remain outside the JSON envelope.

## Trace and audit

`backend/rabbit-platform/src/main/java/com/rabbit/app/common/TraceIdFilter.java` accepts or creates `X-Trace-Id`, puts it in MDC and a request attribute, returns it in the response, and clears MDC in `finally`. Use that request trace for correlation instead of creating another request ID for logging.

`backend/rabbit-reporting/src/main/java/com/rabbit/app/modules/audit/support/AuditLogInterceptor.java` records trace, actor, house and resource coordinates, route data, transport status, envelope result, duration, client IP, and user agent. `backend/rabbit-platform/src/main/java/com/rabbit/app/common/ApiResponseAuditAdvice.java` publishes the envelope fields as request attributes. These attribute names form a cross-module string contract covered by `backend/rabbit-platform/src/test/java/com/rabbit/app/common/ApiResponseAuditAdviceTest.java` and reporting audit tests.

`backend/rabbit-reporting/src/main/java/com/rabbit/app/modules/audit/service/AuditLogService.java` intentionally swallows audit persistence failures so a logging outage does not fail the user request. It currently emits no failure log. This is a known exception, not permission to suppress unrelated errors silently.

## Operation tracking

Operation tracking is split by ownership: access defines annotations, contexts, aspects, and stamping; production persists and reads operation events; reporting owns legacy HTTP audit logs. `docs/backend/modules/operation-tracking.md` describes the flow.

For `@TrackedOperation`:

- Keep the annotation on a Spring-proxied public service boundary.
- Match its `code` to the established operation key.
- Do not self-invoke the annotated method.
- Do not combine aspect-managed deduplication with manual deduplication in the same operation.

`TrackedOperationPlacementTest.java` and `FarmingModuleArchitectureTest.java` enforce placement and self-invocation constraints. Add focused aspect and event tests when changing context propagation or the audit attribute contract.

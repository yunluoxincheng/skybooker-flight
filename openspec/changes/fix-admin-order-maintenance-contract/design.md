## Context

Admin order maintenance already exists in a partial form: `AdminController` exposes order listing, detail, create, refund, change, void, note, refund-record, and change-record endpoints. The current implementation reuses core order/refund/change services for important state transitions, which is the right direction, but the contract is incomplete for issue #115:

- order list accepts only `page` and `size`, so frontend filters are ignored;
- frontend-required admin helper endpoints for detail, change options, passengers, and seats return 404;
- admin order creation only accepts `targetUserId`, while the frontend has submitted `userId`;
- `DELETE /api/admin/orders/{id}?type=delete` has no supported semantics;
- enhanced admin detail needs a dedicated response shape rather than overloading the user-facing `OrderVO`.

The repository rules keep this backend-only. Frontend files remain out of scope.

## Goals / Non-Goals

**Goals:**

- Complete the admin order maintenance backend contract required by issue #115.
- Preserve existing order, refund, change, waitlist, seat inventory, and void state-machine behavior.
- Return standard API envelopes and validation/business errors for invalid admin calls.
- Keep all new admin reads and writes protected by the existing `/api/admin/**` ADMIN portal rule.
- Add focused integration tests for the new/changed admin endpoints and DTO contract.

**Non-Goals:**

- No hard deletion of ticket orders.
- No frontend changes.
- No new payment subsystem or payment-record table.
- No free-form editing of arbitrary order fields, passenger identities, or seats outside the existing refund/change/void/admin-note workflows.
- No relaxation of change candidate route, sellability, seat availability, or ownership rules.

## Decisions

### Reuse Core State Transitions

Admin create, refund, change, and void will continue to call existing core services or mapper-level guarded transitions instead of duplicating seat and inventory logic. This preserves concurrency behavior around locks, sold seats, remaining-seat counts, refund release, waitlist fulfillment, and change old-seat/new-seat ordering.

Alternative considered: implement separate admin-specific order mutation logic. Rejected because it would fork the state machine and increase the risk of inventory drift.

### Dedicated Admin Detail VO

Add a dedicated admin detail response model, such as `AdminOrderDetailVO`, for `GET /api/admin/orders/{id}/detail`. It should include the base order data, passenger snapshots, refund records, change records, and a machine-readable timeline. The existing `GET /api/admin/orders/{id}` remains compatible and can either return the legacy `OrderVO` or delegate to the enhanced detail only if doing so does not break current consumers.

Alternative considered: add refund/change/timeline fields directly to `OrderVO`. Rejected because `OrderVO` is shared by user-facing order APIs and should not absorb admin-only aggregation.

### Canonical `targetUserId` With `userId` Alias

`AdminCreateOrderDTO` will keep `targetUserId` as the documented canonical field and add `userId` as a compatibility alias. Service validation resolves one effective target user ID. If both fields are present and differ, the request fails with validation error.

Alternative considered: force frontend to migrate before backend fix. Rejected because issue #115 is about stabilizing the backend contract for the existing admin UI integration.

### Delete Endpoint Means Guarded Void, Not Hard Delete

`DELETE /api/admin/orders/{id}?type=delete` will not physically delete order rows. It will route to the same protected terminal semantics as voiding: only `CANCELLED` or `REFUNDED` can become `VOIDED`; no seat, flight, refund, change, or payment data is removed or mutated. A reason is still required through either request body or query parameter, and unsupported `type` values are rejected.

Alternative considered: hard delete terminal orders. Rejected because historical orders are referenced by refund/change/passenger/audit flows and hard deletion would weaken traceability.

### Admin Helper Reads Wrap Existing Read Models

The new helper endpoints reuse existing mapper/service reads:

- `GET /api/admin/passengers?userId=...` validates the target is an ordinary user and returns that user's passenger list.
- `GET /api/admin/flights/{flightId}/seats` validates the flight exists and returns the seat map regardless of publish status when appropriate for admin maintenance.
- `GET /api/admin/orders/{id}/change-options` reuses ordinary change candidate rules for route, sellability, cutoff, and remaining seats. Cutoff override remains only on submit-change via `force`.

Alternative considered: have the frontend call user/public endpoints. Rejected because user endpoints enforce current-user ownership and public seat endpoints may not match admin maintenance needs.

## Risks / Trade-offs

- [Risk] Adding many optional order-list filters can produce mismatched `records` and `total` queries. -> Mitigation: factor a single MyBatis `<sql>` where clause shared by list and count tests.
- [Risk] Enhanced detail aggregation can introduce N+1 query growth. -> Mitigation: fetch base order, refund records, and change records in bounded queries by order ID; avoid per-passenger record loops.
- [Risk] `DELETE` with request body may be awkward for some clients. -> Mitigation: accept `reason` from a small DTO body and optionally from a query parameter, with documented precedence.
- [Risk] Alias fields can hide frontend drift. -> Mitigation: keep `targetUserId` canonical in docs and tests, and only allow `userId` as an explicitly tested compatibility path.
- [Risk] Admin change-options without `force` may omit flights an operator could force-change into later. -> Mitigation: the confirmed decision is to keep cutoff bypass only at submit time; the endpoint remains predictable and aligned with normal candidates.

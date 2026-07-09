## Why

Issue #115 shows that admin order maintenance is not yet a complete backend contract: several frontend-required admin endpoints return 404, order list filters are ignored, and create/change DTOs do not fully match the agreed admin workflow. This blocks stable implementation of the admin order management UI and risks inconsistent order, refund, change, and void behavior.

## What Changes

- Extend `GET /api/admin/orders` with effective filtering for status, order number, user, flight, and departure-date fields, keeping paginated `records` and `total` consistent.
- Add an enhanced admin order detail contract at `GET /api/admin/orders/{id}/detail` while preserving `GET /api/admin/orders/{id}` compatibility.
- Introduce a dedicated admin detail response model that aggregates order summary, passenger snapshots, refund records, change records, and a status timeline.
- Make admin order creation accept `targetUserId` as the canonical field and `userId` as a backward-compatible alias; reject requests where both are present and differ.
- Add admin read helpers for order maintenance workflows:
  - `GET /api/admin/orders/{id}/change-options`
  - `GET /api/admin/passengers?userId=...`
  - `GET /api/admin/flights/{flightId}/seats`
- Keep admin change-option listing aligned with ordinary change candidate rules; cutoff bypass remains limited to the existing submit-change `force` flag.
- Add a protected delete endpoint shape for frontend compatibility without hard deletion: `DELETE /api/admin/orders/{id}?type=delete` SHALL route to the guarded void semantics and never physically remove orders.
- Clarify validation behavior for missing reasons, illegal states, illegal status filters, and incompatible request aliases so invalid frontend calls return business or validation errors rather than 500.
- Update backend tests and API documentation for the completed admin order maintenance contract.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `admin-backoffice-mvp`: expands admin order reads and maintenance helpers, formalizes admin order DTO alias compatibility, effective filtering, enhanced details, and no-hard-delete semantics.

## Impact

- Backend API controllers and services under `backend/src/main/java/com/skybooker/admin/`.
- Order, passenger, flight, refund, and change mapper/service reuse points under `backend/src/main/java/com/skybooker/{order,passenger,flight,refund,change}/`.
- MyBatis XML for admin order list filtering and detail aggregation as needed.
- Backend integration tests under `backend/src/test/java/com/skybooker/admin/`.
- API/spec documentation in `openspec/specs/admin-backoffice-mvp/spec.md` delta and likely `docs/07_API_DESIGN.md`.
- No frontend file changes in this issue; frontend integration remains a follow-up consumer of the backend contract.

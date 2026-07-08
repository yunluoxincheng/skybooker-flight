## 1. Contracts and Validation

- [x] 1.1 Add admin order list query DTO or request parameters for status, orderNo, userId, userKeyword, flightNo, flightKeyword, departureDateStart, departureDateEnd, page, and size.
- [x] 1.2 Add validation for supported order statuses, date formats, positive pagination, required helper parameters, and unsupported delete `type` values.
- [x] 1.3 Update `AdminCreateOrderDTO` to keep `targetUserId` canonical while accepting `userId` as a compatibility alias and rejecting conflicting values.
- [x] 1.4 Add dedicated admin response models for enhanced order detail and status timeline without expanding the shared user-facing `OrderVO`.

## 2. Data Access

- [x] 2.1 Add filtered admin order list and count mapper methods that share one MyBatis where clause.
- [x] 2.2 Add or reuse mapper reads for enhanced order detail aggregation: base order detail, order passengers, refund records, change records, and timeline source fields.
- [x] 2.3 Ensure admin flight-seat and passenger-helper reads validate existence/ordinary-user boundaries and reuse existing `FlightSeatVO` and `PassengerVO` shapes where appropriate.

## 3. Service Layer

- [x] 3.1 Extend admin order list service to apply all supported filters and return consistent pagination totals.
- [x] 3.2 Implement enhanced admin order detail assembly with base order, passengers, refund records, change records, admin note, and timeline.
- [x] 3.3 Implement admin change-options service that can read any existing order as admin while preserving ordinary candidate rules and excluding submit-time force behavior.
- [x] 3.4 Implement admin passenger lookup for ordinary users only.
- [x] 3.5 Implement admin flight seat lookup for existing flights.
- [x] 3.6 Implement `DELETE /api/admin/orders/{id}?type=delete` service path as guarded void/no-hard-delete semantics with required reason and audit logging.

## 4. Controller and Documentation

- [x] 4.1 Add controller routes for `/api/admin/orders/{id}/detail`, `/api/admin/orders/{id}/change-options`, `/api/admin/passengers`, `/api/admin/flights/{flightId}/seats`, and `DELETE /api/admin/orders/{id}`.
- [x] 4.2 Preserve existing admin order endpoints and response envelopes.
- [x] 4.3 Update `docs/07_API_DESIGN.md` to document filters, helper endpoints, enhanced detail, `userId` alias compatibility, and no-hard-delete delete semantics.

## 5. Tests

- [x] 5.1 Add integration tests proving admin order status/orderNo filters affect both records and total, including invalid status rejection.
- [x] 5.2 Add integration tests for user, flight, and departure-date order filters.
- [x] 5.3 Add integration tests for enhanced order detail success and not-found behavior.
- [x] 5.4 Add integration tests for admin change-options, passenger lookup, and flight-seat lookup, including authorization boundaries.
- [x] 5.5 Add integration tests for admin create order using `userId`, using `targetUserId`, and rejecting conflicting aliases without inventory changes.
- [x] 5.6 Add integration tests for `DELETE /api/admin/orders/{id}?type=delete` proving VOIDED transition only for voidable states, no physical deletion, no inventory mutation, required reason, unsupported type rejection, and audit behavior.

## 6. Verification

- [x] 6.1 Run `openspec validate fix-admin-order-maintenance-contract --no-color`.
- [x] 6.2 Run focused backend admin tests.
- [x] 6.3 Run `cd backend && mvn test` or document any environment blocker.

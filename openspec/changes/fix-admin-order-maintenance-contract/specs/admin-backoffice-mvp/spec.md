## MODIFIED Requirements

### Requirement: Admin order reads
The system SHALL allow authenticated administrators to list, filter, and view ticket orders, including enhanced maintenance detail and read helpers needed by the admin order workflow.

#### Scenario: Admin order list succeeds
- **WHEN** a valid admin portal token calls `GET /api/admin/orders`
- **THEN** the system MUST return a paginated list of orders with user, flight, amount, and status information.

#### Scenario: Admin order list filters by status and order number
- **WHEN** a valid admin portal token calls `GET /api/admin/orders` with `status` and/or `orderNo`
- **THEN** the system MUST filter returned records and pagination total using those conditions, and every returned order MUST match the requested status and/or order-number condition.

#### Scenario: Admin order list filters by user, flight, and date
- **WHEN** a valid admin portal token calls `GET /api/admin/orders` with user, flight, or departure-date filter parameters such as `userId`, `userKeyword`, `flightNo`, `flightKeyword`, `departureDateStart`, or `departureDateEnd`
- **THEN** the system MUST apply those filters to both returned records and pagination total without silently ignoring supported parameters.

#### Scenario: Admin order list rejects invalid status
- **WHEN** a valid admin portal token calls `GET /api/admin/orders` with an unsupported order status value
- **THEN** the system MUST reject the request with a validation error and MUST NOT return an unfiltered order list.

#### Scenario: Admin legacy order detail succeeds
- **WHEN** a valid admin portal token calls `GET /api/admin/orders/{id}` for an existing order
- **THEN** the system MUST return the order detail including passenger snapshots and seat information.

#### Scenario: Admin enhanced order detail succeeds
- **WHEN** a valid admin portal token calls `GET /api/admin/orders/{id}/detail` for an existing order
- **THEN** the system MUST return a dedicated admin detail response containing the base order data, passenger snapshots, refund records, change records, admin note, and a machine-readable status timeline.

#### Scenario: Admin enhanced order detail rejects missing order
- **WHEN** a valid admin portal token calls `GET /api/admin/orders/{id}/detail` for a non-existent order
- **THEN** the system MUST return the standard resource-not-found response.

#### Scenario: Admin reads change options for an order
- **WHEN** a valid admin portal token calls `GET /api/admin/orders/{id}/change-options` for an existing order
- **THEN** the system MUST return change candidate flights using the same route, sellability, cutoff, and remaining-seat rules as ordinary change-option lookup, and MUST NOT use the submit-change `force` override in this read endpoint.

#### Scenario: Admin reads passengers for a target user
- **WHEN** a valid admin portal token calls `GET /api/admin/passengers?userId={userId}` for an existing ordinary user
- **THEN** the system MUST return that user's passenger records in the standard API envelope.

#### Scenario: Admin passenger lookup protects admin accounts
- **WHEN** a valid admin portal token calls `GET /api/admin/passengers?userId={userId}` for an admin account or non-existent user
- **THEN** the system MUST reject the request without returning passenger data for that account.

#### Scenario: Admin reads seats for a flight
- **WHEN** a valid admin portal token calls `GET /api/admin/flights/{flightId}/seats` for an existing flight
- **THEN** the system MUST return that flight's seat map, including seat id, seat number, cabin class, seat type, price, status, and version in the standard API envelope.

#### Scenario: Non-admin cannot access admin order reads
- **WHEN** a valid USER portal token or an unauthenticated client calls any admin order read, enhanced detail, change-option, passenger-helper, or seat-helper endpoint
- **THEN** the system MUST reject the request with an authorization error.

### Requirement: Admin order maintenance
The system SHALL allow authenticated administrators to maintain ticket orders through dedicated admin endpoints covering order placement on behalf of a user, refund, ticket change, protected void/delete, admin-note update, and refund/change record reads. Every state-changing operation SHALL require the ADMIN portal role and write an admin operation audit entry; read operations SHALL require the ADMIN portal role but are not audited by this change.

#### Scenario: Admin places an order on behalf of a user
- **WHEN** a valid admin portal token submits a target userId, flightId, and passenger-seat items to the admin order-creation endpoint
- **THEN** the system MUST create the order following the admin-placed order creation rules, return the resulting order data, and write an admin operation audit entry.

#### Scenario: Admin order creation accepts userId compatibility alias
- **WHEN** a valid admin portal token submits `userId`, `flightId`, and passenger-seat items to the admin order-creation endpoint without `targetUserId`
- **THEN** the system MUST treat `userId` as the target ordinary user, create the order following the same admin-placed order creation rules, return the resulting order data, and write an admin operation audit entry.

#### Scenario: Admin order creation rejects conflicting user aliases
- **WHEN** a valid admin portal token submits both `targetUserId` and `userId` to the admin order-creation endpoint with different values
- **THEN** the system MUST reject the request with a validation error and MUST NOT create an order, lock seats, decrement inventory, or write a successful admin operation audit entry.

#### Scenario: Admin initiates refund
- **WHEN** a valid admin portal token submits a refund request with a required reason for an existing order through the admin refund endpoint
- **THEN** the system MUST apply the admin refund override rules, return the refund result, and write an admin operation audit entry capturing the action, the reason, and the acting admin.

#### Scenario: Admin refund rejects missing reason
- **WHEN** a valid admin portal token submits an admin refund request without a non-blank reason
- **THEN** the system MUST reject the request with a validation error and MUST NOT change order status, seat inventory, refund records, or admin operation audit rows.

#### Scenario: Admin initiates ticket change
- **WHEN** a valid admin portal token submits a change request with a required reason for an existing order through the admin change endpoint
- **THEN** the system MUST apply the admin ticket-change override rules, return the changed order data, and write an admin operation audit entry capturing the action, the reason, and the acting admin.

#### Scenario: Admin change rejects missing reason
- **WHEN** a valid admin portal token submits an admin change request without a non-blank reason
- **THEN** the system MUST reject the request with a validation error and MUST NOT change order status, seat inventory, change records, or admin operation audit rows.

#### Scenario: Admin voids an order
- **WHEN** a valid admin portal token submits a void request with a required reason for an order in a voidable terminal state through the admin void endpoint
- **THEN** the system MUST transition the order to VOIDED following the ticket order void transition rules, MUST NOT change seat or flight inventory, and MUST write an admin operation audit entry.

#### Scenario: Admin void rejects non-terminal order
- **WHEN** a valid admin portal token submits a void request for an order in PENDING_PAYMENT, ISSUED, CHANGED, CHANGE_PENDING, or another non-voidable state
- **THEN** the system MUST reject the request with the ORDER_NOT_VOIDABLE business error and MUST NOT change order status, seat, flight inventory, or write a void audit entry.

#### Scenario: Admin delete endpoint never hard deletes
- **WHEN** a valid admin portal token calls `DELETE /api/admin/orders/{id}?type=delete` with a required reason for an order in a voidable terminal state
- **THEN** the system MUST transition the order to VOIDED using the same guarded void semantics, MUST NOT physically delete the ticket order or related passenger, refund, change, seat, flight, or audit rows, and MUST write an admin operation audit entry.

#### Scenario: Admin delete endpoint rejects non-voidable order
- **WHEN** a valid admin portal token calls `DELETE /api/admin/orders/{id}?type=delete` for an order that is not in a voidable terminal state
- **THEN** the system MUST reject the request with the ORDER_NOT_VOIDABLE business error and MUST NOT delete or mutate order, seat, flight, refund, change, payment, or audit data.

#### Scenario: Admin delete endpoint rejects unsupported type
- **WHEN** a valid admin portal token calls `DELETE /api/admin/orders/{id}` with an unsupported `type` parameter
- **THEN** the system MUST reject the request with a validation error and MUST NOT delete or mutate order data.

#### Scenario: Admin updates order admin note
- **WHEN** a valid admin portal token submits an admin note update for an existing order
- **THEN** the system MUST update only ticket_order.admin_note, MUST NOT change any other order field, and MUST write an admin operation audit entry.

#### Scenario: Admin reads refund and change records for an order
- **WHEN** a valid admin portal token requests the refund history or change history for an existing order
- **THEN** the system MUST return the order's refund records and change records linked by order id.

#### Scenario: Non-admin cannot access admin order maintenance
- **WHEN** a valid USER portal token or an unauthenticated client calls any admin order maintenance endpoint
- **THEN** the system MUST reject the request with an authorization error.

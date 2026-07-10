# Goal: Implement two-segment connecting itinerary search, booking, and whole-itinerary changes

Implement a production-safe MVP for user-facing connecting itineraries in SkyBooker. Do not stop until every acceptance criterion and required test is satisfied.

## 1. Project Background

- Project: SkyBooker flight booking system.
- Frontend: Next.js/React/TypeScript, Tailwind and Base UI/shadcn-style components under `frontend/`.
- Backend: Java 21, Spring Boot, MyBatis, Spring Security.
- Database: MySQL 8 with Flyway migrations; Redis is used by existing booking flows.
- Existing behavior:
  - A `flight` is one schedulable flight leg with seat inventory.
  - Public `GET /api/flights` and the `/flights` page return only individual legs.
  - `direct_flag=0` is currently only a non-direct/stopover marker; it has no stopover details and does not represent a connecting itinerary.
  - One `ticket_order`, `order_passenger`, and `change_record` currently describe one flight leg only.
  - Existing single-leg booking, simulated payment, cancellation, refund, change, waitlist, and admin workflows must remain compatible.

## 2. Objective

Allow authenticated users to discover and purchase an itinerary consisting of exactly two independently scheduled flight legs, then manage it as one order. The system must atomically reserve both legs, prevent partial bookings, display connection details, and support a safe whole-itinerary change before travel begins.

## 3. Scope

### Included

1. A user search API and `/flights` UI that return both direct one-leg itineraries and automatically composed one-stop itineraries.
2. Connecting-itinerary detail and booking UI with one seat selection per passenger on each leg.
3. One parent order representing the complete journey, with persistent segment and segment-passenger snapshots.
4. Simulated payment, unpaid cancellation, whole-itinerary refund, and whole-itinerary change for connecting orders.
5. User and admin order detail/list views that identify connecting orders and show all segments.
6. Database migrations, API documentation, seed/test-data support, backend integration tests, and frontend checks.

### Explicitly excluded

1. More than two legs, open-jaw/multi-city journeys, overnight hotel/visa rules, codeshares, or interline ticketing.
2. Administrator-authored itinerary products. Administrators continue to maintain individual flight legs only; public search composes eligible pairs at query time.
3. Partial cancellation, partial refund, passenger-specific refund, or changing only one leg of a connecting journey.
4. Connecting waitlists. A connecting itinerary is only shown and bookable when every segment has enough sellable inventory for all passengers.
5. Real payment-provider integration; retain the current simulated pay flow.
6. Changing a journey after any segment has departed.

## 4. Relevant Directories

- Backend flight search: `backend/src/main/java/com/skybooker/flight/`, `backend/src/main/resources/mapper/flight/`.
- Backend orders: `backend/src/main/java/com/skybooker/order/`, `backend/src/main/resources/mapper/order/`.
- Backend refunds and changes: `backend/src/main/java/com/skybooker/refund/`, `backend/src/main/java/com/skybooker/change/`.
- Backend admin order read support: `backend/src/main/java/com/skybooker/admin/`.
- Migrations: `backend/src/main/resources/db/migration/`.
- Frontend flight search and cards: `frontend/src/app/flights/`, `frontend/src/features/flights/`, `frontend/src/components/common/FlightCard.tsx`.
- Frontend booking: `frontend/src/app/booking/`, `frontend/src/features/booking/`, `frontend/src/services/` and `frontend/src/types/`.
- API documentation: `docs/07_API_DESIGN.md`, feature documentation as appropriate.
- Tests: backend tests adjacent to the relevant module and test-data generation/validation under `scripts/`.
- Do not modify unrelated frontend pages, authentication behavior, deployed migrations, or existing applied migration files.

## 5. Business Rules

1. An itinerary has either one `DIRECT` segment or exactly two `CONNECTING` segments. This feature creates only `CONNECTING` itineraries; existing direct booking remains unchanged.
2. A connecting pair is eligible only when all of the following are true:
   - First leg departs from the requested origin and second leg arrives at the requested destination.
   - First leg arrival airport equals second leg departure airport.
   - Both legs are published, have status `ON_TIME` or `DELAYED`, depart in the future, and satisfy the existing sellability rules.
   - Second-leg departure is at least 90 minutes and no more than 6 hours after first-leg arrival.
   - The first leg departs on the requested search date; the second leg may arrive or depart on the following calendar date if it remains in the valid connection window.
   - Both legs have sufficient available seats for the requested passenger count; when a cabin filter is supplied, both legs must have that cabin inventory.
3. Different airlines are allowed. The selected connection airport must be visible in the search card and booking flow.
4. Search returns direct and connecting itineraries together. `directOnly=true` returns direct itineraries only; it must never include a connecting pair.
5. A connecting result shows: journey type, two ordered leg summaries, connection airport, connection duration, total duration, earliest per-passenger fare estimate, and the itinerary's sellability.
6. The server recomputes all availability and money. The client never supplies a trusted total price, transfer duration, or itinerary classification.
7. During booking, every selected passenger must have one valid available seat on each segment. Cabin class may differ by segment; each selected seat price is used in server-side calculation.
8. Connecting-order money is calculated server-side as:
   - `ticketAmount`: sum of every selected seat price across every passenger and segment.
   - `airportFee`: existing per-passenger airport fee multiplied by passenger count and segment count.
   - `fuelFee`: existing per-passenger fuel fee multiplied by passenger count and segment count.
   - `serviceFee`: one existing order-level service fee per parent order.
   - `totalAmount`: the sum of those values, stored as `DECIMAL`, never float.
9. A connecting booking has one payment deadline and one simulated payment operation. Payment commits seats on both segments or none.
10. Unpaid cancellation cancels the full parent order and releases locks on every segment. It creates no refund record.
11. Refund is all-or-nothing for the complete journey. It is allowed only while no segment has departed and uses the existing fee schedule against the earliest segment departure; it releases sold seats on every segment in one transaction.
12. Whole-itinerary change is the only connecting change mode. Before the earliest current segment departure and subject to the existing two-hour cutoff (unless a valid admin `force=true` request uses the current override policy), the user/admin selects a complete replacement direct or connecting itinerary from the same original origin to the same final destination. The system never changes only one segment.
13. A connecting itinerary cannot be changed after any current segment has departed. A replacement itinerary must meet the same sellability and connection rules, and all replacement segments must depart after the original first-leg departure plus the existing change rule's minimum boundary.
14. Whole-itinerary change recalculates seat prices, airport/fuel fees, total amount, price difference, and change fee on the server. The change fee follows the existing change-fee policy using the earliest current segment departure.
15. Connecting orders are excluded from current single-flight waitlist fulfillment. Do not accidentally create a waitlist against only one segment.

## 6. Frontend Requirements

### Search

- Update `/flights` to render a discriminated itinerary card model rather than assuming every result is one `FlightVO`.
- Direct cards preserve the current visual flow.
- Connecting cards clearly display:
  - “中转” badge;
  - origin, connection airport, and final destination;
  - both flight numbers/airlines and departure/arrival times;
  - connection duration, total duration, and total estimated fare;
  - a warning only when the second segment is on the next calendar day.
- Preserve existing filters. “仅显示直飞航班” hides connecting results. Empty, loading, pagination, and network-error states remain usable.

### Booking

- Add a connecting booking route/component; do not overload `/booking/[flightId]` with an ambiguous comma-separated ID.
- The route receives only non-authoritative segment identifiers. On load, it fetches a fresh itinerary quote/detail and redirects with a friendly message if it is no longer valid.
- Use the same passenger set for both segments. Present seat selection segment by segment, identify the flight/airport/time for each segment, and require every passenger to choose a seat on every segment before confirmation.
- The confirmation view itemizes both legs, connection airport/duration, each fee category, total amount, and the shared payment deadline.
- Use existing login redirect, API envelope, toast/inline-error conventions, responsive layout, and accessible labels.

### Orders and post-booking actions

- User order list/detail and admin order list/detail show `DIRECT` or `CONNECTING` and render all persisted segments and seat assignments.
- For connecting orders, cancellation, refund, and change actions use whole-itinerary labels and explain that individual-leg changes are not supported.
- Existing direct-order UI remains behaviorally unchanged.
- Disabled/unavailable actions must give an understandable reason (for example, “首段已起飞，无法改签”).

## 7. Backend Requirements

### APIs

1. `GET /api/itineraries/search`
   - Public; accepts the existing flight search inputs plus pagination.
   - Returns `PageResponse<ItineraryVO>`, where each item has `journeyType` (`DIRECT` or `CONNECTING`), `segments[]`, origin/final destination summary, connection metadata when applicable, total duration, estimated amount, and availability summary.
   - Preserve `GET /api/flights` for existing single-flight consumers, AI flows, and compatibility.

2. `POST /api/itineraries/quote`
   - Authenticated USER portal.
   - Request: ordered `segmentFlightIds` (one or two), selected `passengerIds`, and optional per-segment cabin preferences.
   - Revalidates the itinerary; returns fresh segment data, seat maps/availability, price estimate, and a short-lived non-authoritative quote token or request version.
   - The quote does not reserve inventory.

3. `POST /api/orders/connecting`
   - Authenticated USER portal.
   - Request: `clientRequestId` (UUID), ordered `segments[{flightId, items[{passengerId, seatId}]}]`.
   - Requires exactly two segments, identical passenger sets in each segment, no duplicate seat per segment, and a currently valid connecting pair.
   - Creates one parent `ticket_order` in `PENDING_PAYMENT` plus segment snapshots and locks all selected seats atomically.
   - Returns the normal enriched order response.

4. Existing `POST /api/orders/{id}/pay`, `POST /api/orders/{id}/cancel`, `POST /api/orders/{id}/refund`, `GET /api/orders/{id}`, and `GET /api/orders`
   - Extend safely to handle connecting parent orders using persisted segments.
   - Do not change direct-order request contracts.

5. `GET /api/orders/{id}/connecting-change-options` and `POST /api/orders/{id}/connecting-change`
   - USER portal, ownership enforced. Admin equivalents follow existing `/api/admin/orders/{id}/...` conventions and authorization.
   - Change request has `clientRequestId`, replacement ordered segments with passenger/seat mappings, and `reason`; admin may retain existing `force` semantics.
   - The replacement may be a direct itinerary (one segment) or a connecting itinerary (two segments), but must preserve original trip origin and final destination.

### Services

- Add a dedicated itinerary composition service; do not put route-join logic in controllers or frontend code.
- Use a single reusable booking core for direct/connecting seat locking where possible, but preserve external direct-order contracts.
- Extend refund/change/cancel services via explicit connecting-order paths rather than loosening single-flight assumptions silently.
- Add mapper queries with bound parameters for self-joining eligible flights through the same airport. Never interpolate user-provided SQL.
- Update API documentation and error-code mapping for itinerary invalidation, invalid connection, incomplete segment seats, mixed passenger sets, and segment departure restrictions.

## 8. Database Requirements

Add a new Flyway migration; never edit applied migrations.

1. Extend `ticket_order`:
   - `journey_type VARCHAR(20) NOT NULL DEFAULT 'DIRECT'` with a CHECK constraint `DIRECT|CONNECTING`.
   - `client_request_id VARCHAR(64) NULL` for client retry idempotency.
   - Unique index on `(user_id, client_request_id)` for non-null values.
   - Keep legacy `flight_id` populated for existing direct orders. For connecting orders, set it to the first segment flight for compatibility only; every new connecting read/write must use segment records, not this compatibility value.

2. Create `ticket_order_segment`:
   - `id BIGINT` primary key;
   - `order_id BIGINT NOT NULL`;
   - `segment_no TINYINT NOT NULL` (1 or 2, unique per order);
   - `flight_id BIGINT NOT NULL`;
   - immutable snapshots for flight number, airline name/code, departure/arrival airport/city, departure/arrival time, and segment ticket amount;
   - `created_at`, `updated_at`;
   - unique `(order_id, segment_no)`, index on `flight_id`, and foreign key to `ticket_order` consistent with existing schema policy.

3. Create `order_segment_passenger`:
   - `id BIGINT` primary key;
   - `order_segment_id BIGINT NOT NULL`;
   - `passenger_id`, passenger name/type snapshots;
   - `seat_id`, `seat_no`, `ticket_price`;
   - unique `(order_segment_id, passenger_id)` and unique `(order_segment_id, seat_id)`;
   - indexes for segment, passenger, and seat lookup.

4. Create `connecting_change_record` and `connecting_change_segment` (or an equivalently normalized header/detail schema):
   - header records order, old/new total amount, price difference, change fee, reason, status, and timestamps;
   - detail records old/new ordered segment snapshots and passenger seat mappings;
   - do not misuse the existing one-flight `change_record` to represent a multi-segment replacement.

5. Preserve existing direct rows and direct migrations. Backfill is not required beyond `journey_type='DIRECT'` defaults.

## 9. State Machine

Parent order states remain the existing `PENDING_PAYMENT`, `ISSUED`, `CHANGED`, `CANCELLED`, `REFUNDED`, `CHANGE_PENDING`, and `VOIDED`; a connecting order always applies a transition to every persisted segment as one unit.

- Initial: `PENDING_PAYMENT`.
- Terminal: `CANCELLED`, `REFUNDED`, and `VOIDED`.
- Legal transitions:
  - `PENDING_PAYMENT -> ISSUED`: one successful simulated payment commits all locked segment seats to SOLD.
  - `PENDING_PAYMENT -> CANCELLED`: user cancellation or expiration releases locks on all segments; no refund record.
  - `ISSUED|CHANGED -> REFUNDED`: whole-itinerary refund before any segment departure; releases all sold seats.
  - `ISSUED|CHANGED -> CHANGED`: whole-itinerary change succeeds after all new segment seats are secured and all old segment seats are released.
  - `CANCELLED|REFUNDED -> VOIDED`: retain the existing protected admin void semantics.
- Illegal transitions:
  - Any single segment independently changing status; segments are not independently cancellable/refundable/changeable.
  - `PENDING_PAYMENT -> CHANGED|REFUNDED`.
  - `ISSUED|CHANGED -> CANCELLED`.
  - Any change/refund after the earliest relevant segment has departed.
  - Any transition from `VOIDED`.
- Transition side effects must be in the same transaction as the parent state CAS:
  - booking: parent order + segments + segment passengers + all seat locks + all remaining-seat decrements;
  - payment: parent status + every locked seat SOLD + payment time;
  - cancellation/expiry: parent status + all lock releases + every segment remaining-seat increment;
  - refund: parent status + refund record + all SOLD-seat releases + every segment remaining-seat increment;
  - change: locks/decrements all new segments, updates segment/passenger snapshots and parent amounts, releases/increments all old segments, then writes the connecting change records.

## 10. Error Handling Requirements

- Missing order, flight, segment, passenger, or seat: standard resource-not-found response without leaking another user's data.
- Invalid itinerary: explicit validation error for leg count, discontinuous airports, transfer window, origin/destination mismatch, departed/unsellable leg, or stale quote.
- Insufficient inventory or a failed seat CAS: existing seat-not-available/sellability error; the whole transaction rolls back.
- Mixed/missing passenger sets across segments, duplicate seat selection, and client-supplied invalid segment order: validation error.
- Connecting cancellation/refund/change on an ineligible state or after departure: state-invalid/change-window/refund-window error with a user-readable message.
- Duplicate `clientRequestId`: return the existing order/change result when the completed request matches, otherwise reject conflicting reuse; never create a second parent order.
- Frontend displays errors inline in booking/action dialogs and preserves user selections when safe to retry.

## 11. Concurrency & Consistency

- Transaction boundaries:
  - Connecting create: validate every segment and passenger, create parent/segments/snapshots, CAS-lock every requested seat, decrement every segment inventory, then commit once.
  - Payment, cancellation, expiration, refund, and whole-itinerary change each update all affected segments and the parent order in one database transaction.
- Idempotency:
  - `POST /api/orders/connecting` and connecting-change submission require a UUID `clientRequestId` scoped by user and stored/uniquely constrained.
  - Repeated pay/cancel/refund handling follows current state-guard semantics and must not release or sell seats twice.
- Locking/anti-oversell:
  - Reuse the current seat conditional updates and remaining-seat conditional decrement logic for every leg.
  - Acquire new connecting-change seats in deterministic `(flight_id, seat_id)` order to reduce deadlock risk; only release old seats after every new lock/decrement succeeds.
  - Any mismatch in affected-row counts aborts the transaction and leaves no new locks, decrements, segment snapshots, or partial order rows.
- Money integrity:
  - Recompute all segment prices and fees server-side from current seat snapshots; do not accept a client total.
  - Store amounts with `BigDecimal`/`DECIMAL`; recompute the refund/change fee against the earliest current segment departure.
- Specific races to test:
  - Two users compete for a seat on either leg.
  - First-leg lock succeeds and second-leg lock fails.
  - Payment/cancel/expiry races on the same parent order.
  - Concurrent whole-itinerary change and refund.
  - A duplicate create/change request with the same request ID.

## 12. Permissions & Security

- Search is public, but booking, quote, payment, cancellation, refund, and change require a USER-portal token.
- Users may only read and mutate their own parent orders and connecting change options.
- ADMIN portal uses explicit admin endpoints; administrators may not reuse user identity endpoints.
- Validate all client-supplied flight IDs, ordered segment IDs, passenger ownership, seat ownership/scope, cabin data, and request IDs on the server.
- Do not expose other users' passenger information, seat lock owner, or internal database IDs beyond existing authorized contracts.
- Quote results are advisory only and must be revalidated at booking/change submission; no client-side quote grants authorization or inventory ownership.

## 13. Engineering Constraints

- Follow `AGENTS.md`: backend/deployment work belongs in `backend/`, `scripts/`, `docs/`, and `appendices/`; do not edit unrelated frontend work.
- Flyway migrations are the only schema/data initialization source. Add a new versioned migration.
- Controllers validate and return the standard API envelope; services own transactions and business rules; mappers only access data.
- Preserve all existing direct-flight API contracts and behavior.
- Reuse current security, MyBatis, error-code, money, seat CAS, operation-log, and response conventions.
- Do not add a dependency unless it solves a demonstrated need; no third-party route/OTA integration is in scope.
- Ensure all time calculations honor the project business timezone policy and the time-zone correction tracked separately in issue #139.

## 14. Execution Flow

1. Read `README.md`, `AGENTS.md`, `docs/07_API_DESIGN.md`, existing flight/order/refund/change modules, and issue #139 before implementation.
2. Add/update API and domain documentation before coding DTO/VO contracts.
3. Implement migrations and mapper/entity models, then backend itinerary composition and order lifecycle extensions.
4. Implement user frontend search cards, connecting booking, and order detail/action rendering after backend contracts stabilize.
5. Update deterministic seed/test-data generation to include valid and invalid connection pairs.
6. Add focused unit/integration tests for services, mappers, controllers, and concurrency-sensitive booking paths; add frontend type/lint/build checks.
7. Run backend tests, package build, frontend type check/lint/build, and documented manual/API verification.

## 15. Acceptance Criteria

1. A search for an origin/destination/date returns valid direct and one-stop results; a direct-only filter excludes every connecting result.
2. Every connecting result has exactly two ordered legs, one shared airport, a 90-minute to 6-hour transfer, correct total duration, and server-derived price/availability.
3. Search never creates pseudo-connections through different airports, expired/departed/unpublished/unsellable legs, or a transfer outside the defined window.
4. A user can select the same passenger set and one valid seat per passenger per leg, then create exactly one pending parent order with both leg snapshots.
5. If either segment cannot lock/decrement, no order, lock, inventory decrement, or passenger snapshot persists.
6. One payment commits all segment seats; unpaid cancellation/expiry releases all locks; no partial segment state exists.
7. Whole-itinerary refund and whole-itinerary change work before the earliest applicable departure and are rejected after any segment has departed.
8. Whole-itinerary change atomically protects the replacement itinerary before releasing the old one; no partial replacement survives a failure.
9. Direct booking, direct refund/change, admin order workflows, AI search, and waitlist behavior retain their existing contracts and tests.
10. User/admin order details display every connecting segment and connecting change record without losing direct-order rendering.
11. The project starts, backend tests pass, frontend type/lint/build checks pass, and no test result is fabricated.

## 16. Testing Requirements

1. Search tests: valid same-airport 90-minute, 6-hour, cross-day connection; reject 89-minute, over-6-hour, disconnected, departed, unpublished, cancelled, sold-out, and insufficient-capacity legs.
2. API tests: direct-only behavior, itinerary detail/quote validation, unauthorized/ADMIN portal rejection for user endpoints, malformed segment order, and stale itinerary submission.
3. Booking integration tests: one/two passengers, different cabins across legs, amount calculation, all-or-nothing locks, duplicate request ID, and simultaneous last-seat competition on either leg.
4. Lifecycle tests: pay, cancel, expiry, refund, and whole-itinerary change; verify all relevant seats, remaining-seat counters, order status, segments, records, and money values.
5. Change tests: direct-to-connecting, connecting-to-direct, connecting-to-connecting; reject leg-only changes, post-departure changes, and invalid replacement route/connection.
6. Regression tests: existing direct order, admin order maintenance, waitlist, AI recommendations, timezone/departure sellability, and public `/api/flights` behavior.
7. Frontend checks: card rendering, connecting booking validation, error display, order detail display, `pnpm exec tsc --noEmit`, lint, and production build.

## 17. Prohibited Actions

- Do not edit existing applied Flyway migrations or manually mutate production/demo records.
- Do not represent a two-leg connection by only setting `direct_flag=0`.
- Do not trust client price, route, transfer duration, availability, or a quote token without revalidation.
- Do not create one order per leg, allow a partial booking, or implement single-leg connecting changes/refunds.
- Do not re-use one-flight `change_record` as an ambiguous multi-leg audit record.
- Do not silently change existing direct API contracts or bypass authorization/seat CAS.
- Do not add connecting waitlist fulfillment in this feature.

## 18. Final Output Requirements

After implementation, report:

1. Modified files and their purpose.
2. New/extended APIs and examples.
3. Migration version, tables/columns/indexes, and compatibility notes.
4. State-machine and locking verification evidence.
5. Exact tests/builds run and results.
6. Deferred items: multi-stop journeys, partial segment operations, connecting waitlist, OTA/interline policies, and real payment integration.

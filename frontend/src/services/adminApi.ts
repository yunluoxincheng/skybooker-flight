import { del, get, patch, post, put } from "@/lib/request"
import type { AdminLoginResponse, AdminUser } from "@/types/auth"
import type {
  AirlineFormDTO,
  AirlineVO,
  AirportFormDTO,
  AirportVO,
  DashboardSummaryVO,
  HotRouteVO,
  OrderStatusDistributionVO,
  UserAdminVO,
  FlightFormDTO,
  UpdateFlightCabinsDTO,
  SalesTrendVO,
  RoutePerformanceVO,
  FlightLoadFactorVO,
  RefundTrendVO,
  WaitlistPerformanceVO,
  LlmConfigVO,
  LlmConfigDTO,
  CreateUserAdminDTO,
  DeleteUserBlockInfoVO,
  CreateConnectingFlightsDTO,
  ConnectingFlightPairVO,
  ConnectingItineraryAdminVO,
  ConnectingItineraryFormDTO,
} from "@/types/admin"
import type { FlightCabinVO, FlightSeatVO, FlightVO } from "@/types/flight"
import type { PassengerVO } from "@/types/passenger"
import type {
  AdminChangeDTO,
  AdminCancelOrderDTO,
  AdminNoteDTO,
  AdminOrderDetailVO,
  AdminOrderQueryDTO,
  AdminRefundDTO,
  AdminVoidDTO,
  ChangeOptionVO,
  ChangeOrderResultVO,
  ChangeRecordVO,
  CreateAdminOrderDTO,
  CreateAdminConnectingOrderDTO,
  ConnectingChangeDTO,
  OrderVO,
  RefundRecordVO,
  RefundVO,
} from "@/types/order"
import type { PageData } from "@/types/api"
import type { FlightSearchParams, ItineraryVO } from "@/types/flight"

// ---- Auth ----

export function adminLogin(username: string, password: string) {
  return post<AdminLoginResponse>("/admin/auth/login", { username, password })
}

export function getAdminMe() {
  return get<AdminUser>("/admin/me", undefined, { auth: "admin" })
}

export function adminLogout(refreshToken?: string) {
  return post<null>("/admin/logout", refreshToken ? { refreshToken } : undefined, { auth: "admin" })
}

// ---- Dashboard ----

export function getDashboardSummary() {
  return get<DashboardSummaryVO>("/admin/dashboard/summary", undefined, { auth: "admin" })
}

export function getHotRoutes() {
  return get<HotRouteVO[]>("/admin/dashboard/hot-routes", undefined, { auth: "admin" })
}

export function getOrderStatusDistribution() {
  return get<OrderStatusDistributionVO[]>("/admin/dashboard/order-status", undefined, { auth: "admin" })
}

// ---- Flights ----

export function getFlights(params?: Record<string, string | number | boolean | undefined>) {
  return get<PageData<FlightVO>>("/admin/flights", params, { auth: "admin" })
}

export function createFlight(data: FlightFormDTO) {
  return post<FlightVO>("/admin/flights", data, { auth: "admin" })
}

export function createConnectingFlights(data: CreateConnectingFlightsDTO) {
  return post<ConnectingFlightPairVO>("/admin/flights/connecting-pair", data, { auth: "admin" })
}

export function getConnectingItineraries() {
  return get<ConnectingItineraryAdminVO[]>("/admin/connecting-itineraries", undefined, { auth: "admin" })
}

export function createConnectingItinerary(data: ConnectingItineraryFormDTO) {
  return post<ConnectingItineraryAdminVO>("/admin/connecting-itineraries", data, { auth: "admin" })
}

export function updateConnectingItinerary(id: number, data: ConnectingItineraryFormDTO) {
  return put<ConnectingItineraryAdminVO>(`/admin/connecting-itineraries/${id}`, data, { auth: "admin" })
}

export function publishConnectingItinerary(id: number) {
  return post<ConnectingItineraryAdminVO>(`/admin/connecting-itineraries/${id}/publish`, undefined, { auth: "admin" })
}

export function unpublishConnectingItinerary(id: number) {
  return post<ConnectingItineraryAdminVO>(`/admin/connecting-itineraries/${id}/unpublish`, undefined, { auth: "admin" })
}

export function updateFlight(id: number, data: FlightFormDTO) {
  return put<FlightVO>(`/admin/flights/${id}`, data, { auth: "admin" })
}

export function publishFlight(id: number) {
  return post<null>(`/admin/flights/${id}/publish`, undefined, { auth: "admin" })
}

export function unpublishFlight(id: number) {
  return post<null>(`/admin/flights/${id}/unpublish`, undefined, { auth: "admin" })
}

export function generateSeats(id: number) {
  return post<null>(`/admin/flights/${id}/generate-seats`, undefined, { auth: "admin" })
}

export function updateFlightCabins(id: number, data: UpdateFlightCabinsDTO) {
  return put<FlightCabinVO[]>(`/admin/flights/${id}/cabins`, data, { auth: "admin" })
}

// ---- Airlines ----

export function getAirlines(params?: Record<string, string | number | boolean | undefined>) {
  return get<PageData<AirlineVO>>("/admin/airlines", params, { auth: "admin" })
}

export function createAirline(data: AirlineFormDTO) {
  return post<AirlineVO>("/admin/airlines", data, { auth: "admin" })
}

export function updateAirline(id: number, data: Pick<AirlineFormDTO, "name" | "logoUrl">) {
  return put<AirlineVO>(`/admin/airlines/${id}`, data, { auth: "admin" })
}

export function enableAirline(id: number) {
  return post<null>(`/admin/airlines/${id}/enable`, undefined, { auth: "admin" })
}

export function disableAirline(id: number) {
  return post<null>(`/admin/airlines/${id}/disable`, undefined, { auth: "admin" })
}

export function deleteAirline(id: number) {
  return del<null>(`/admin/airlines/${id}`, { auth: "admin" })
}

// ---- Airports ----

export function getAirports(params?: Record<string, string | number | boolean | undefined>) {
  return get<PageData<AirportVO>>("/admin/airports", params, { auth: "admin" })
}

export function createAirport(data: AirportFormDTO) {
  return post<AirportVO>("/admin/airports", data, { auth: "admin" })
}

export function updateAirport(id: number, data: Omit<AirportFormDTO, "code">) {
  return put<AirportVO>(`/admin/airports/${id}`, data, { auth: "admin" })
}

export function enableAirport(id: number) {
  return post<null>(`/admin/airports/${id}/enable`, undefined, { auth: "admin" })
}

export function disableAirport(id: number) {
  return post<null>(`/admin/airports/${id}/disable`, undefined, { auth: "admin" })
}

export function deleteAirport(id: number) {
  return del<null>(`/admin/airports/${id}`, { auth: "admin" })
}

// ---- Orders ----

export function getAdminOrders(params?: AdminOrderQueryDTO) {
  return get<PageData<OrderVO>>("/admin/orders", params, { auth: "admin" })
}

export function getAdminOrderById(id: number) {
  return get<OrderVO>(`/admin/orders/${id}`, undefined, { auth: "admin" })
}

export function createAdminOrder(data: CreateAdminOrderDTO) {
  return post<OrderVO>("/admin/orders", data, { auth: "admin" })
}

export function searchAdminItineraries(params: FlightSearchParams) {
  return get<PageData<ItineraryVO>>("/itineraries/search", params as Record<string, string | number | boolean | undefined>, { auth: "admin" })
}

export function createAdminConnectingOrder(data: CreateAdminConnectingOrderDTO) {
  return post<OrderVO>("/admin/orders/connecting", data, { auth: "admin" })
}

export function refundAdminOrder(id: number, data: AdminRefundDTO) {
  return post<RefundVO>(`/admin/orders/${id}/refund`, data, { auth: "admin" })
}

export function cancelAdminOrder(id: number, data: AdminCancelOrderDTO) {
  return post<OrderVO>(`/admin/orders/${id}/cancel`, data, { auth: "admin" })
}

export function getAdminChangeOptions(id: number) {
  return get<ChangeOptionVO[]>(`/admin/orders/${id}/change-options`, undefined, { auth: "admin" })
}

export function changeAdminOrder(id: number, data: AdminChangeDTO) {
  return post<ChangeOrderResultVO>(`/admin/orders/${id}/change`, data, { auth: "admin" })
}

export function getAdminConnectingChangeOptions(id: number, startDate?: string, endDate?: string) {
  return get<ItineraryVO[]>(`/admin/orders/${id}/connecting-change-options`, { startDate, endDate }, { auth: "admin" })
}

export function changeAdminConnectingOrder(id: number, data: ConnectingChangeDTO) {
  return post<OrderVO>(`/admin/orders/${id}/connecting-change`, data, { auth: "admin" })
}

export function voidAdminOrder(id: number, data: AdminVoidDTO) {
  return post<OrderVO>(`/admin/orders/${id}/void`, data, { auth: "admin" })
}

export function getAdminOrderDetailEnhanced(id: number) {
  return get<AdminOrderDetailVO>(`/admin/orders/${id}/detail`, undefined, { auth: "admin" })
}

export function getPassengersByUser(userId: number) {
  return get<PassengerVO[]>("/admin/passengers", { userId }, { auth: "admin" })
}

export function getAdminOrderRefunds(id: number) {
  return get<RefundRecordVO[]>(`/admin/orders/${id}/refunds`, undefined, { auth: "admin" })
}

export function getAdminOrderChanges(id: number) {
  return get<ChangeRecordVO[]>(`/admin/orders/${id}/changes`, undefined, { auth: "admin" })
}

export function updateAdminNote(id: number, data: AdminNoteDTO) {
  return patch<OrderVO>(`/admin/orders/${id}/admin-note`, data, { auth: "admin" })
}

export function getAdminFlightSeats(flightId: number) {
  return get<FlightSeatVO[]>(`/admin/flights/${flightId}/seats`, undefined, { auth: "admin" })
}

// ---- Users ----

export function getUsers(params?: Record<string, string | number | boolean | undefined>) {
  return get<PageData<UserAdminVO>>("/admin/users", params, { auth: "admin" })
}

export function disableUser(id: number) {
  return post<null>(`/admin/users/${id}/disable`, undefined, { auth: "admin" })
}

export function enableUser(id: number) {
  return post<null>(`/admin/users/${id}/enable`, undefined, { auth: "admin" })
}

export function createAdminUser(data: CreateUserAdminDTO) {
  return post<UserAdminVO>("/admin/users", data, { auth: "admin" })
}

export function deleteUser(id: number) {
  return del<null>(`/admin/users/${id}`, { auth: "admin" })
}

export function checkUserDeletable(id: number) {
  return get<DeleteUserBlockInfoVO>(`/admin/users/${id}/delete-check`, undefined, { auth: "admin" })
}

// ---- Reports ----

export function getSalesTrend(params?: Record<string, string | number | boolean | undefined>) {
  return get<SalesTrendVO[]>("/admin/reports/sales-trend", params, { auth: "admin" })
}

export function getRoutePerformance(params?: Record<string, string | number | boolean | undefined>) {
  return get<RoutePerformanceVO[]>("/admin/reports/route-performance", params, { auth: "admin" })
}

export function getFlightLoadFactor(params?: Record<string, string | number | boolean | undefined>) {
  return get<FlightLoadFactorVO[]>("/admin/reports/flight-load-factor", params, { auth: "admin" })
}

export function getRefundTrend(params?: Record<string, string | number | boolean | undefined>) {
  return get<RefundTrendVO[]>("/admin/reports/refund-trend", params, { auth: "admin" })
}

export function getWaitlistPerformance(params?: Record<string, string | number | boolean | undefined>) {
  return get<WaitlistPerformanceVO>("/admin/reports/waitlist-performance", params, { auth: "admin" })
}

// ---- AI Config ----

export function getLlmConfig() {
  return get<LlmConfigVO>("/admin/ai/llm-config", undefined, { auth: "admin" })
}

export function updateLlmConfig(data: LlmConfigDTO) {
  return put<LlmConfigVO>("/admin/ai/llm-config", data, { auth: "admin" })
}

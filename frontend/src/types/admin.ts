/** 仪表盘汇总 */
export interface DashboardSummaryVO {
  totalUsers: number
  totalFlights: number
  totalTicketOrders: number
  issuedTicketOrders: number
  refundedTicketOrders: number
  pendingPaymentTicketOrders: number
  totalWaitlistOrders: number
  waitingWaitlistOrders: number
  grossIssuedOrderRevenue: number
  ticketRefundAmount: number
  waitlistRefundAmount: number
  totalRefundAmount: number
}

/** 热门航线 */
export interface HotRouteVO {
  departureCity: string
  arrivalCity: string
  routeLabel: string
  issuedOrderCount: number
  passengerCount: number
  revenue: number
}

/** 订单状态分布 */
export interface OrderStatusDistributionVO {
  status: string
  count: number
}

/** 管理员端的用户 — 匹配后端 UserAdminVO */
export interface UserAdminVO {
  id: number
  email: string
  realName: string
  phone?: string
  avatarUrl?: string
  role: "USER" | "ADMIN"
  status: "NORMAL" | "DISABLED"
  emailVerified: boolean
  phoneVerified: boolean
  lastLoginAt?: string
  createdAt: string
  updatedAt?: string
}

/** 航班表单 DTO（管理端） */
export interface FlightFormDTO {
  flightNo: string
  airlineId: number
  departureAirportId: number
  arrivalAirportId: number
  departureTime: string
  arrivalTime: string
  durationMinutes: number
  basePrice: number
  totalSeats: number
  baggageAllowance: string
  directFlag: boolean
}

/** 销售趋势 — 匹配后端 SalesTrendVO */
export interface SalesTrendVO {
  period: string
  activeOrderCount: number
  passengerCount: number
  revenue: number
}

/** 航线表现 — 匹配后端 RoutePerformanceVO */
export interface RoutePerformanceVO {
  departureCity: string
  arrivalCity: string
  routeLabel: string
  activeOrderCount: number
  passengerCount: number
  revenue: number
  refundAmount: number
  netRevenue: number
}

/** 航班载客率 — 匹配后端 FlightLoadFactorVO */
export interface FlightLoadFactorVO {
  flightId: number
  flightNo: string
  airlineName: string
  routeLabel: string
  departureTime: string
  totalSeats: number
  soldPassengerCount: number
  loadFactorPercent: number
}

/** 退款趋势 — 匹配后端 RefundTrendVO */
export interface RefundTrendVO {
  period: string
  refundCount: number
  refundAmount: number
}

/** LLM 配置 VO — 匹配后端 AiLlmConfigVO */
export interface LlmConfigVO {
  enabled: boolean
  baseUrl: string
  apiKey: string
  model: string
  timeoutMs: number
  maxRetries: number
  source: string
  updatedBy: number
  updatedAt: string
}

/** LLM 配置 DTO — 匹配后端 AiLlmConfigDTO */
export interface LlmConfigDTO {
  enabled: boolean
  baseUrl?: string
  apiKey?: string
  model?: string
  timeoutMs?: number
  maxRetries?: number
}

/** 舱位库存设置项 */
export interface FlightCabinSettingDTO {
  cabinClass: "ECONOMY" | "BUSINESS" | "FIRST"
  price: number
  totalSeats: number
}

/** 批量更新舱位库存请求 */
export interface UpdateFlightCabinsDTO {
  cabins: FlightCabinSettingDTO[]
}

/** 候补表现 */
export interface WaitlistPerformanceVO {
  submittedCount: number
  pendingPaymentCount: number
  waitingCount: number
  successCount: number
  failedCount: number
  cancelledCount: number
  refundedCount: number
  expiredCount: number
  payAmount: number
  refundAmount: number
}

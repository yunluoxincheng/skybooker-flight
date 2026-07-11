import type { FlightVO } from "@/types/flight"

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
  /** 旧数据兼容字段；新建用户不再填写或提交。 */
  realName: string
  nickname?: string
  phone?: string
  avatarUrl?: string
  role: "USER" | "ADMIN"
  status: "NORMAL" | "DISABLED" | "DELETED"
  emailVerified: boolean
  phoneVerified: boolean
  lastLoginAt?: string
  createdAt: string
  updatedAt?: string
}

/** 管理端新增普通用户 DTO */
export interface CreateUserAdminDTO {
  email: string
  nickname: string
  phone?: string
  password: string
}

/** 删除用户前阻断信息 */
export interface DeleteUserBlockInfoVO {
  canDelete: boolean
  orderCount: number
  passengerCount: number
  waitlistCount: number
  refundOrChangeCount: number
  oauthBound: boolean
  aiSessionCount: number
  aiRecommendationCount: number
  blockReasons: string[]
}

export type AdminEntityStatus = "ENABLED" | "DISABLED"

export interface AirlineVO {
  id: number
  code: string
  name: string
  logoUrl?: string
  status: AdminEntityStatus
  createdAt: string
  updatedAt: string
}

export interface AirlineFormDTO {
  code: string
  name: string
  logoUrl?: string
}

export interface AirportVO {
  id: number
  code: string
  name: string
  city: string
  province?: string
  status: AdminEntityStatus
  createdAt: string
  updatedAt: string
}

export interface AirportFormDTO {
  code: string
  name: string
  city: string
  province?: string
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

export interface CreateConnectingFlightsDTO {
  firstSegment: FlightFormDTO
  secondSegment: FlightFormDTO
}

export interface ConnectingFlightPairVO {
  firstSegment: FlightVO
  secondSegment: FlightVO
  transferMinutes: number
}

export interface ConnectingItineraryFormDTO {
  firstFlightId: number
  secondFlightId: number
}

export interface ConnectingItineraryAdminVO {
  id: number
  firstSegment: FlightVO
  secondSegment: FlightVO
  publishStatus: "DRAFT" | "PUBLISHED"
  transferMinutes: number
  createdBy?: number
  createdAt: string
  updatedAt: string
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
export type UpdateFlightCabinsDTO = FlightCabinSettingDTO[]

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

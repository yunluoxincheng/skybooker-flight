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

/** 管理员端的用户 */
export interface UserAdminVO {
  id: number
  email: string
  nickname: string
  role: "USER" | "ADMIN"
  status: "ENABLED" | "DISABLED"
  createdAt: string
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

/** 销售趋势 */
export interface SalesTrendVO {
  date: string
  orderCount: number
  revenue: number
}

/** 航线表现 */
export interface RoutePerformanceVO {
  route: string
  orderCount: number
  loadFactor: number
  avgRevenue: number
}

/** 航班载客率 */
export interface FlightLoadFactorVO {
  flightNo: string
  route: string
  totalSeats: number
  occupiedSeats: number
  loadFactor: number
}

/** 退款趋势 */
export interface RefundTrendVO {
  date: string
  refundCount: number
  refundAmount: number
}

/** 候补表现 */
export interface WaitlistPerformanceVO {
  date: string
  waitlistCount: number
  convertedCount: number
  conversionRate: number
}

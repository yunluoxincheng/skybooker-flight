/** 订单状态 */
export type OrderStatus =
  | "PENDING_PAYMENT"
  | "ISSUED"
  | "CHANGED"
  | "CHANGE_PENDING"
  | "REFUNDED"
  | "CANCELLED"
  | "VOIDED"

/** 乘机人类型 */
export type PassengerType = "ADULT" | "CHILD" | "INFANT"

/** 订单中的乘机人 */
export interface OrderPassengerVO {
  passengerId: number
  passengerName: string
  passengerType: PassengerType
  seatId: number
  seatNo: string
  ticketPrice: number
}

/** 订单 VO */
export interface OrderVO {
  id: number
  orderNo: string
  flightId: number
  flightNo: string
  userId: number
  userEmail: string
  userNickname: string
  status: OrderStatus
  ticketAmount: number
  airportFee: number
  fuelFee: number
  serviceFee: number
  totalAmount: number
  payTime?: string
  expireTime: string
  createdAt: string
  passengers: OrderPassengerVO[]
  departureCity?: string
  arrivalCity?: string
  departureTime?: string
  arrivalTime?: string
  airlineName?: string
}

export interface AdminOrderQueryDTO {
  status?: string
  orderNo?: string
  userId?: number
  userKeyword?: string
  flightNo?: string
  flightKeyword?: string
  departureDateStart?: string
  departureDateEnd?: string
  page?: number
  size?: number
}

/** 创建订单项 */
export interface OrderItemDTO {
  passengerId: number
  seatId: number
}

/** 创建订单 DTO */
export interface CreateOrderDTO {
  flightId: number
  items: OrderItemDTO[]
}

/** 改签可选航班 — 匹配后端 ChangeOptionVO */
export interface ChangeOptionVO {
  flightId: number
  flightNo: string
  departureTime: string
  arrivalTime: string
  basePrice: number
  remainingSeats: number
  status: string
}

/** 改签座位映射 */
export interface SeatMapping {
  passengerId: number
  newSeatId: number
}

/** 改签 DTO */
export interface ChangeOrderDTO {
  newFlightId: number
  seatMappings: SeatMapping[]
}

export interface AdminChangeDTO extends ChangeOrderDTO {
  reason: string
  force?: boolean
}

/** 退票结果 VO — 匹配后端 RefundVO */
export interface RefundVO {
  id: number
  orderId: number
  refundAmount: number
  feeAmount: number
  status: string
  createdAt: string
}

/** 改签结果 VO — 匹配后端 ChangeOrderResultVO */
export interface ChangeOrderResultVO {
  id: number
  orderNo: string
  status: string
  flightId: number
  totalAmount: number
  passengers: OrderPassengerVO[]
}

export interface CreateAdminOrderDTO {
  userId: number
  flightId: number
  items: OrderItemDTO[]
}

export interface AdminRefundDTO {
  reason: string
  force?: boolean
}

export interface AdminVoidDTO {
  reason: string
}

export interface AdminCancelOrderDTO {
  reason: string
}

export interface AdminNoteDTO {
  adminNote?: string
}

export interface RefundRecordVO {
  id: number
  orderId: number
  userId: number
  refundAmount: number
  feeAmount: number
  reason: string
  status: string
  createdAt: string
}

export interface ChangeRecordVO {
  id: number
  orderId: number
  oldFlightId: number
  newFlightId: number
  oldSeatId: number
  newSeatId: number
  priceDiff: number
  changeFee: number
  status: string
  createdAt: string
}

export interface AdminOrderTimelineItemVO {
  eventType: string
  status?: string
  description?: string
  occurredAt?: string
}

export interface AdminOrderDetailVO extends OrderVO {
  refunds: RefundRecordVO[]
  changes: ChangeRecordVO[]
  timeline: AdminOrderTimelineItemVO[]
  adminNote?: string
}

export type OrderDeleteType = "cancel" | "delete"

/** 订单状态 */
export type OrderStatus =
  | "PENDING_PAYMENT"
  | "ISSUED"
  | "CHANGED"
  | "REFUNDED"
  | "CANCELLED"
  | "EXPIRED"

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

/* PENDING BACKEND: 管理端创建、更新、退票、增强详情、删除接口尚未提供，以下类型暂未启用。
export interface CreateAdminOrderDTO {
  userId: number
  flightId: number
  items: OrderItemDTO[]
}

export interface UpdateAdminOrderDTO {
  items?: OrderItemDTO[]
}

export interface AdminRefundDTO {
  reason: string
  refundFee?: number
}

export interface RefundRecordVO {
  id: number
  orderId: number
  refundAmount: number
  feeAmount: number
  reason: string
  status: string
  createdAt: string
  completedAt?: string
}

export interface ChangeRecordVO {
  id: number
  orderNo: string
  oldFlightNo: string
  newFlightNo: string
  oldDepartureTime: string
  newDepartureTime: string
  feeAmount: number
  priceDiff: number
  status: string
  createdAt: string
}

export interface PaymentInfoVO {
  payTime?: string
  payAmount?: number
  payMethod?: string
  tradeNo?: string
}

export interface OrderStatusTimelineItemVO {
  status: OrderStatus
  title?: string
  description?: string
  operatorName?: string
  occurredAt?: string
}

export interface AdminOrderDetailVO extends OrderVO {
  refundRecords: RefundRecordVO[]
  changeRecords: ChangeRecordVO[]
  paymentInfo?: PaymentInfoVO
  statusTimeline: OrderStatusTimelineItemVO[]
}

export type OrderDeleteType = "cancel" | "delete"
*/

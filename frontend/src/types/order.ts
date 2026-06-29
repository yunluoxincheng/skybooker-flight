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

/** 改签可选航班 */
export interface ChangeOptionVO {
  flightId: number
  flightNo: string
  airlineName: string
  departureTime: string
  arrivalTime: string
  durationMinutes: number
  basePrice: number
  remainingSeats: number
  priceDiff: number
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

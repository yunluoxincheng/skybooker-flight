import type { CabinClass } from "./flight"
import type { PassengerType } from "./order"

/** 候补状态 — 匹配后端 WaitlistStatus */
export type WaitlistStatus =
  | "PENDING_PAYMENT"
  | "WAITING"
  | "SUCCESS"
  | "FAILED"
  | "CANCELLED"
  | "REFUNDED"
  | "EXPIRED"

/** 候补乘客 */
export interface WaitlistPassengerVO {
  passengerId: number
  passengerName: string
  passengerType: PassengerType
  seatId?: number
  seatNo?: string
}

/** 候补 VO — 匹配后端 WaitlistVO */
export interface WaitlistVO {
  id: number
  waitlistNo: string
  flightId: number
  flightNo: string
  userId: number
  passengerCount: number
  cabinClass: CabinClass
  status: WaitlistStatus
  payAmount: number
  paidAt?: string
  ticketOrderId?: number
  refundAmount?: number
  refundTime?: string
  lastSkipReason?: string
  expireTime: string
  createdAt: string
  passengers: WaitlistPassengerVO[]
}

/** 创建候补 DTO — 匹配后端 CreateWaitlistDTO */
export interface CreateWaitlistDTO {
  flightId: number
  cabinClass: CabinClass
  passengerIds: number[]
}

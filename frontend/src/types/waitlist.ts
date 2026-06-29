/** 候补状态 */
export type WaitlistStatus = "WAITING" | "NOTIFIED" | "CONVERTED" | "CANCELLED"

/** 候补 VO */
export interface WaitlistVO {
  id: number
  waitlistNo: string
  flightId: number
  flightNo: string
  userId: number
  userEmail: string
  passengerCount: number
  status: WaitlistStatus
  createdAt: string
  notifiedAt?: string
  expireTime: string
}

/** 创建候补 DTO */
export interface CreateWaitlistDTO {
  flightId: number
  passengerIds: number[]
}

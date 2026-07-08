/** 航班状态 */
export type FlightStatus =
  | "ON_TIME"
  | "DELAYED"
  | "CANCELLED"
  | "BOARDING"
  | "DEPARTED"
  | "ARRIVED"

/** 上架状态 */
export type PublishStatus = "PUBLISHED" | "UNPUBLISHED"

/** 舱位类型 */
export type CabinClass = "ECONOMY" | "BUSINESS" | "FIRST"

/** 舱位顺序 */
export const CABIN_CLASS_ORDER: CabinClass[] = ["ECONOMY", "BUSINESS", "FIRST"]

/** 舱位标签 */
export const CABIN_CLASS_LABEL: Record<CabinClass, string> = {
  ECONOMY: "经济舱",
  BUSINESS: "公务舱",
  FIRST: "头等舱",
}

/** 舱位摘要 */
export interface FlightCabinVO {
  cabinClass: CabinClass
  price: number
  totalSeats: number
  availableSeats?: number
  remainingSeats?: number
}

/** 航班 VO */
export interface FlightVO {
  id: number
  flightNo: string
  airlineId: number
  airlineCode: string
  airlineName: string
  departureAirportId: number
  departureAirportCode: string
  departureAirportName: string
  departureCity: string
  arrivalAirportId: number
  arrivalAirportCode: string
  arrivalAirportName: string
  arrivalCity: string
  departureTime: string
  arrivalTime: string
  durationMinutes: number
  basePrice: number
  remainingSeats: number
  totalSeats: number
  status: FlightStatus
  publishStatus: PublishStatus
  directFlag: number   // 后端 Integer: 0=经停 1=直飞
  baggageAllowance: string
  punctualityRate: number
  cabins?: FlightCabinVO[]
}

/** 航班搜索参数 */
export interface FlightSearchParams {
  departureCity?: string
  arrivalCity?: string
  departureDate?: string
  departureDateStart?: string
  departureDateEnd?: string
  airlineId?: number
  minPrice?: number
  maxPrice?: number
  directOnly?: boolean
  departureTimeStart?: string // "HH:mm"
  departureTimeEnd?: string   // "HH:mm"
  sort?: string
  page?: number
  size?: number
}

/** 座位 VO */
export interface FlightSeatVO {
  id: number
  seatNo: string
  cabinClass: "ECONOMY" | "BUSINESS" | "FIRST"
  seatType: "WINDOW" | "AISLE" | "MIDDLE"
  price: number
  status: "AVAILABLE" | "OCCUPIED" | "LOCKED"
  version: number
}

/** 座位类型 */
export type SeatType = "WINDOW" | "AISLE" | "MIDDLE"

/** 座位状态 */
export type SeatStatus = "AVAILABLE" | "OCCUPIED" | "LOCKED"

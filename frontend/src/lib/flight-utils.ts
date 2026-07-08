import type { FlightVO, FlightStatus } from "@/types/flight"

export interface BookableResult {
  bookable: boolean
  reason?: string
}

const STATUS_REASONS: Partial<Record<FlightStatus, string>> = {
  CANCELLED: "该航班已取消",
  BOARDING: "该航班已停止售票，正在登机中",
  DEPARTED: "该航班已出发",
  ARRIVED: "该航班已到达",
}

export function isFlightBookable(flight: FlightVO): BookableResult {
  if (flight.publishStatus !== "PUBLISHED") {
    return { bookable: false, reason: "该航班未上架，暂不可预订" }
  }

  if (new Date(flight.departureTime) <= new Date()) {
    return { bookable: false, reason: "该航班已出发，无法预订" }
  }

  if (flight.status !== "ON_TIME" && flight.status !== "DELAYED") {
    return {
      bookable: false,
      reason: STATUS_REASONS[flight.status] || "该航班当前不可预订",
    }
  }

  if (flight.remainingSeats <= 0) {
    return { bookable: false, reason: "该航班已售罄" }
  }

  return { bookable: true }
}

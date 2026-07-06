import type { CabinClass, FlightCabinVO } from "@/types/flight"

export function getFallbackCabinPrice(basePrice: number, cabinClass: CabinClass) {
  switch (cabinClass) {
    case "BUSINESS":
      return Math.round(basePrice * 2.5)
    case "FIRST":
      return Math.round(basePrice * 5)
    default:
      return basePrice
  }
}

export function getCabinAvailableSeats(cabin?: Partial<FlightCabinVO> | null) {
  return cabin?.availableSeats ?? cabin?.remainingSeats ?? 0
}

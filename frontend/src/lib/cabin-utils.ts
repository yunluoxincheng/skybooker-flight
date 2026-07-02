import type { CabinClass } from "@/types/flight"

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

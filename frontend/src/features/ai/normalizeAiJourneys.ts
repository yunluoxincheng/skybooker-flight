import type { AiJourneyCardVO } from "@/types/ai"
import type { FlightStatus, FlightVO } from "@/types/flight"

type LegacyAiFlight = {
  flightId?: number
  flightNo?: string
  airlineName?: string
  departureCity?: string
  arrivalCity?: string
  departureTime?: string
  arrivalTime?: string
  durationMinutes?: number
  price?: number
  remainingSeats?: number
  status?: string
  detailUrl?: string
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null

const flightStatus = (value?: string): FlightStatus =>
  value === "DELAYED" || value === "CANCELLED" ? value : "ON_TIME"

function normalizeLegacyFlight(value: LegacyAiFlight): AiJourneyCardVO | null {
  if (
    typeof value.flightId !== "number" ||
    !value.flightNo ||
    !value.departureTime ||
    !value.arrivalTime
  ) {
    return null
  }
  const segment = {
    id: value.flightId,
    flightNo: value.flightNo,
    airlineName: value.airlineName || "航空公司",
    departureCity: value.departureCity || "出发地",
    arrivalCity: value.arrivalCity || "目的地",
    departureTime: value.departureTime,
    arrivalTime: value.arrivalTime,
    durationMinutes: value.durationMinutes || 0,
    basePrice: value.price || 0,
    remainingSeats: value.remainingSeats || 0,
    status: flightStatus(value.status),
    baggageAllowance: "以航司规定为准",
    punctualityRate: 0,
  } as FlightVO
  return {
    id: value.flightId,
    journeyType: "DIRECT",
    segments: [segment],
    originCity: segment.departureCity,
    destinationCity: segment.arrivalCity,
    totalDurationMinutes: value.durationMinutes || 0,
    estimatedAmount: value.price || 0,
    availableSeats: value.remainingSeats || 0,
    sellable: (value.remainingSeats || 0) > 0,
    detailUrl: value.detailUrl,
  }
}

export function normalizeAiJourneys(values: unknown): AiJourneyCardVO[] | undefined {
  if (!Array.isArray(values)) return undefined
  const normalized = values.flatMap((value) => {
    if (!isRecord(value)) return []
    if (Array.isArray(value.segments) && value.segments.length > 0) {
      return [value as unknown as AiJourneyCardVO]
    }
    const legacy = normalizeLegacyFlight(value as LegacyAiFlight)
    return legacy ? [legacy] : []
  })
  return normalized.length ? normalized : undefined
}

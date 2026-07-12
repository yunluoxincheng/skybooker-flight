import { describe, expect, it } from "vitest"
import { normalizeAiJourneys } from "./normalizeAiJourneys"

describe("normalizeAiJourneys", () => {
  it("keeps unified journey responses", () => {
    const journey = { id: 8, journeyType: "CONNECTING", segments: [{ id: 1 }, { id: 2 }] }
    expect(normalizeAiJourneys([journey])).toEqual([journey])
  })

  it("converts persisted legacy flight cards to direct journeys", () => {
    const result = normalizeAiJourneys([{
      flightId: 42,
      flightNo: "MU5101",
      airlineName: "东方航空",
      departureCity: "上海",
      arrivalCity: "北京",
      departureTime: "2026-08-01T08:00:00",
      arrivalTime: "2026-08-01T10:00:00",
      durationMinutes: 120,
      price: 680,
      remainingSeats: 3,
      status: "ON_TIME",
    }])
    expect(result?.[0]).toMatchObject({
      id: 42,
      journeyType: "DIRECT",
      estimatedAmount: 680,
      availableSeats: 3,
      segments: [{ id: 42, flightNo: "MU5101", status: "ON_TIME" }],
    })
  })

  it("drops malformed historical cards instead of crashing", () => {
    expect(normalizeAiJourneys([{ flightId: 1 }, null])).toBeUndefined()
  })
})

import { describe, expect, it } from "vitest"
import { toConnectingPairPayload, validateConnection, type SegmentInput } from "./connectingFlightPair"

const segment = (from: number, to: number, departureTime: string, arrivalTime: string): SegmentInput => ({
  flightNo: "CA1401", airlineId: 1, departureAirportId: from, arrivalAirportId: to,
  departureTime, arrivalTime, durationMinutes: 120, basePrice: 500, totalSeats: 180, baggageAllowance: "20kg",
})

describe("connecting flight pair", () => {
  const first = segment(1, 2, "2026-08-01T08:00", "2026-08-01T10:00")

  it.each([[89, "2026-08-01T11:29", false], [90, "2026-08-01T11:30", true],
    [360, "2026-08-01T16:00", true], [361, "2026-08-01T16:01", false]])("validates %i minute boundary", (minutes, departure, valid) => {
    const second = segment(2, 3, departure as string, "2026-08-01T20:00")
    expect(validateConnection(first, second) === null).toBe(valid)
  })

  it("rejects disconnected airports and circular routes", () => {
    expect(validateConnection(first, segment(4, 3, "2026-08-01T12:00", "2026-08-01T14:00"))).not.toBeNull()
    expect(validateConnection(first, segment(2, 1, "2026-08-01T12:00", "2026-08-01T14:00"))).not.toBeNull()
  })

  it("normalizes the request and forces both rows to direct segments", () => {
    const second = segment(2, 3, "2026-08-01T12:00", "2026-08-01T14:00")
    const payload = toConnectingPairPayload(first, second)
    expect(payload.firstSegment.departureTime).toBe("2026-08-01T08:00:00")
    expect(payload.firstSegment.directFlag).toBe(true)
    expect(payload.secondSegment.directFlag).toBe(true)
  })
})

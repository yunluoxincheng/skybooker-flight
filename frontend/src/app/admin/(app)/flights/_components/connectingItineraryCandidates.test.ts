import { describe, expect, it } from "vitest"
import { buildCandidateQuery, changesFirstFlight } from "./connectingItineraryCandidates"

describe("connecting itinerary server candidates", () => {
  it("keeps keyword and requested page so flights outside the first 100 remain searchable", () => {
    expect(buildCandidateQuery(6, " CA999 ")).toMatchObject({ page: 6, size: 20, keyword: "CA999" })
  })

  it("omits blank keywords without falling back to a fixed first-page data set", () => {
    expect(buildCandidateQuery(2, " ")).toMatchObject({ page: 2, size: 20, keyword: undefined })
  })

  it("passes date and airport filters to the server", () => {
    expect(buildCandidateQuery(1, "", 20, { startDate: "2026-08-01", endDate: "2026-08-03", departureAirportId: 1, arrivalAirportId: 2 }))
      .toMatchObject({ startDate: "2026-08-01", endDate: "2026-08-03", departureAirportId: 1, arrivalAirportId: 2 })
  })

  it("signals that changing the first segment must clear and recompute second candidates", () => {
    expect(changesFirstFlight(101, 202)).toBe(true)
    expect(changesFirstFlight(202, 202)).toBe(false)
  })
})

export interface CandidateFilters {
  startDate?: string
  endDate?: string
  departureAirportId?: number | null
  arrivalAirportId?: number | null
}

export function buildCandidateQuery(page: number, keyword: string, size = 20, filters: CandidateFilters = {}) {
  return {
    page, size, keyword: keyword.trim() || undefined,
    startDate: filters.startDate || undefined,
    endDate: filters.endDate || undefined,
    departureAirportId: filters.departureAirportId || undefined,
    arrivalAirportId: filters.arrivalAirportId || undefined,
  }
}

export function changesFirstFlight(previousId: number | null, nextId: number) {
  return previousId !== nextId
}

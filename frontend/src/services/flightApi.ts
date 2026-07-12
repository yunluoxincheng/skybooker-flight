import { get, post } from "@/lib/request"
import type { FareCalendarVO, FlightVO, FlightSearchParams, FlightSeatVO, ItineraryVO } from "@/types/flight"
import type { PageData } from "@/types/api"

/** 搜索航班 */
export function searchFlights(params: FlightSearchParams) {
  return get<PageData<FlightVO>>("/flights", params as Record<string, string | number | boolean | undefined>)
}

export function searchItineraries(params: FlightSearchParams) {
  return get<PageData<ItineraryVO>>("/itineraries/search", params as Record<string, string | number | boolean | undefined>)
}

export function quoteItinerary(data: { segmentFlightIds: number[]; passengerIds: number[]; cabinPreferences?: string[] }) {
  return post<ItineraryVO>("/itineraries/quote", data, { auth: "user" })
}

export function getFareCalendar(params: { departureCity: string; arrivalCity: string; startDate: string; days: number }) {
  return get<FareCalendarVO[]>("/itineraries/fare-calendar", params)
}

export function getConnectingItinerary(id: number) {
  return get<ItineraryVO>(`/itineraries/connecting/${id}`)
}

/** 获取航班详情 */
export function getFlightById(id: number) {
  return get<FlightVO>(`/flights/${id}`)
}

/** 获取航班座位图 */
export function getFlightSeats(id: number) {
  return get<FlightSeatVO[]>(`/flights/${id}/seats`)
}

import { get } from "@/lib/request"
import type { FlightVO, FlightSearchParams, FlightSeatVO } from "@/types/flight"
import type { PageData } from "@/types/api"

/** 搜索航班 */
export function searchFlights(params: FlightSearchParams) {
  return get<PageData<FlightVO>>("/flights", params as Record<string, string | number | boolean | undefined>)
}

/** 获取航班详情 */
export function getFlightById(id: number) {
  return get<FlightVO>(`/flights/${id}`)
}

/** 获取航班座位图 */
export function getFlightSeats(id: number) {
  return get<FlightSeatVO[]>(`/flights/${id}/seats`)
}

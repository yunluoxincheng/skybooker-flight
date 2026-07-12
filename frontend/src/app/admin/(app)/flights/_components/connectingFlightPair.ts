import type { FlightFormDTO } from "@/types/admin"

export type SegmentInput = Omit<FlightFormDTO, "directFlag"> & { directFlag?: boolean }

export function validateConnection(first: SegmentInput, second: SegmentInput): string | null {
  if (first.arrivalAirportId !== second.departureAirportId) return "中转机场必须连续"
  if (first.departureAirportId === second.arrivalAirportId) return "起终点不能形成环线"
  const transfer = (new Date(second.departureTime).getTime() - new Date(first.arrivalTime).getTime()) / 60_000
  return Number.isFinite(transfer) && transfer >= 90 && transfer <= 360 ? null : "中转时间须为 90 分钟至 6 小时"
}

export function toConnectingPairPayload(first: SegmentInput, second: SegmentInput) {
  const normalize = (segment: SegmentInput): FlightFormDTO => ({
    ...segment,
    departureTime: segment.departureTime.length === 16 ? `${segment.departureTime}:00` : segment.departureTime,
    arrivalTime: segment.arrivalTime.length === 16 ? `${segment.arrivalTime}:00` : segment.arrivalTime,
    directFlag: true,
  })
  return { firstSegment: normalize(first), secondSegment: normalize(second) }
}

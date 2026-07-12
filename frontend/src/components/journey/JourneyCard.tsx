import Link from "next/link"
import { Luggage, Plane, TrendingUp } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { FlightPriceTag } from "@/components/common/FlightPriceTag"
import { formatDate, formatTime, getCrossDayLabel } from "@/lib/date-utils"
import type { ItineraryVO } from "@/types/flight"

const duration = (minutes: number) => `${Math.floor(minutes / 60)}时${minutes % 60 ? `${minutes % 60}分` : ""}`

export function JourneyCard({ journey }: { journey: ItineraryVO }) {
  const first = journey.segments[0]
  const last = journey.segments.at(-1)!
  const connecting = journey.journeyType === "CONNECTING"
  const detailUrl = connecting ? `/itineraries/connecting/${journey.id}` : `/flights/${first.id}`
  return <Card className="transition-shadow hover:shadow-md"><CardContent className="p-5">
    <div className="flex flex-col gap-4 lg:flex-row lg:items-center">
      <div className="flex min-w-0 shrink-0 items-center gap-3 lg:w-[180px]">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10"><Plane className="h-5 w-5 text-primary"/></div>
        <div className="min-w-0"><p className="truncate text-sm font-medium">{connecting && first.airlineName !== last.airlineName ? `${first.airlineName} / ${last.airlineName}` : first.airlineName}</p><p className="text-xs text-muted-foreground">{journey.segments.map((segment) => segment.flightNo).join(" · ")}</p></div>
      </div>
      <div className="flex min-w-0 flex-1 items-center gap-4">
        <div className="shrink-0 text-center"><p className="text-xl font-bold tabular-nums">{formatTime(first.departureTime)}</p><p className="text-xs text-muted-foreground">{formatDate(first.departureTime)}</p><p className="text-sm text-muted-foreground">{first.departureCity}</p></div>
        <div className="flex min-w-[100px] flex-1 flex-col items-center px-2"><div className="flex w-full items-center"><div className="h-2 w-2 rounded-full bg-primary"/><div className="h-px flex-1 bg-slate-300"/><Plane className="h-4 w-4 -rotate-90 text-primary"/><div className="h-px flex-1 bg-slate-300"/><div className="h-2 w-2 rounded-full bg-primary"/></div><p className="mt-1 text-xs text-muted-foreground">全程 {duration(journey.totalDurationMinutes)}</p><p className="text-center text-xs text-muted-foreground">{connecting ? `中转${first.arrivalCity} · ${first.arrivalAirportCode} · 停留${duration(journey.connectionDurationMinutes || 0)}` : "直飞"}</p></div>
        <div className="shrink-0 text-center"><p className="text-xl font-bold tabular-nums">{formatTime(last.arrivalTime)}</p>{getCrossDayLabel(first.departureTime, last.arrivalTime) && <p className="text-xs text-muted-foreground">{getCrossDayLabel(first.departureTime, last.arrivalTime)}</p>}<p className="text-sm text-muted-foreground">{last.arrivalCity}</p></div>
      </div>
      <div className="flex shrink-0 items-center justify-end gap-4 lg:w-[200px]"><div className="text-right"><FlightPriceTag price={journey.estimatedAmount} className="text-lg"/><p className="text-xs text-muted-foreground">每人起 · 余 {journey.availableSeats} 座</p></div><Button render={<Link href={detailUrl}>查看详情</Link>} size="sm" nativeButton={false}/></div>
    </div>
    <div className="mt-3 flex flex-wrap gap-3 border-t border-slate-100 pt-3 text-xs text-muted-foreground"><span className="inline-flex items-center gap-1"><Luggage className="h-3 w-3"/>{connecting ? "行李规则按航段适用" : first.baggageAllowance}</span>{journey.segments.map((segment) => <span key={segment.id} className="inline-flex items-center gap-1"><TrendingUp className="h-3 w-3"/>{segment.flightNo} 准点率 {segment.punctualityRate}%</span>)}</div>
  </CardContent></Card>
}

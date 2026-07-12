import Link from "next/link"
import { Luggage, Plane, TrendingUp } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { FlightPriceTag } from "@/components/common/FlightPriceTag"
import { FlightStatusBadge } from "@/components/common/FlightStatusBadge"
import { formatDateFull, formatTime } from "@/lib/date-utils"
import type { ItineraryVO } from "@/types/flight"

const duration = (minutes: number) => `${Math.floor(minutes / 60)}时${minutes % 60 ? `${minutes % 60}分` : ""}`

export function JourneyDetail({ journey }: { journey: ItineraryVO }) {
  const connecting = journey.journeyType === "CONNECTING"
  const bookingUrl = connecting ? `/booking/connecting/${journey.id}` : `/booking/${journey.segments[0].id}`
  return <div className="space-y-6">
    <Card><CardContent className="p-6"><div className="mb-6 flex flex-wrap items-center gap-3"><h1 className="text-xl font-bold">{journey.originCity} → {journey.destinationCity}</h1><Badge variant="outline">{connecting ? "一次中转" : "直飞"}</Badge><span className="text-sm text-muted-foreground">全程 {duration(journey.totalDurationMinutes)}</span></div>
      <div className="space-y-4">{journey.segments.map((segment, index) => <div key={segment.id}><div className="grid grid-cols-[1fr_auto_1fr] items-center gap-4"><div><p className="text-2xl font-bold tabular-nums">{formatTime(segment.departureTime)}</p><p className="font-medium">{segment.departureCity} · {segment.departureAirportCode}</p><p className="text-xs text-muted-foreground">{segment.departureAirportName} · {formatDateFull(segment.departureTime)}</p></div><div className="min-w-32 text-center"><div className="flex items-center"><div className="h-2 w-2 rounded-full bg-primary"/><div className="h-px flex-1 bg-slate-300"/><Plane className="h-4 w-4 -rotate-90 text-primary"/><div className="h-px flex-1 bg-slate-300"/><div className="h-2 w-2 rounded-full bg-primary"/></div><p className="mt-1 text-xs text-muted-foreground">第 {index + 1} 段 · {segment.flightNo} · {duration(segment.durationMinutes)}</p></div><div className="text-right"><p className="text-2xl font-bold tabular-nums">{formatTime(segment.arrivalTime)}</p><p className="font-medium">{segment.arrivalCity} · {segment.arrivalAirportCode}</p><p className="text-xs text-muted-foreground">{segment.arrivalAirportName} · {formatDateFull(segment.arrivalTime)}</p></div></div>{connecting && index === 0 && <div className="my-4 rounded-lg border border-dashed bg-muted/40 px-4 py-3 text-center text-sm">在 {journey.connectionAirportName}（{journey.connectionAirportCode}）中转，停留 {duration(journey.connectionDurationMinutes || 0)}；请预留换乘时间并确认行李规则</div>}</div>)}</div>
    </CardContent></Card>
    <div className="grid gap-6 md:grid-cols-[1fr_280px]"><div className="space-y-4">{journey.segments.map((segment, index) => <Card key={segment.id}><CardContent className="space-y-3 p-5"><div className="flex items-center gap-2"><h2 className="font-semibold">第 {index + 1} 航段</h2><Badge variant="outline">{segment.airlineName} {segment.flightNo}</Badge><FlightStatusBadge status={segment.status}/></div><div className="flex flex-wrap gap-4 text-sm text-muted-foreground"><span className="inline-flex items-center gap-1"><Luggage className="h-4 w-4"/>{segment.baggageAllowance}</span><span className="inline-flex items-center gap-1"><TrendingUp className="h-4 w-4"/>准点率 {segment.punctualityRate}%</span><span>余 {segment.remainingSeats} 座</span></div></CardContent></Card>)}</div><Card className="h-fit md:sticky md:top-24"><CardContent className="space-y-4 p-5 text-center"><div><FlightPriceTag price={journey.estimatedAmount} className="text-2xl"/><p className="mt-1 text-xs text-muted-foreground">每人起 · 两段合计</p></div><Button className="w-full" size="lg" disabled={!journey.sellable} render={journey.sellable ? <Link href={bookingUrl}>立即预订</Link> : undefined} nativeButton={!journey.sellable}>{journey.sellable ? "立即预订" : "暂不可预订"}</Button><p className="text-xs text-muted-foreground">共享余票 {journey.availableSeats} 座，最终价格以选座结果为准</p></CardContent></Card></div>
  </div>
}

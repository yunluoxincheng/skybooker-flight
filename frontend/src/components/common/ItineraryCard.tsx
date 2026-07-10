import Link from "next/link"
import { ArrowRight, Clock3, Plane, Route } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { FlightCard } from "./FlightCard"
import { formatDate, formatTime } from "@/lib/date-utils"
import type { CabinClass, ItineraryVO } from "@/types/flight"

export function ItineraryCard({ itinerary, cabinClass }: { itinerary: ItineraryVO; cabinClass?: CabinClass }) {
  if (itinerary.journeyType === "DIRECT") return <FlightCard flight={itinerary.segments[0]} cabinClass={cabinClass} />
  const [first, second] = itinerary.segments
  const duration = (minutes: number) => `${Math.floor(minutes / 60)}时${minutes % 60 ? `${minutes % 60}分` : ""}`
  const bookingUrl = `/booking/connecting?segments=${first.id},${second.id}`
  const nextDay = new Date(second.departureTime).toDateString() !== new Date(first.departureTime).toDateString()
  return (
    <Card className="overflow-hidden border-sky-200 bg-gradient-to-br from-white to-sky-50/60 transition-shadow hover:shadow-md">
      <CardContent className="p-0">
        <div className="flex items-center justify-between border-b border-sky-100 px-5 py-3">
          <div className="flex items-center gap-2"><Badge className="bg-sky-700">中转</Badge><span className="text-sm font-medium">{first.departureCity} → {first.arrivalCity} → {second.arrivalCity}</span></div>
          {nextDay && <span className="text-xs text-amber-700">第二段次日出发</span>}
        </div>
        <div className="grid gap-4 p-5 lg:grid-cols-[1fr_auto_1fr_auto] lg:items-center">
          {[first, second].map((segment, index) => <div key={segment.id} className="rounded-xl bg-white/80 p-3 ring-1 ring-slate-200">
            <div className="mb-2 flex items-center gap-2 text-sm"><Plane className="h-4 w-4 text-sky-700"/><b>{segment.flightNo}</b><span className="text-muted-foreground">{segment.airlineName}</span></div>
            <div className="flex items-end justify-between"><div><b className="text-lg">{formatTime(segment.departureTime)}</b><p className="text-xs text-muted-foreground">{formatDate(segment.departureTime)} · {segment.departureAirportCode}</p></div><ArrowRight className="mb-2 h-4 w-4 text-slate-400"/><div className="text-right"><b className="text-lg">{formatTime(segment.arrivalTime)}</b><p className="text-xs text-muted-foreground">{segment.arrivalAirportCode}</p></div></div>
            <p className="mt-2 text-xs text-muted-foreground">第 {index + 1} 段 · {duration(segment.durationMinutes)}</p>
          </div>)}
          <div className="hidden lg:flex flex-col items-center text-sky-800"><Route className="h-5 w-5"/><span className="text-xs">{first.arrivalAirportCode}</span><span className="text-xs">停留 {duration(itinerary.connectionDurationMinutes || 0)}</span></div>
          <div className="flex items-center justify-between gap-5 lg:flex-col lg:items-end"><div className="text-right"><p className="flex items-center justify-end gap-1 text-xs text-muted-foreground"><Clock3 className="h-3 w-3"/>全程 {duration(itinerary.totalDurationMinutes)}</p><p className="text-xl font-bold text-rose-600">¥{itinerary.estimatedAmount.toLocaleString()}</p><p className="text-xs text-muted-foreground">每人起 · 余 {itinerary.availableSeats} 座</p></div><Button render={<Link href={bookingUrl}>选择联程</Link>} nativeButton={false}/></div>
        </div>
      </CardContent>
    </Card>
  )
}

import Link from "next/link"
import { Clock, Plane } from "lucide-react"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { FlightStatusBadge } from "@/components/common/FlightStatusBadge"
import { formatDate, formatTime, getCrossDayLabel } from "@/lib/date-utils"
import type { AiFlightCardVO } from "@/types/ai"

interface AiFlightCardProps {
  flight: AiFlightCardVO
  className?: string
}

export function AiFlightCard({ flight, className }: AiFlightCardProps) {
  const formatDuration = (minutes: number) => {
    const h = Math.floor(minutes / 60)
    const m = minutes % 60
    return `${h}时${m > 0 ? `${m}分` : ""}`
  }

  const crossDayLabel = getCrossDayLabel(flight.departureTime, flight.arrivalTime)

  return (
    <Card className={`hover:shadow-md transition-shadow ${className || ""}`}>
      <CardContent className="p-4">
        <div className="flex flex-col sm:flex-row sm:items-center gap-3">
          {/* 左：航司信息 */}
          <div className="flex items-center gap-2.5 min-w-0 sm:w-[160px] shrink-0">
            <div className="flex items-center justify-center h-9 w-9 rounded-lg bg-primary/10 shrink-0">
              <Plane className="h-4 w-4 text-primary" />
            </div>
            <div className="min-w-0">
              <p className="text-sm font-medium truncate">{flight.airlineName}</p>
              <p className="text-xs text-muted-foreground">{flight.flightNo}</p>
            </div>
          </div>

          {/* 中：行程信息 */}
          <div className="flex items-center gap-3 flex-1 min-w-0">
            <div className="text-center shrink-0">
              <p className="text-lg font-bold tabular-nums">{formatTime(flight.departureTime)}</p>
              <p className="text-[10px] text-muted-foreground">{formatDate(flight.departureTime)}</p>
              <p className="text-xs text-muted-foreground">{flight.departureCity}</p>
            </div>

            <div className="flex-1 flex flex-col items-center px-1 min-w-[60px]">
              <div className="flex items-center w-full">
                <div className="h-1.5 w-1.5 rounded-full bg-primary shrink-0" />
                <div className="h-px flex-1 bg-slate-300" />
                <Plane className="h-3.5 w-3.5 text-primary shrink-0 -rotate-90" />
                <div className="h-px flex-1 bg-slate-300" />
                <div className="h-1.5 w-1.5 rounded-full bg-primary shrink-0" />
              </div>
              <div className="flex items-center gap-1.5 mt-0.5">
                <Clock className="h-3 w-3 text-muted-foreground" />
                <p className="text-xs text-muted-foreground">{formatDuration(flight.durationMinutes)}</p>
              </div>
            </div>

            <div className="text-center shrink-0">
              <p className="text-lg font-bold tabular-nums">{formatTime(flight.arrivalTime)}</p>
              {crossDayLabel && (
                <p className="text-[10px] text-muted-foreground">{crossDayLabel}</p>
              )}
              <p className="text-xs text-muted-foreground">{flight.arrivalCity}</p>
            </div>
          </div>

          {/* 右：价格 + 操作 */}
          <div className="flex items-center gap-3 sm:w-[180px] shrink-0 justify-end">
            <div className="text-right">
              <div className="flex items-center gap-1.5 justify-end">
                <FlightStatusBadge status={flight.status} className="text-xs" />
                {flight.remainingSeats > 0 && (
                  <span className="text-xs text-muted-foreground">余 {flight.remainingSeats} 座</span>
                )}
              </div>
              <p className="text-lg text-[#f97316] font-bold mt-1">
                ¥{flight.price.toLocaleString()}
              </p>
            </div>
            <Button
              render={<Link href={flight.detailUrl || `/flights/${flight.flightId}`}>查看</Link>}
              size="sm"
              nativeButton={false}
            />
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

import Link from "next/link"
import { Clock, Plane, Luggage, TrendingUp } from "lucide-react"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { FlightStatusBadge } from "./FlightStatusBadge"
import { FlightPriceTag } from "./FlightPriceTag"
import type { FlightVO } from "@/types/flight"

interface FlightCardProps {
  flight: FlightVO
  showBookButton?: boolean
  showStatus?: boolean
  className?: string
  actionSlot?: React.ReactNode
}

export function FlightCard({
  flight,
  showBookButton = true,
  showStatus = false,
  className,
  actionSlot,
}: FlightCardProps) {
  const formatTime = (iso: string) => {
    const d = new Date(iso)
    return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`
  }

  const formatDuration = (minutes: number) => {
    const h = Math.floor(minutes / 60)
    const m = minutes % 60
    return `${h}时${m > 0 ? `${m}分` : ""}`
  }

  return (
    <Card className={`hover:shadow-md transition-shadow ${className || ""}`}>
      <CardContent className="p-5">
        <div className="flex flex-col lg:flex-row lg:items-center gap-4">
          {/* 左：航司信息 */}
          <div className="flex items-center gap-3 min-w-0 lg:w-[180px] shrink-0">
            <div className="flex items-center justify-center h-10 w-10 rounded-lg bg-primary/10 shrink-0">
              <Plane className="h-5 w-5 text-primary" />
            </div>
            <div className="min-w-0">
              <p className="text-sm font-medium truncate">{flight.airlineName}</p>
              <p className="text-xs text-muted-foreground">{flight.flightNo}</p>
            </div>
          </div>

          {/* 中：行程信息 */}
          <div className="flex items-center gap-4 flex-1 min-w-0">
            {/* 出发 */}
            <div className="text-center shrink-0">
              <p className="text-xl font-bold tabular-nums">{formatTime(flight.departureTime)}</p>
              <p className="text-sm text-muted-foreground">{flight.departureCity}</p>
            </div>

            {/* 飞行时间线 */}
            <div className="flex-1 flex flex-col items-center px-2 min-w-[80px]">
              <div className="flex items-center w-full">
                <div className="h-2 w-2 rounded-full bg-primary shrink-0" />
                <div className="h-px flex-1 bg-slate-300" />
                <Plane className="h-4 w-4 text-primary shrink-0 -rotate-90" />
                <div className="h-px flex-1 bg-slate-300" />
                <div className="h-2 w-2 rounded-full bg-primary shrink-0" />
              </div>
              <p className="text-xs text-muted-foreground mt-1">{formatDuration(flight.durationMinutes)}</p>
              <p className="text-xs text-muted-foreground">{flight.directFlag ? "直飞" : "经停"}</p>
            </div>

            {/* 到达 */}
            <div className="text-center shrink-0">
              <p className="text-xl font-bold tabular-nums">{formatTime(flight.arrivalTime)}</p>
              <p className="text-sm text-muted-foreground">{flight.arrivalCity}</p>
            </div>
          </div>

          {/* 右：价格 + 操作 */}
          <div className="flex items-center gap-4 lg:w-[200px] shrink-0 justify-end">
            <div className="text-right">
              {showStatus && <FlightStatusBadge status={flight.status} />}
              <div>
                <FlightPriceTag price={flight.basePrice} className="text-lg" />
              </div>
              {flight.remainingSeats > 0 && (
                <p className="text-xs text-muted-foreground">余 {flight.remainingSeats} 座</p>
              )}
            </div>
            {actionSlot || (
              showBookButton && (
                <Button render={<Link href={`/flights/${flight.id}`}>查看详情</Link>} size="sm" />
              )
            )}
          </div>
        </div>

        {/* 底部标签 */}
        <div className="flex flex-wrap gap-2 mt-3 pt-3 border-t border-slate-100">
          {flight.baggageAllowance && (
            <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
              <Luggage className="h-3 w-3" /> {flight.baggageAllowance}
            </span>
          )}
          {flight.punctualityRate > 0 && (
            <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
              <TrendingUp className="h-3 w-3" /> 准点率 {flight.punctualityRate}%
            </span>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

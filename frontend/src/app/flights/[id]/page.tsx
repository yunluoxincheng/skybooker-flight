"use client"

import { useEffect, useState } from "react"
import { useParams, useRouter } from "next/navigation"
import Link from "next/link"
import { Plane, Clock, Luggage, TrendingUp, MapPin, ChevronRight } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { UserLayout } from "@/components/layout/UserLayout"
import { FlightStatusBadge } from "@/components/common/FlightStatusBadge"
import { FlightPriceTag } from "@/components/common/FlightPriceTag"
import * as flightApi from "@/services/flightApi"
import type { FlightVO, FlightSeatVO } from "@/types/flight"
import type { ApiError } from "@/lib/request"

export default function FlightDetailPage() {
  const params = useParams()
  const router = useRouter()
  const id = Number(params.id)
  const [flight, setFlight] = useState<FlightVO | null>(null)
  const [seats, setSeats] = useState<FlightSeatVO[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    setIsLoading(true)
    setError(null)

    Promise.all([flightApi.getFlightById(id), flightApi.getFlightSeats(id)])
      .then(([flightData, seatsData]) => {
        setFlight(flightData)
        setSeats(seatsData)
      })
      .catch((err: ApiError) => {
        setError(err.message || "加载航班详情失败")
      })
      .finally(() => setIsLoading(false))
  }, [id])

  if (isLoading) {
    return (
      <UserLayout>
        <div className="mx-auto max-w-4xl px-4 py-8 space-y-6">
          <Skeleton className="h-10 w-48" />
          <Skeleton className="h-64 w-full rounded-xl" />
          <Skeleton className="h-48 w-full rounded-xl" />
        </div>
      </UserLayout>
    )
  }

  if (error || !flight) {
    return (
      <UserLayout>
        <div className="mx-auto max-w-4xl px-4 py-8 text-center">
          <p className="text-destructive mb-4">{error || "航班不存在"}</p>
          <Button variant="outline" onClick={() => router.back()}>
            返回
          </Button>
        </div>
      </UserLayout>
    )
  }

  const formatTime = (iso: string) => {
    const d = new Date(iso)
    return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`
  }

  const formatDate = (iso: string) => {
    const d = new Date(iso)
    return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
  }

  const formatDuration = (minutes: number) => {
    const h = Math.floor(minutes / 60)
    const m = minutes % 60
    return `${h}时${m > 0 ? `${m}分` : ""}`
  }

  const availableSeats = seats.filter((s) => s.status === "AVAILABLE").length
  const economySeats = seats.filter((s) => s.cabinClass === "ECONOMY" && s.status === "AVAILABLE").length
  const businessSeats = seats.filter((s) => s.cabinClass === "BUSINESS" && s.status === "AVAILABLE").length
  const firstSeats = seats.filter((s) => s.cabinClass === "FIRST" && s.status === "AVAILABLE").length

  return (
    <UserLayout>
      <div className="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8 py-8">
        {/* 面包屑 */}
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-6">
          <Link href="/" className="hover:text-foreground">首页</Link>
          <ChevronRight className="h-4 w-4" />
          <Link href="/flights" className="hover:text-foreground">航班查询</Link>
          <ChevronRight className="h-4 w-4" />
          <span className="text-foreground">{flight.flightNo}</span>
        </div>

        {/* 航班信息卡片 */}
        <Card>
          <CardContent className="p-6">
            <div className="flex flex-wrap items-center gap-3 mb-6">
              <h1 className="text-xl font-bold">{flight.airlineName}</h1>
              <Badge variant="outline">{flight.flightNo}</Badge>
              <FlightStatusBadge status={flight.status} />
            </div>

            {/* 行程 */}
            <div className="flex items-center gap-6 sm:gap-12">
              <div className="text-center">
                <p className="text-3xl font-bold tabular-nums">{formatTime(flight.departureTime)}</p>
                <p className="text-base font-medium mt-1">{flight.departureCity}</p>
                <p className="text-xs text-muted-foreground">{flight.departureAirportName}</p>
                <p className="text-xs text-muted-foreground mt-2">{formatDate(flight.departureTime)}</p>
              </div>

              <div className="flex-1 flex flex-col items-center">
                <div className="flex items-center w-full max-w-[200px]">
                  <div className="h-2 w-2 rounded-full bg-primary shrink-0" />
                  <div className="h-px flex-1 bg-slate-300" />
                  <Plane className="h-4 w-4 text-primary shrink-0 -rotate-90" />
                  <div className="h-px flex-1 bg-slate-300" />
                  <div className="h-2 w-2 rounded-full bg-primary shrink-0" />
                </div>
                <p className="text-sm text-muted-foreground mt-1">
                  {formatDuration(flight.durationMinutes)}
                </p>
                <p className="text-xs text-muted-foreground">
                  {flight.directFlag ? "直飞" : "经停"}
                </p>
              </div>

              <div className="text-center">
                <p className="text-3xl font-bold tabular-nums">{formatTime(flight.arrivalTime)}</p>
                <p className="text-base font-medium mt-1">{flight.arrivalCity}</p>
                <p className="text-xs text-muted-foreground">{flight.arrivalAirportName}</p>
                <p className="text-xs text-muted-foreground mt-2">{formatDate(flight.arrivalTime)}</p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 详情 + 预订 */}
        <div className="mt-6 grid gap-6 md:grid-cols-[1fr_280px]">
          {/* 左：信息 */}
          <div className="space-y-6">
            {/* 航班详情 */}
            <Card>
              <CardContent className="p-5 space-y-3">
                <h2 className="font-semibold">航班详情</h2>
                <div className="grid grid-cols-2 gap-2 text-sm">
                  <div>
                    <span className="text-muted-foreground">航班号：</span>
                    {flight.flightNo}
                  </div>
                  <div>
                    <span className="text-muted-foreground">航司：</span>
                    {flight.airlineName}
                  </div>
                  <div>
                    <span className="text-muted-foreground">出发：</span>
                    {flight.departureCity} ({flight.departureAirportCode})
                  </div>
                  <div>
                    <span className="text-muted-foreground">到达：</span>
                    {flight.arrivalCity} ({flight.arrivalAirportCode})
                  </div>
                  <div>
                    <span className="text-muted-foreground">飞行时长：</span>
                    {formatDuration(flight.durationMinutes)}
                  </div>
                  <div>
                    <span className="text-muted-foreground">直飞：</span>
                    {flight.directFlag ? "是" : "否"}
                  </div>
                </div>

                <Separator />

                <div className="flex flex-wrap gap-4 text-sm">
                  <span className="inline-flex items-center gap-1">
                    <Luggage className="h-4 w-4 text-muted-foreground" />
                    {flight.baggageAllowance}
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <TrendingUp className="h-4 w-4 text-muted-foreground" />
                    准点率 {flight.punctualityRate}%
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <MapPin className="h-4 w-4 text-muted-foreground" />
                    总座位 {flight.totalSeats} · 可售 {availableSeats}
                  </span>
                </div>
              </CardContent>
            </Card>

            {/* 舱位 + 座位 */}
            <Card>
              <CardContent className="p-5">
                <h2 className="font-semibold mb-3">可选舱位</h2>
                <div className="space-y-3">
                  {[
                    { label: "经济舱", available: economySeats, price: flight.basePrice },
                    { label: "商务舱", available: businessSeats, price: Math.round(flight.basePrice * 2.5) },
                    { label: "头等舱", available: firstSeats, price: Math.round(flight.basePrice * 5) },
                  ].map((cabin) => (
                    <div
                      key={cabin.label}
                      className="flex items-center justify-between rounded-lg border border-slate-200 p-3"
                    >
                      <div>
                        <p className="font-medium text-sm">{cabin.label}</p>
                        <p className="text-xs text-muted-foreground">
                          {cabin.available > 0 ? `余 ${cabin.available} 座` : "已售罄"}
                        </p>
                      </div>
                      <div className="text-right">
                        <FlightPriceTag price={cabin.price} />
                      </div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          </div>

          {/* 右：预订卡片 */}
          <div>
            <Card className="sticky top-24">
              <CardContent className="p-5 text-center space-y-4">
                <div>
                  <FlightPriceTag price={flight.basePrice} className="text-2xl" />
                  <p className="text-xs text-muted-foreground mt-1">经济舱起</p>
                </div>
                <Button
                  className="w-full"
                  size="lg"
                  disabled={availableSeats === 0}
                  asChild
                >
                  <Link href={`/booking/${flight.id}`}>
                    {availableSeats > 0 ? "立即预订" : "已售罄"}
                  </Link>
                </Button>
                {availableSeats > 0 && availableSeats < 10 && (
                  <p className="text-xs text-destructive">
                    仅剩 {availableSeats} 座，请尽快预订
                  </p>
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </UserLayout>
  )
}

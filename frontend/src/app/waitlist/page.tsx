"use client"

import { Suspense, useCallback, useEffect, useMemo, useState } from "react"
import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import { Plane, MapPin, Clock, ChevronLeft, ChevronRight } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { UserLayout } from "@/components/layout/UserLayout"
import { FlightPriceTag } from "@/components/common/FlightPriceTag"
import { WaitlistStatusBadge } from "@/components/common/WaitlistStatusBadge"
import { useAuth } from "@/contexts/AuthContext"
import * as flightApi from "@/services/flightApi"
import * as waitlistApi from "@/services/waitlistApi"
import type { ApiError } from "@/lib/request"
import type { FlightVO } from "@/types/flight"
import type { WaitlistStatus, WaitlistVO } from "@/types/waitlist"
import { CABIN_CLASS_LABEL } from "@/types/flight"

const STATUS_TABS: Array<{ value: "" | WaitlistStatus; label: string }> = [
  { value: "", label: "全部" },
  { value: "PENDING_PAYMENT", label: "待支付" },
  { value: "WAITING", label: "排队中" },
  { value: "SUCCESS", label: "候补成功" },
  { value: "FAILED", label: "候补失败" },
  { value: "CANCELLED", label: "已取消" },
  { value: "REFUNDED", label: "已退款" },
  { value: "EXPIRED", label: "已过期" },
]

function WaitlistListContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth()

  const statusParam = (searchParams.get("status") || "") as "" | WaitlistStatus
  const pageParam = Math.max(1, Number(searchParams.get("page") || "1"))
  const pageSize = 10

  const [waitlists, setWaitlists] = useState<WaitlistVO[]>([])
  const [flightMap, setFlightMap] = useState<Record<number, FlightVO>>({})
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchWaitlists = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const data = await waitlistApi.getMyWaitlist()
      setWaitlists(data)

      const flightIds = Array.from(new Set(data.map((item) => item.flightId)))
      const flightResults = await Promise.allSettled(
        flightIds.map(async (flightId) => ({
          flightId,
          flight: await flightApi.getFlightById(flightId),
        }))
      )

      const nextFlightMap: Record<number, FlightVO> = {}
      flightResults.forEach((result) => {
        if (result.status === "fulfilled") {
          nextFlightMap[result.value.flightId] = result.value.flight
        }
      })
      setFlightMap(nextFlightMap)
    } catch (err) {
      setError((err as ApiError).message || "加载候补列表失败")
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      const redirect =
        searchParams.toString().length > 0 ? `/waitlist?${searchParams.toString()}` : "/waitlist"
      router.push(`/login?redirect=${encodeURIComponent(redirect)}`)
      return
    }

    if (isAuthenticated) {
      fetchWaitlists()
    }
  }, [isAuthenticated, isAuthLoading, fetchWaitlists, router, searchParams])

  const filteredWaitlists = useMemo(() => {
    if (!statusParam) return waitlists
    return waitlists.filter((item) => item.status === statusParam)
  }, [waitlists, statusParam])

  const totalPages = Math.max(1, Math.ceil(filteredWaitlists.length / pageSize))
  const safePage = Math.min(pageParam, totalPages)
  const paginatedWaitlists = filteredWaitlists.slice(
    (safePage - 1) * pageSize,
    safePage * pageSize
  )

  const switchStatus = (status: "" | WaitlistStatus) => {
    const params = new URLSearchParams()
    if (status) params.set("status", status)
    router.push(params.toString() ? `/waitlist?${params.toString()}` : "/waitlist")
  }

  const goToPage = (page: number) => {
    const params = new URLSearchParams(searchParams.toString())
    params.set("page", String(page))
    router.push(`/waitlist?${params.toString()}`)
  }

  const formatDate = (iso?: string) => {
    if (!iso) return "—"
    const date = new Date(iso)
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(
      date.getDate()
    ).padStart(2, "0")}`
  }

  const formatTime = (iso?: string) => {
    if (!iso) return "—"
    const date = new Date(iso)
    return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(
      2,
      "0"
    )}`
  }

  if (isAuthLoading) {
    return (
      <UserLayout>
        <div className="mx-auto max-w-4xl px-4 py-8 space-y-4">
          <Skeleton className="h-8 w-32" />
          <Skeleton className="h-48 w-full rounded-xl" />
        </div>
      </UserLayout>
    )
  }

  if (!isAuthenticated) return null

  return (
    <UserLayout>
      <div className="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8 py-8">
        <h1 className="text-2xl font-bold mb-6">我的候补</h1>

        <div className="flex gap-1 overflow-x-auto pb-2 mb-6">
          {STATUS_TABS.map((tab) => (
            <Button
              key={tab.value || "ALL"}
              variant={statusParam === tab.value ? "default" : "ghost"}
              size="sm"
              onClick={() => switchStatus(tab.value)}
              className="shrink-0"
            >
              {tab.label}
            </Button>
          ))}
        </div>

        {isLoading && (
          <div className="space-y-4">
            {Array.from({ length: 3 }).map((_, index) => (
              <Skeleton key={index} className="h-36 w-full rounded-xl" />
            ))}
          </div>
        )}

        {error && (
          <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-8 text-center">
            <p className="text-destructive mb-3">{error}</p>
            <Button variant="outline" onClick={fetchWaitlists}>
              重试
            </Button>
          </div>
        )}

        {!isLoading && !error && filteredWaitlists.length === 0 && (
          <Card>
            <CardContent className="p-12 text-center">
              <p className="text-muted-foreground mb-4">暂无候补记录</p>
              <Button render={<Link href="/flights">去查询航班</Link>} nativeButton={false} />
            </CardContent>
          </Card>
        )}

        {!isLoading && !error && filteredWaitlists.length > 0 && (
          <div className="space-y-4">
            {paginatedWaitlists.map((waitlist) => {
              const flight = flightMap[waitlist.flightId]

              return (
                <Card
                  key={waitlist.id}
                  className="hover:shadow-md transition-shadow cursor-pointer"
                  onClick={() => router.push(`/waitlist/${waitlist.id}`)}
                >
                  <CardContent className="p-5">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                      <div className="flex items-start gap-3 min-w-0">
                        <div className="flex items-center justify-center h-9 w-9 rounded-lg bg-primary/10 shrink-0">
                          <Plane className="h-4 w-4 text-primary" />
                        </div>

                        <div className="min-w-0 space-y-1">
                          <p className="font-medium text-sm truncate">
                            {flight?.airlineName || waitlist.flightNo}{" "}
                            <span className="text-muted-foreground font-normal">
                              {waitlist.flightNo}
                            </span>
                          </p>

                          <p className="text-xs text-muted-foreground flex items-center gap-1">
                            <MapPin className="h-3 w-3" />
                            {flight
                              ? `${flight.departureCity} → ${flight.arrivalCity}`
                              : "航班信息加载中"}
                          </p>

                          <p className="text-xs text-muted-foreground flex items-center gap-1">
                            <Clock className="h-3 w-3" />
                            {flight
                              ? `${formatTime(flight.departureTime)} → ${formatTime(
                                  flight.arrivalTime
                                )}`
                              : "—"}
                          </p>

                          <p className="text-xs text-muted-foreground">
                            目标舱位：{CABIN_CLASS_LABEL[waitlist.cabinClass]} · 乘机人数：
                            {waitlist.passengerCount}
                          </p>
                        </div>
                      </div>

                      <div className="flex items-center gap-4 shrink-0">
                        <div className="text-right">
                          <p className="text-xs text-muted-foreground">{waitlist.waitlistNo}</p>
                          <FlightPriceTag price={waitlist.payAmount} className="text-sm" />
                          <p className="text-xs text-muted-foreground">
                            {formatDate(waitlist.createdAt)}
                          </p>
                        </div>
                        <WaitlistStatusBadge status={waitlist.status} />
                      </div>
                    </div>
                  </CardContent>
                </Card>
              )
            })}

            {totalPages > 1 && (
              <div className="flex items-center justify-center gap-2 mt-8">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={safePage <= 1}
                  onClick={() => goToPage(safePage - 1)}
                >
                  <ChevronLeft className="h-4 w-4" /> 上一页
                </Button>

                {Array.from({ length: Math.min(totalPages, 5) }).map((_, index) => {
                  const startPage = Math.max(1, Math.min(safePage - 2, totalPages - 4))
                  const pageNumber = startPage + index
                  if (pageNumber > totalPages) return null

                  return (
                    <Button
                      key={pageNumber}
                      variant={pageNumber === safePage ? "default" : "outline"}
                      size="sm"
                      className="w-9"
                      onClick={() => goToPage(pageNumber)}
                    >
                      {pageNumber}
                    </Button>
                  )
                })}

                <Button
                  variant="outline"
                  size="sm"
                  disabled={safePage >= totalPages}
                  onClick={() => goToPage(safePage + 1)}
                >
                  下一页 <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            )}
          </div>
        )}
      </div>
    </UserLayout>
  )
}

export default function WaitlistPage() {
  return (
    <Suspense
      fallback={
        <UserLayout>
          <div className="mx-auto max-w-4xl px-4 py-8 space-y-4">
            <Skeleton className="h-8 w-32" />
            <Skeleton className="h-48 w-full rounded-xl" />
          </div>
        </UserLayout>
      }
    >
      <WaitlistListContent />
    </Suspense>
  )
}

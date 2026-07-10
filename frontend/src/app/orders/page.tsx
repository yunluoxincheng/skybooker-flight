"use client"

import { Suspense, useEffect, useState, useCallback } from "react"
import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import { Plane, MapPin, Clock, ChevronLeft, ChevronRight } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { UserLayout } from "@/components/layout/UserLayout"
import { OrderStatusBadge } from "@/components/common/OrderStatusBadge"
import { FlightPriceTag } from "@/components/common/FlightPriceTag"
import { useAuth } from "@/contexts/AuthContext"
import * as orderApi from "@/services/orderApi"
import type { OrderVO } from "@/types/order"
import type { ApiError } from "@/lib/request"

const STATUS_TABS = [
  { value: "", label: "全部" },
  { value: "PENDING_PAYMENT", label: "待支付" },
  { value: "ISSUED", label: "已出票" },
  { value: "CHANGED", label: "已改签" },
  { value: "REFUNDED", label: "已退票" },
  { value: "CANCELLED", label: "已取消" },
]

function OrdersContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth()

  const statusParam = searchParams.get("status") || ""
  const pageParam = Number(searchParams.get("page") || "1")
  const size = 10

  const [orders, setOrders] = useState<OrderVO[]>([])
  const [total, setTotal] = useState(0)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchOrders = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const data = await orderApi.getMyOrders({
        status: statusParam || undefined,
        page: pageParam,
        size,
      })
      setOrders(data.records)
      setTotal(data.total)
    } catch (err) {
      setError((err as ApiError).message || "加载订单失败")
    } finally {
      setIsLoading(false)
    }
  }, [statusParam, pageParam])

  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      router.push("/login?redirect=/orders")
      return
    }
    if (isAuthenticated) {
      fetchOrders()
    }
  }, [fetchOrders, isAuthLoading, isAuthenticated, router])

  const switchStatus = (status: string) => {
    const params = new URLSearchParams()
    if (status) params.set("status", status)
    router.push(`/orders?${params.toString()}`)
  }

  const goToPage = (page: number) => {
    const params = new URLSearchParams(searchParams.toString())
    params.set("page", String(page))
    router.push(`/orders?${params.toString()}`)
  }

  const totalPages = Math.max(1, Math.ceil(total / size))

  const formatTime = (iso: string) => {
    if (!iso) return ""
    const d = new Date(iso)
    return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`
  }

  const formatDate = (iso: string) => {
    if (!iso) return ""
    const d = new Date(iso)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
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
        <h1 className="text-2xl font-bold mb-6">我的订单</h1>

        {/* 状态 Tabs */}
        <div className="flex gap-1 overflow-x-auto pb-2 mb-6">
          {STATUS_TABS.map((tab) => (
            <Button
              key={tab.value}
              variant={statusParam === tab.value ? "default" : "ghost"}
              size="sm"
              onClick={() => switchStatus(tab.value)}
              className="shrink-0"
            >
              {tab.label}
            </Button>
          ))}
        </div>

        {/* Loading */}
        {isLoading && (
          <div className="space-y-4">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-32 w-full rounded-xl" />
            ))}
          </div>
        )}

        {/* Error */}
        {error && (
          <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-8 text-center">
            <p className="text-destructive mb-3">{error}</p>
            <Button variant="outline" onClick={fetchOrders}>重试</Button>
          </div>
        )}

        {/* 订单列表 */}
        {!isLoading && !error && orders.length === 0 && (
          <Card>
            <CardContent className="p-12 text-center">
              <p className="text-muted-foreground mb-4">暂无订单</p>
              <Button render={<Link href="/flights">去预订航班</Link>} nativeButton={false} />
            </CardContent>
          </Card>
        )}

        {!isLoading && !error && (
          <div className="space-y-4">
            {orders.map((order) => (
              <Card
                key={order.id}
                className="hover:shadow-md transition-shadow cursor-pointer"
                onClick={() => router.push(`/orders/${order.id}`)}
              >
                <CardContent className="p-5">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="flex items-center justify-center h-9 w-9 rounded-lg bg-primary/10 shrink-0">
                        <Plane className="h-4 w-4 text-primary" />
                      </div>
                      <div className="min-w-0">
                        <p className="font-medium text-sm truncate">
                          {order.airlineName || order.flightNo}{" "}
                          <span className="text-muted-foreground font-normal">{order.flightNo}</span>
                          <span className="ml-2 rounded bg-sky-50 px-1.5 py-0.5 text-xs font-normal text-sky-700">{order.journeyType === "CONNECTING" ? "中转联程" : "直飞"}</span>
                        </p>
                        {order.journeyType === "CONNECTING" && order.segments && <p className="text-xs text-muted-foreground">{order.segments.map(s => s.flightNo).join(" + ")}</p>}
                        <p className="text-xs text-muted-foreground flex items-center gap-1">
                          <MapPin className="h-3 w-3" />
                          {(order.departureCity && order.arrivalCity)
                            ? `${order.departureCity} → ${order.arrivalCity}`
                            : "—"}
                        </p>
                        {(order.departureTime || order.arrivalTime) && (
                          <p className="text-xs text-muted-foreground flex items-center gap-1">
                            <Clock className="h-3 w-3" />
                            {order.departureTime ? formatTime(order.departureTime) : "—"} →{" "}
                            {order.arrivalTime ? formatTime(order.arrivalTime) : "—"}
                          </p>
                        )}
                      </div>
                    </div>

                    <div className="flex items-center gap-4 shrink-0">
                      <div className="text-right">
                        <p className="text-xs text-muted-foreground">{order.orderNo}</p>
                        <FlightPriceTag price={order.totalAmount} className="text-sm" />
                        <p className="text-xs text-muted-foreground">{formatDate(order.createdAt)}</p>
                      </div>
                      <OrderStatusBadge status={order.status} />
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}

            {/* 分页 */}
            {totalPages > 1 && (
              <div className="flex items-center justify-center gap-2 mt-8">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={pageParam <= 1}
                  onClick={() => goToPage(pageParam - 1)}
                >
                  <ChevronLeft className="h-4 w-4" /> 上一页
                </Button>
                {Array.from({ length: Math.min(totalPages, 5) }).map((_, i) => {
                  const startPage = Math.max(1, Math.min(pageParam - 2, totalPages - 4))
                  const pageNum = startPage + i
                  if (pageNum > totalPages) return null
                  return (
                    <Button
                      key={pageNum}
                      variant={pageNum === pageParam ? "default" : "outline"}
                      size="sm"
                      className="w-9"
                      onClick={() => goToPage(pageNum)}
                    >
                      {pageNum}
                    </Button>
                  )
                })}
                <Button
                  variant="outline"
                  size="sm"
                  disabled={pageParam >= totalPages}
                  onClick={() => goToPage(pageParam + 1)}
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

export default function OrdersPage() {
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
      <OrdersContent />
    </Suspense>
  )
}

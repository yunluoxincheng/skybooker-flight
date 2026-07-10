"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { useParams, useRouter } from "next/navigation"
import {
  AlertTriangle,
  ChevronRight,
  Clock,
  Loader2,
  MapPin,
  Plane,
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { UserLayout } from "@/components/layout/UserLayout"
import { FlightPriceTag } from "@/components/common/FlightPriceTag"
import { useAuth } from "@/contexts/AuthContext"
import { formatDate, formatDateFull, formatTime, getCrossDayLabel, getHoursUntil } from "@/lib/date-utils"
import * as flightApi from "@/services/flightApi"
import * as orderApi from "@/services/orderApi"
import { ApiError } from "@/lib/request"
import type { ChangeOptionVO, OrderVO, SeatMapping } from "@/types/order"

function formatDateTimeLabel(iso?: string) {
  if (!iso) return "—"
  return `${formatDateFull(iso)} ${formatTime(iso)}`
}

function formatDuration(minutes: number) {
  const hours = Math.floor(minutes / 60)
  const remain = minutes % 60
  return `${hours}时${remain > 0 ? `${remain}分` : ""}`
}

function canAccessChangePage(order: OrderVO) {
  if (order.status !== "ISSUED") return false

  const hoursUntilDeparture = order.departureTime ? getHoursUntil(order.departureTime) : Number.NaN
  return !Number.isNaN(hoursUntilDeparture) && hoursUntilDeparture >= 2
}

export default function OrderChangePage() {
  const params = useParams()
  const router = useRouter()
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const id = Number(params.id)

  const [order, setOrder] = useState<OrderVO | null>(null)
  const [options, setOptions] = useState<ChangeOptionVO[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [selectedFlight, setSelectedFlight] = useState<ChangeOptionVO | null>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const fetchData = useCallback(async () => {
    setIsLoading(true)
    setError(null)

    try {
      const orderData = await orderApi.getOrderById(id)

      if (!canAccessChangePage(orderData)) {
        router.replace(`/orders/${id}`)
        return
      }

      const optionData = await orderApi.getChangeOptions(id)
      setOrder(orderData)
      setOptions(optionData)
    } catch (err) {
      setError((err as ApiError).message || "加载改签页面失败")
    } finally {
      setIsLoading(false)
    }
  }, [id, router])

  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      router.push(`/login?redirect=${encodeURIComponent(`/orders/${id}/change`)}`)
      return
    }

    if (isAuthenticated && id) {
      fetchData()
    }
  }, [fetchData, id, isAuthLoading, isAuthenticated, router])

  const hoursUntilDeparture = order?.departureTime ? getHoursUntil(order.departureTime) : Number.NaN
  const changeFeeRate = !Number.isNaN(hoursUntilDeparture) && hoursUntilDeparture > 24 ? 0.1 : 0.3
  const estimatedChangeFee = order ? order.totalAmount * changeFeeRate : 0

  const currentFlightDuration = useMemo(() => {
    if (!order?.departureTime || !order?.arrivalTime) return null
    const departure = new Date(order.departureTime)
    const arrival = new Date(order.arrivalTime)
    const durationMinutes = Math.round((arrival.getTime() - departure.getTime()) / (1000 * 60))
    return durationMinutes > 0 ? durationMinutes : null
  }, [order?.arrivalTime, order?.departureTime])

  const openConfirm = (option: ChangeOptionVO) => {
    setSelectedFlight(option)
    setActionError(null)
    setConfirmOpen(true)
  }

  const handleConfirmChange = async () => {
    if (!order || !selectedFlight) return

    setIsSubmitting(true)
    setActionError(null)

    try {
      const seats = await flightApi.getFlightSeats(selectedFlight.flightId)
      const availableSeats = seats.filter((seat) => seat.status === "AVAILABLE")

      if (availableSeats.length < order.passengers.length) {
        throw new ApiError(0, "当前航班可用座位不足，请重新选择其他航班")
      }

      const seatMappings: SeatMapping[] = order.passengers.map((passenger, index) => ({
        passengerId: passenger.passengerId,
        newSeatId: availableSeats[index].id,
      }))

      await orderApi.changeOrder(id, {
        newFlightId: selectedFlight.flightId,
        seatMappings,
      })

      setConfirmOpen(false)
      router.push(`/orders/${id}`)
    } catch (err) {
      setActionError((err as ApiError).message || "改签失败")
      setConfirmOpen(false)
    } finally {
      setIsSubmitting(false)
    }
  }

  if (isAuthLoading || isLoading) {
    return (
      <UserLayout>
        <div className="mx-auto max-w-4xl px-4 py-8 space-y-6">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-40 w-full rounded-xl" />
          <Skeleton className="h-48 w-full rounded-xl" />
        </div>
      </UserLayout>
    )
  }

  if (!isAuthenticated) return null

  if (error || !order) {
    return (
      <UserLayout>
        <div className="mx-auto max-w-4xl px-4 py-8 text-center">
          <p className="text-destructive mb-4">{error || "改签信息不存在"}</p>
          <Button variant="outline" onClick={() => router.push(`/orders/${id}`)}>
            返回订单详情
          </Button>
        </div>
      </UserLayout>
    )
  }

  return (
    <UserLayout>
      <div className="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-6">
          <button onClick={() => router.push("/orders")} className="hover:text-foreground">
            我的订单
          </button>
          <ChevronRight className="h-4 w-4" />
          <button onClick={() => router.push(`/orders/${id}`)} className="hover:text-foreground">
            {order.orderNo}
          </button>
          <ChevronRight className="h-4 w-4" />
          <span className="text-foreground">改签</span>
        </div>

        {actionError && (
          <div className="rounded-lg bg-destructive/10 px-4 py-3 text-sm text-destructive mb-4 flex items-center gap-2">
            <AlertTriangle className="h-4 w-4" />
            {actionError}
          </div>
        )}

        <Card className="mb-6">
          <CardContent className="p-5">
            <h2 className="font-semibold mb-3">原航班信息</h2>
            <div className="flex items-center gap-3 mb-3">
              <div className="flex items-center justify-center h-9 w-9 rounded-lg bg-primary/10">
                <Plane className="h-4 w-4 text-primary" />
              </div>
              <div>
                <p className="font-medium text-sm">
                  {order.airlineName || "—"}{" "}
                  <Badge variant="outline" className="ml-1 text-xs">
                    {order.flightNo}
                  </Badge>
                </p>
                <p className="text-xs text-muted-foreground flex items-center gap-1">
                  <MapPin className="h-3 w-3" />
                  {order.departureCity || "—"} → {order.arrivalCity || "—"}
                </p>
              </div>
            </div>

            <div className="grid gap-3 text-sm sm:grid-cols-2">
              <div>
                <span className="text-muted-foreground">出发时间：</span>
                {formatDateTimeLabel(order.departureTime)}
              </div>
              <div>
                <span className="text-muted-foreground">到达时间：</span>
                {formatDateTimeLabel(order.arrivalTime)}
              </div>
              <div>
                <span className="text-muted-foreground">乘机人数：</span>
                {order.passengers.length}
              </div>
              <div>
                <span className="text-muted-foreground">当前订单金额：</span>
                ¥{order.totalAmount.toLocaleString()}
              </div>
            </div>
          </CardContent>
        </Card>

        <div className="space-y-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="font-semibold">可改签航班</h2>
              <p className="text-sm text-muted-foreground mt-1">
                改签费预估按当前订单总额计算：
                {changeFeeRate === 0.1 ? "距起飞超过24小时，约10%" : "距起飞24小时内，约30%"}
              </p>
            </div>
            <Button variant="outline" onClick={fetchData}>
              刷新列表
            </Button>
          </div>

          {options.length === 0 ? (
            <Card>
              <CardContent className="p-12 text-center">
                <p className="text-muted-foreground mb-4">当前无可改签航班</p>
                <Button variant="outline" onClick={() => router.push(`/orders/${id}`)}>
                  返回订单详情
                </Button>
              </CardContent>
            </Card>
          ) : (
            options.map((option) => {
              const crossDayLabel = getCrossDayLabel(option.departureTime, option.arrivalTime)

              return (
                <Card key={option.flightId}>
                  <CardContent className="p-5">
                    <div className="flex flex-col lg:flex-row lg:items-center gap-4">
                      <div className="flex items-center gap-3 min-w-0 lg:w-[220px] shrink-0">
                        <div className="flex items-center justify-center h-10 w-10 rounded-lg bg-primary/10 shrink-0">
                          <Plane className="h-5 w-5 text-primary" />
                        </div>
                        <div className="min-w-0">
                          <p className="text-sm font-medium truncate">
                            {order.airlineName || "同航线航班"}
                          </p>
                          <p className="text-xs text-muted-foreground">{option.flightNo}</p>
                        </div>
                      </div>

                      <div className="flex items-center gap-4 flex-1 min-w-0">
                        <div className="text-center shrink-0">
                          <p className="text-xl font-bold tabular-nums">
                            {formatTime(option.departureTime)}
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {formatDate(option.departureTime)}
                          </p>
                          <p className="text-sm text-muted-foreground">{order.departureCity || "出发地"}</p>
                        </div>

                        <div className="flex-1 flex flex-col items-center px-2 min-w-[80px]">
                          <div className="flex items-center w-full">
                            <div className="h-2 w-2 rounded-full bg-primary shrink-0" />
                            <div className="h-px flex-1 bg-slate-300" />
                            <Plane className="h-4 w-4 text-primary shrink-0 -rotate-90" />
                            <div className="h-px flex-1 bg-slate-300" />
                            <div className="h-2 w-2 rounded-full bg-primary shrink-0" />
                          </div>
                          {currentFlightDuration && (
                            <div className="flex items-center gap-1.5 mt-1">
                              <Clock className="h-3 w-3 text-muted-foreground" />
                              <p className="text-xs text-muted-foreground">
                                {formatDuration(currentFlightDuration)}
                              </p>
                            </div>
                          )}
                        </div>

                        <div className="text-center shrink-0">
                          <p className="text-xl font-bold tabular-nums">
                            {formatTime(option.arrivalTime)}
                          </p>
                          {crossDayLabel && (
                            <p className="text-xs text-muted-foreground">{crossDayLabel}</p>
                          )}
                          <p className="text-sm text-muted-foreground">{order.arrivalCity || "到达地"}</p>
                        </div>
                      </div>

                      <div className="flex items-center gap-4 lg:w-[220px] shrink-0 justify-end">
                        <div className="text-right">
                          <FlightPriceTag price={option.basePrice} className="text-lg" />
                          <p className="text-xs text-muted-foreground">
                            剩余 {option.remainingSeats} 座
                          </p>
                          <p className="text-xs text-muted-foreground">状态：{option.status}</p>
                        </div>
                        <Button size="sm" onClick={() => openConfirm(option)}>
                          选择此航班
                        </Button>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              )
            })
          )}
        </div>

        <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>确认改签</DialogTitle>
              <DialogDescription>
                系统将在提交时自动为所有乘机人分配可用座位，实际费用以系统计算结果为准。
              </DialogDescription>
            </DialogHeader>

            {selectedFlight && (
              <div className="space-y-4">
                <div className="rounded-lg bg-slate-50 p-4">
                  <p className="font-medium text-sm mb-2">{selectedFlight.flightNo}</p>
                  <p className="text-sm text-muted-foreground">
                    {order.departureCity || "出发地"} {formatDate(selectedFlight.departureTime)}{" "}
                    {formatTime(selectedFlight.departureTime)} → {order.arrivalCity || "到达地"}{" "}
                    {formatTime(selectedFlight.arrivalTime)}
                    {getCrossDayLabel(selectedFlight.departureTime, selectedFlight.arrivalTime) &&
                      ` ${getCrossDayLabel(selectedFlight.departureTime, selectedFlight.arrivalTime)}`}
                  </p>
                </div>

                <div className="space-y-2 text-sm">
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">当前订单金额</span>
                    <span>¥{order.totalAmount.toLocaleString()}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">候选航班基础票价</span>
                    <span>¥{selectedFlight.basePrice.toLocaleString()}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">预估改签费</span>
                    <span>¥{estimatedChangeFee.toLocaleString()}</span>
                  </div>
                  <Separator />
                  <p className="text-xs text-muted-foreground">
                    说明：预估改签费按当前订单总额的
                    {changeFeeRate === 0.1 ? "10%" : "30%"} 计算，仅供参考。
                  </p>
                </div>

                <div className="flex justify-end gap-2">
                  <Button variant="ghost" onClick={() => setConfirmOpen(false)} disabled={isSubmitting}>
                    取消
                  </Button>
                  <Button onClick={handleConfirmChange} disabled={isSubmitting}>
                    {isSubmitting && <Loader2 className="h-4 w-4 animate-spin mr-1" />}
                    确认改签
                  </Button>
                </div>
              </div>
            )}
          </DialogContent>
        </Dialog>
      </div>
    </UserLayout>
  )
}

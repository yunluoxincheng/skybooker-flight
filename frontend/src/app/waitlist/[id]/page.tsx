"use client"

import { useCallback, useEffect, useState } from "react"
import Link from "next/link"
import { useParams, useRouter } from "next/navigation"
import {
  AlertTriangle,
  Clock,
  Loader2,
  MapPin,
  Plane,
  ChevronRight,
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
  DialogTrigger,
} from "@/components/ui/dialog"
import { UserLayout } from "@/components/layout/UserLayout"
import { FlightPriceTag } from "@/components/common/FlightPriceTag"
import { WaitlistStatusBadge } from "@/components/common/WaitlistStatusBadge"
import { useAuth } from "@/contexts/AuthContext"
import { PASSENGER_TYPE_LABEL } from "@/lib/passenger-utils"
import * as flightApi from "@/services/flightApi"
import * as orderApi from "@/services/orderApi"
import * as waitlistApi from "@/services/waitlistApi"
import type { ApiError } from "@/lib/request"
import type { FlightVO } from "@/types/flight"
import type { OrderVO } from "@/types/order"
import type { WaitlistVO } from "@/types/waitlist"
import { CABIN_CLASS_LABEL } from "@/types/flight"

const AIRPORT_FEE_PER_PASSENGER = 50
const FUEL_FEE_PER_PASSENGER = 30

export default function WaitlistDetailPage() {
  const params = useParams()
  const router = useRouter()
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const id = Number(params.id)

  const [waitlist, setWaitlist] = useState<WaitlistVO | null>(null)
  const [flight, setFlight] = useState<FlightVO | null>(null)
  const [ticketOrder, setTicketOrder] = useState<OrderVO | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionLoading, setActionLoading] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [cancelOpen, setCancelOpen] = useState(false)

  const fetchWaitlistDetail = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const waitlistData = await waitlistApi.getWaitlistById(id)
      setWaitlist(waitlistData)

      const [flightResult, ticketOrderResult] = await Promise.allSettled([
        flightApi.getFlightById(waitlistData.flightId),
        waitlistData.ticketOrderId
          ? orderApi.getOrderById(waitlistData.ticketOrderId)
          : Promise.resolve(null),
      ])

      setFlight(flightResult.status === "fulfilled" ? flightResult.value : null)
      setTicketOrder(ticketOrderResult.status === "fulfilled" ? ticketOrderResult.value : null)
    } catch (err) {
      setError((err as ApiError).message || "加载候补详情失败")
    } finally {
      setIsLoading(false)
    }
  }, [id])

  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      router.push(`/login?redirect=${encodeURIComponent(`/waitlist/${id}`)}`)
      return
    }

    if (isAuthenticated && id) {
      fetchWaitlistDetail()
    }
  }, [fetchWaitlistDetail, id, isAuthLoading, isAuthenticated, router])

  const doAction = async (action: () => Promise<unknown>, afterSuccess?: () => void) => {
    setActionLoading(true)
    setActionError(null)
    try {
      await action()
      await fetchWaitlistDetail()
      afterSuccess?.()
    } catch (err) {
      setActionError((err as ApiError).message || "操作失败")
    } finally {
      setActionLoading(false)
    }
  }

  const handlePay = () => doAction(() => waitlistApi.payWaitlist(id))
  const handleCancel = () =>
    doAction(() => waitlistApi.cancelWaitlist(id), () => setCancelOpen(false))

  const formatTime = (iso?: string) => {
    if (!iso) return "—"
    const date = new Date(iso)
    return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(
      2,
      "0"
    )}`
  }

  const formatFull = (iso?: string) => {
    if (!iso) return "—"
    const date = new Date(iso)
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(
      date.getDate()
    ).padStart(2, "0")} ${formatTime(iso)}`
  }

  if (isAuthLoading || isLoading) {
    return (
      <UserLayout>
        <div className="mx-auto max-w-4xl px-4 py-8 space-y-6">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-64 w-full rounded-xl" />
          <Skeleton className="h-32 w-full rounded-xl" />
        </div>
      </UserLayout>
    )
  }

  if (!isAuthenticated) return null

  if (error || !waitlist) {
    return (
      <UserLayout>
        <div className="mx-auto max-w-4xl px-4 py-8 text-center">
          <p className="text-destructive mb-4">{error || "候补记录不存在"}</p>
          <Button variant="outline" onClick={() => router.back()}>
            返回
          </Button>
        </div>
      </UserLayout>
    )
  }

  const canPay = waitlist.status === "PENDING_PAYMENT"
  const canCancel = waitlist.status === "PENDING_PAYMENT" || waitlist.status === "WAITING"
  const airportFee = waitlist.passengerCount * AIRPORT_FEE_PER_PASSENGER
  const fuelFee = waitlist.passengerCount * FUEL_FEE_PER_PASSENGER
  const ticketAmount = Math.max(waitlist.payAmount - airportFee - fuelFee, 0)

  return (
    <UserLayout>
      <div className="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-6">
          <button onClick={() => router.push("/waitlist")} className="hover:text-foreground">
            我的候补
          </button>
          <ChevronRight className="h-4 w-4" />
          <span className="text-foreground">{waitlist.waitlistNo}</span>
        </div>

        {actionError && (
          <div className="rounded-lg bg-destructive/10 px-4 py-3 text-sm text-destructive mb-4 flex items-center gap-2">
            <AlertTriangle className="h-4 w-4" />
            {actionError}
          </div>
        )}

        <Card className="mb-6">
          <CardContent className="p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <div className="flex items-center gap-3 mb-1">
                <h1 className="text-lg font-bold">{waitlist.waitlistNo}</h1>
                <WaitlistStatusBadge status={waitlist.status} />
              </div>
              <p className="text-sm text-muted-foreground">
                创建时间：{formatFull(waitlist.createdAt)}
                {waitlist.paidAt && ` · 支付时间：${formatFull(waitlist.paidAt)}`}
              </p>
            </div>

            <div className="flex items-center gap-2">
              {canPay && (
                <Button size="sm" onClick={handlePay} disabled={actionLoading}>
                  {actionLoading && <Loader2 className="h-3.5 w-3.5 animate-spin mr-1" />}
                  立即支付
                </Button>
              )}

              {canCancel && (
                <Dialog open={cancelOpen} onOpenChange={setCancelOpen}>
                  <DialogTrigger
                    render={
                      <Button size="sm" variant="outline" disabled={actionLoading}>
                        取消候补
                      </Button>
                    }
                  />
                  <DialogContent>
                    <DialogHeader>
                      <DialogTitle>确认取消候补</DialogTitle>
                      <DialogDescription>
                        {waitlist.status === "WAITING"
                          ? "取消后将发起退款，且无法恢复。"
                          : "取消后当前候补记录无法恢复。"}
                      </DialogDescription>
                    </DialogHeader>
                    <div className="flex justify-end gap-2 mt-4">
                      <Button variant="ghost" onClick={() => setCancelOpen(false)}>
                        返回
                      </Button>
                      <Button variant="destructive" onClick={handleCancel} disabled={actionLoading}>
                        确认取消
                      </Button>
                    </div>
                  </DialogContent>
                </Dialog>
              )}
            </div>
          </CardContent>
        </Card>

        <div className="grid gap-6 md:grid-cols-[1fr_260px]">
          <div className="space-y-6">
            <Card>
              <CardContent className="p-5">
                <h2 className="font-semibold mb-3">航班信息</h2>

                <div className="flex items-center gap-3 mb-3">
                  <div className="flex items-center justify-center h-9 w-9 rounded-lg bg-primary/10">
                    <Plane className="h-4 w-4 text-primary" />
                  </div>
                  <div>
                    <p className="font-medium text-sm">
                      {flight?.airlineName || "航班"}{" "}
                      <Badge variant="outline" className="ml-1 text-xs">
                        {waitlist.flightNo}
                      </Badge>
                    </p>
                    <p className="text-xs text-muted-foreground flex items-center gap-1">
                      <MapPin className="h-3 w-3" />
                      {flight ? `${flight.departureCity} → ${flight.arrivalCity}` : "航班信息加载中"}
                    </p>
                  </div>
                </div>

                <p className="text-sm text-muted-foreground flex items-center gap-1">
                  <Clock className="h-3.5 w-3.5" />
                  {flight
                    ? `${formatTime(flight.departureTime)} → ${formatTime(flight.arrivalTime)}`
                    : "—"}
                </p>
              </CardContent>
            </Card>

            <Card>
              <CardContent className="p-5">
                <h2 className="font-semibold mb-3">候补信息</h2>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
                  <div>
                    <span className="text-muted-foreground">目标舱位：</span>
                    {CABIN_CLASS_LABEL[waitlist.cabinClass]}
                  </div>
                  <div>
                    <span className="text-muted-foreground">乘机人数：</span>
                    {waitlist.passengerCount}
                  </div>
                  <div>
                    <span className="text-muted-foreground">支付截止：</span>
                    {formatFull(waitlist.expireTime)}
                  </div>
                  {waitlist.refundTime && (
                    <div>
                      <span className="text-muted-foreground">退款时间：</span>
                      {formatFull(waitlist.refundTime)}
                    </div>
                  )}
                </div>

                {waitlist.lastSkipReason && (
                  <div className="rounded-lg bg-muted px-4 py-3 text-sm text-muted-foreground mt-4">
                    系统备注：{waitlist.lastSkipReason}
                  </div>
                )}

                {waitlist.status === "SUCCESS" && waitlist.ticketOrderId && (
                  <div className="rounded-lg bg-primary/5 px-4 py-3 text-sm mt-4">
                    关联正式订单：
                    <Link
                      href={`/orders/${waitlist.ticketOrderId}`}
                      className="text-primary hover:underline ml-1"
                    >
                      {ticketOrder?.orderNo || `#${waitlist.ticketOrderId}`}
                    </Link>
                  </div>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardContent className="p-5">
                <h2 className="font-semibold mb-3">乘机人</h2>
                <div className="space-y-2">
                  {waitlist.passengers.map((passenger) => (
                    <div
                      key={passenger.passengerId}
                      className="flex items-center justify-between text-sm py-2 border-b border-slate-100 last:border-0"
                    >
                      <div>
                        <span className="font-medium">{passenger.passengerName}</span>
                        <span className="text-muted-foreground ml-2">
                          {PASSENGER_TYPE_LABEL[passenger.passengerType]}
                        </span>
                      </div>
                      <div className="text-muted-foreground">
                        {passenger.seatNo ? `锁定座位 ${passenger.seatNo}` : "待分配"}
                      </div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          </div>

          <Card className="h-fit sticky top-24">
            <CardContent className="p-5">
              <h2 className="font-semibold mb-3">费用明细</h2>
              <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">票价</span>
                  <span>¥{ticketAmount.toLocaleString()}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">机场建设费</span>
                  <span>¥{airportFee.toLocaleString()}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">燃油费</span>
                  <span>¥{fuelFee.toLocaleString()}</span>
                </div>
                {typeof waitlist.refundAmount === "number" && waitlist.refundAmount > 0 && (
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">退款金额</span>
                    <span>¥{waitlist.refundAmount.toLocaleString()}</span>
                  </div>
                )}
                <Separator />
                <div className="flex justify-between font-bold text-base">
                  <span>合计</span>
                  <FlightPriceTag price={waitlist.payAmount} className="text-base" />
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </UserLayout>
  )
}

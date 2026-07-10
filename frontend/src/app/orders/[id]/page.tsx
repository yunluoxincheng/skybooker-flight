"use client"

import { useCallback, useEffect, useState } from "react"
import { useParams, useRouter } from "next/navigation"
import { Plane, MapPin, Clock, ChevronRight, Loader2, AlertTriangle } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  DialogDescription,
} from "@/components/ui/dialog"
import { Textarea } from "@/components/ui/textarea"
import { UserLayout } from "@/components/layout/UserLayout"
import { OrderStatusBadge } from "@/components/common/OrderStatusBadge"
import { FlightPriceTag } from "@/components/common/FlightPriceTag"
import { useAuth } from "@/contexts/AuthContext"
import { formatDateFull, formatTime, getHoursUntil } from "@/lib/date-utils"
import { PASSENGER_TYPE_LABEL } from "@/lib/passenger-utils"
import * as orderApi from "@/services/orderApi"
import type { OrderVO } from "@/types/order"
import type { ApiError } from "@/lib/request"

export default function OrderDetailPage() {
  const params = useParams()
  const router = useRouter()
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const id = Number(params.id)

  const [order, setOrder] = useState<OrderVO | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionLoading, setActionLoading] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  // 退票
  const [refundOpen, setRefundOpen] = useState(false)
  const [refundReason, setRefundReason] = useState("")

  // 取消
  const [cancelOpen, setCancelOpen] = useState(false)

  const fetchOrder = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const data = await orderApi.getOrderById(id)
      setOrder(data)
    } catch (err) {
      setError((err as ApiError).message || "加载订单失败")
    } finally {
      setIsLoading(false)
    }
  }, [id])

  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      router.push(`/login?redirect=/orders/${id}`)
      return
    }
    if (isAuthenticated) {
      fetchOrder()
    }
  }, [fetchOrder, id, isAuthLoading, isAuthenticated, router])

  const doAction = async (action: () => Promise<unknown>) => {
    setActionLoading(true)
    setActionError(null)
    try {
      await action()
      await fetchOrder()
    } catch (err) {
      setActionError((err as ApiError).message || "操作失败")
    } finally {
      setActionLoading(false)
    }
  }

  const handlePay = () => doAction(() => orderApi.payOrder(id))
  const handleCancel = () =>
    doAction(() => orderApi.cancelOrder(id)).then(() => setCancelOpen(false))
  const handleRefund = () =>
    doAction(() => orderApi.refundOrder(id, refundReason || undefined)).then(() => setRefundOpen(false))

  const formatFull = (iso: string) => {
    if (!iso) return "—"
    return `${formatDateFull(iso)} ${formatTime(iso)}`
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
  if (error || !order) {
    return (
      <UserLayout>
        <div className="mx-auto max-w-4xl px-4 py-8 text-center">
          <p className="text-destructive mb-4">{error || "订单不存在"}</p>
          <Button variant="outline" onClick={() => router.back()}>返回</Button>
        </div>
      </UserLayout>
    )
  }

  const canPay = order.status === "PENDING_PAYMENT"
  const canCancel = order.status === "PENDING_PAYMENT"
  const canRefund = order.status === "ISSUED" || order.status === "CHANGED"
  const canChange = order.status === "ISSUED"
  const hoursUntilDeparture = order.departureTime ? getHoursUntil(order.departureTime) : Number.NaN
  const canChangeByTime = !Number.isNaN(hoursUntilDeparture) && hoursUntilDeparture >= 2
  const canChangeEnabled = canChange && canChangeByTime
  const changeDisabledReason = (() => {
    if (!canChange) return ""
    if (!order.departureTime || Number.isNaN(hoursUntilDeparture)) return "航班信息缺失，无法改签"
    if (hoursUntilDeparture < 2) return "距起飞不足2小时，不可改签"
    return ""
  })()

  return (
    <UserLayout>
      <div className="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8 py-8">
        {/* 面包屑 */}
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-6">
          <button onClick={() => router.push("/orders")} className="hover:text-foreground">
            我的订单
          </button>
          <ChevronRight className="h-4 w-4" />
          <span className="text-foreground">{order.orderNo}</span>
        </div>

        {/* 操作错误 */}
        {actionError && (
          <div className="rounded-lg bg-destructive/10 px-4 py-3 text-sm text-destructive mb-4 flex items-center gap-2">
            <AlertTriangle className="h-4 w-4" /> {actionError}
          </div>
        )}

        {/* 订单头部 */}
        <Card className="mb-6">
          <CardContent className="p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <div className="flex items-center gap-3 mb-1">
                <h1 className="text-lg font-bold">{order.orderNo}</h1>
                <OrderStatusBadge status={order.status} />
              </div>
              <p className="text-sm text-muted-foreground">
                创建时间：{formatFull(order.createdAt)}
                {order.payTime && ` · 支付时间：${formatFull(order.payTime)}`}
              </p>
            </div>

            {/* 操作按钮 */}
            <div className="flex items-center gap-2">
              {canPay && (
                <Button size="sm" onClick={handlePay} disabled={actionLoading}>
                  {actionLoading && <Loader2 className="h-3.5 w-3.5 animate-spin mr-1" />}
                  立即支付
                </Button>
              )}
              {canCancel && (
                <Dialog open={cancelOpen} onOpenChange={setCancelOpen}>
                  <DialogTrigger render={<Button size="sm" variant="outline" disabled={actionLoading}>取消订单</Button>} />
                  <DialogContent>
                    <DialogHeader>
                      <DialogTitle>确认取消</DialogTitle>
                      <DialogDescription>确定要取消此订单吗？取消后无法恢复。</DialogDescription>
                    </DialogHeader>
                    <div className="flex justify-end gap-2 mt-4">
                      <Button variant="ghost" onClick={() => setCancelOpen(false)}>返回</Button>
                      <Button variant="destructive" onClick={handleCancel} disabled={actionLoading}>确认取消</Button>
                    </div>
                  </DialogContent>
                </Dialog>
              )}
              {canChange && (
                <span title={changeDisabledReason || undefined} className="inline-flex">
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={!canChangeEnabled}
                    onClick={() => router.push(`/orders/${id}/change`)}
                  >
                    改签
                  </Button>
                </span>
              )}
              {canRefund && (
                <Dialog open={refundOpen} onOpenChange={setRefundOpen}>
                  <DialogTrigger render={<Button size="sm" variant="outline" disabled={actionLoading}>退票</Button>} />
                  <DialogContent>
                    <DialogHeader>
                      <DialogTitle>退票申请</DialogTitle>
                      <DialogDescription>预计退款金额约 ¥{order.totalAmount.toLocaleString()}（实际金额以审核为准）</DialogDescription>
                    </DialogHeader>
                    <div className="space-y-3 mt-2">
                      <div className="space-y-1.5">
                        <span className="text-sm">退票原因（选填）</span>
                        <Textarea
                          placeholder="请输入退票原因..."
                          value={refundReason}
                          onChange={(e) => setRefundReason(e.target.value)}
                          rows={3}
                        />
                      </div>
                    </div>
                    <div className="flex justify-end gap-2 mt-4">
                      <Button variant="ghost" onClick={() => setRefundOpen(false)}>返回</Button>
                      <Button variant="destructive" onClick={handleRefund} disabled={actionLoading}>
                        {actionLoading && <Loader2 className="h-3.5 w-3.5 animate-spin mr-1" />}
                        确认退票
                      </Button>
                    </div>
                  </DialogContent>
                </Dialog>
              )}
            </div>
          </CardContent>
        </Card>

        <div className="grid gap-6 md:grid-cols-[1fr_260px]">
          {/* 左：航班 + 乘机人 */}
          <div className="space-y-6">
            {/* 航班信息 */}
            <Card>
              <CardContent className="p-5">
                <h2 className="font-semibold mb-3">航班信息</h2>
                <div className="flex items-center gap-3 mb-3">
                  <div className="flex items-center justify-center h-9 w-9 rounded-lg bg-primary/10">
                    <Plane className="h-4 w-4 text-primary" />
                  </div>
                  <div>
                    <p className="font-medium text-sm">
                      {order.airlineName || "—"} <Badge variant="outline" className="ml-1 text-xs">{order.flightNo}</Badge>
                    </p>
                    <p className="text-xs text-muted-foreground flex items-center gap-1">
                      <MapPin className="h-3 w-3" />
                      {order.departureCity || "—"} → {order.arrivalCity || "—"}
                    </p>
                  </div>
                </div>
                {(order.departureTime || order.arrivalTime) && (
                  <p className="text-sm text-muted-foreground flex items-center gap-1">
                    <Clock className="h-3.5 w-3.5" />
                    {order.departureTime ? formatTime(order.departureTime) : "—"} →{" "}
                    {order.arrivalTime ? formatTime(order.arrivalTime) : "—"}
                  </p>
                )}
              </CardContent>
            </Card>

            {/* 乘机人 */}
            <Card>
              <CardContent className="p-5">
                <h2 className="font-semibold mb-3">乘机人</h2>
                <div className="space-y-2">
                  {order.passengers.map((p) => (
                    <div key={p.passengerId} className="flex items-center justify-between text-sm py-2 border-b border-slate-100 last:border-0">
                      <div>
                        <span className="font-medium">{p.passengerName}</span>
                        <span className="text-muted-foreground ml-2">
                          {PASSENGER_TYPE_LABEL[p.passengerType]}
                        </span>
                      </div>
                      <div className="text-right">
                        <span className="text-sm">{p.seatNo}</span>
                        <span className="text-muted-foreground ml-3">¥{p.ticketPrice.toLocaleString()}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          </div>

          {/* 右：价格明细 */}
          <Card className="h-fit sticky top-24">
            <CardContent className="p-5">
              <h2 className="font-semibold mb-3">价格明细</h2>
              <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">票价</span>
                  <span>¥{order.ticketAmount.toLocaleString()}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">机场建设费</span>
                  <span>¥{order.airportFee.toLocaleString()}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">燃油费</span>
                  <span>¥{order.fuelFee.toLocaleString()}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">服务费</span>
                  <span>¥{order.serviceFee.toLocaleString()}</span>
                </div>
                <Separator />
                <div className="flex justify-between font-bold text-base">
                  <span>合计</span>
                  <FlightPriceTag price={order.totalAmount} className="text-base" />
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </UserLayout>
  )
}

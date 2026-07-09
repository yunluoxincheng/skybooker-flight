"use client"

import { Loader2 } from "lucide-react"
import { FlightPriceTag } from "@/components/common/FlightPriceTag"
import { OrderStatusBadge } from "@/components/common/OrderStatusBadge"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { formatDateFull, formatTime } from "@/lib/date-utils"
import { PASSENGER_TYPE_LABEL } from "@/lib/passenger-utils"
import type { OrderStatus, OrderVO } from "@/types/order"

interface RefundRecordItem {
  id: number
  orderId: number
  refundAmount: number
  feeAmount: number
  reason: string
  status: string
  createdAt: string
  completedAt?: string
}

interface ChangeRecordItem {
  id: number
  orderNo: string
  oldFlightNo: string
  newFlightNo: string
  oldDepartureTime: string
  newDepartureTime: string
  feeAmount: number
  priceDiff: number
  status: string
  createdAt: string
}

interface PaymentInfoItem {
  payTime?: string
  payAmount?: number
  payMethod?: string
  tradeNo?: string
}

interface OrderStatusTimelineItem {
  status: OrderStatus
  title?: string
  description?: string
  operatorName?: string
  occurredAt?: string
}

export interface AdminOrderDetailData extends OrderVO {
  refundRecords: RefundRecordItem[]
  changeRecords: ChangeRecordItem[]
  paymentInfo?: PaymentInfoItem
  statusTimeline: OrderStatusTimelineItem[]
}

interface OrderDetailSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  order: AdminOrderDetailData | null
  loading?: boolean
}

function formatDateTime(iso?: string | null) {
  if (!iso) return "—"
  return `${formatDateFull(iso)} ${formatTime(iso)}`
}

function formatMoney(value?: number | null) {
  if (typeof value !== "number") return "暂无数据"
  return `¥${value.toLocaleString()}`
}

export function OrderDetailSheet({
  open,
  onOpenChange,
  order,
  loading = false,
}: OrderDetailSheetProps) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-2xl">
        <SheetHeader className="space-y-2 border-b">
          <SheetTitle>订单详情</SheetTitle>
          <SheetDescription>
            查看订单基础信息与乘机人详情。退票/改签记录和状态流转需后端增强接口支持。
          </SheetDescription>
        </SheetHeader>

        {loading ? (
          <div className="flex min-h-[320px] items-center justify-center">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        ) : !order ? (
          <div className="px-4 py-10 text-center text-sm text-muted-foreground">暂无订单详情</div>
        ) : (
          <div className="space-y-6 p-4">
            <section className="space-y-3 rounded-xl border p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-xs text-muted-foreground">订单号</p>
                  <p className="font-mono text-sm">{order.orderNo}</p>
                </div>
                <OrderStatusBadge status={order.status} />
              </div>

              <div className="grid gap-3 text-sm md:grid-cols-2">
                <div>
                  <p className="text-xs text-muted-foreground">用户</p>
                  <p>{order.userNickname || "未设置昵称"}</p>
                  <p className="text-muted-foreground">{order.userEmail}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">航班</p>
                  <p>{order.airlineName || "—"} {order.flightNo}</p>
                  <p className="text-muted-foreground">
                    {order.departureCity || "—"} → {order.arrivalCity || "—"}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">出发时间</p>
                  <p>{formatDateTime(order.departureTime)}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">创建时间</p>
                  <p>{formatDateTime(order.createdAt)}</p>
                </div>
              </div>
            </section>

            <section className="space-y-3 rounded-xl border p-4">
              <div className="flex items-center justify-between">
                <h3 className="font-medium">乘机人与费用</h3>
                <span className="text-xs text-muted-foreground">{order.passengers.length} 位乘机人</span>
              </div>
              <div className="space-y-2">
                {order.passengers.map((passenger) => (
                  <div
                    key={passenger.passengerId}
                    className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-muted/40 px-3 py-2 text-sm"
                  >
                    <div>
                      <p className="font-medium">{passenger.passengerName}</p>
                      <p className="text-xs text-muted-foreground">
                        {PASSENGER_TYPE_LABEL[passenger.passengerType]} · 座位 {passenger.seatNo}
                      </p>
                    </div>
                    <FlightPriceTag price={passenger.ticketPrice} className="text-sm" />
                  </div>
                ))}
              </div>
              <Separator />
              <div className="space-y-1 text-sm">
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">票价</span>
                  <span>{formatMoney(order.ticketAmount)}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">机场建设费</span>
                  <span>{formatMoney(order.airportFee)}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">燃油费</span>
                  <span>{formatMoney(order.fuelFee)}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">服务费</span>
                  <span>{formatMoney(order.serviceFee)}</span>
                </div>
                <Separator />
                <div className="flex items-center justify-between font-medium">
                  <span>订单总额</span>
                  <FlightPriceTag price={order.totalAmount} />
                </div>
              </div>
            </section>

            <section className="rounded-xl border p-4">
              <h3 className="mb-3 font-medium">支付信息</h3>
              <div className="grid gap-3 text-sm md:grid-cols-2">
                <div>
                  <p className="text-xs text-muted-foreground">支付方式</p>
                  <p>{order.paymentInfo?.payMethod || "暂无数据"}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">支付金额</p>
                  <p>{formatMoney(order.paymentInfo?.payAmount)}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">交易号</p>
                  <p className="break-all">{order.paymentInfo?.tradeNo || "暂无数据"}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">支付时间</p>
                  <p>{order.paymentInfo?.payTime ? formatDateTime(order.paymentInfo.payTime) : "暂无数据"}</p>
                </div>
              </div>
              <p className="mt-3 text-xs text-muted-foreground">支付详情需后端增强接口支持</p>
            </section>

            <section className="rounded-xl border p-4">
              <div className="mb-3 flex items-center justify-between">
                <h3 className="font-medium">退票记录</h3>
                <Badge variant="outline">{order.refundRecords.length}</Badge>
              </div>
              {order.refundRecords.length === 0 ? (
                <p className="text-sm text-muted-foreground">暂无退票记录（需后端增强接口支持）</p>
              ) : (
                <div className="space-y-3">
                  {order.refundRecords.map((record) => (
                    <div key={record.id} className="rounded-lg bg-muted/40 p-3 text-sm">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="font-medium">退款 {formatMoney(record.refundAmount)}</span>
                        <Badge variant="outline">{record.status}</Badge>
                      </div>
                      <p className="mt-1 text-muted-foreground">
                        手续费 {formatMoney(record.feeAmount)} · 原因：{record.reason}
                      </p>
                      <p className="mt-1 text-xs text-muted-foreground">
                        创建于 {formatDateTime(record.createdAt)}
                        {record.completedAt ? ` · 完成于 ${formatDateTime(record.completedAt)}` : ""}
                      </p>
                    </div>
                  ))}
                </div>
              )}
            </section>

            <section className="rounded-xl border p-4">
              <div className="mb-3 flex items-center justify-between">
                <h3 className="font-medium">改签记录</h3>
                <Badge variant="outline">{order.changeRecords.length}</Badge>
              </div>
              {order.changeRecords.length === 0 ? (
                <p className="text-sm text-muted-foreground">暂无改签记录（需后端增强接口支持）</p>
              ) : (
                <div className="space-y-3">
                  {order.changeRecords.map((record) => (
                    <div key={record.id} className="rounded-lg bg-muted/40 p-3 text-sm">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="font-medium">
                          {record.oldFlightNo} → {record.newFlightNo}
                        </span>
                        <Badge variant="outline">{record.status}</Badge>
                      </div>
                      <p className="mt-1 text-muted-foreground">
                        差价 {formatMoney(record.priceDiff)} · 手续费 {formatMoney(record.feeAmount)}
                      </p>
                      <p className="mt-1 text-xs text-muted-foreground">
                        {formatDateTime(record.oldDepartureTime)} → {formatDateTime(record.newDepartureTime)}
                      </p>
                      <p className="mt-1 text-xs text-muted-foreground">
                        创建于 {formatDateTime(record.createdAt)}
                      </p>
                    </div>
                  ))}
                </div>
              )}
            </section>

            <section className="rounded-xl border p-4">
              <div className="mb-3 flex items-center justify-between">
                <h3 className="font-medium">状态流转</h3>
                <Badge variant="outline">{order.statusTimeline.length}</Badge>
              </div>
              {order.statusTimeline.length === 0 ? (
                <p className="text-sm text-muted-foreground">暂无状态流转记录（需后端增强接口支持）</p>
              ) : (
                <div className="space-y-4">
                  {order.statusTimeline.map((item, index) => (
                    <div key={`${item.status}-${item.occurredAt || index}`} className="relative pl-5">
                      {index < order.statusTimeline.length - 1 && (
                        <span className="absolute left-[7px] top-5 h-[calc(100%+0.5rem)] w-px bg-border" />
                      )}
                      <span className="absolute left-0 top-1.5 h-3.5 w-3.5 rounded-full bg-primary/20 ring-4 ring-primary/5" />
                      <div className="space-y-1 text-sm">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-medium">{item.title || item.status}</span>
                          <Badge variant="outline">{item.status}</Badge>
                        </div>
                        {item.description && (
                          <p className="text-muted-foreground">{item.description}</p>
                        )}
                        <p className="text-xs text-muted-foreground">
                          {formatDateTime(item.occurredAt)}
                          {item.operatorName ? ` · 操作人 ${item.operatorName}` : ""}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>
        )}
      </SheetContent>
    </Sheet>
  )
}

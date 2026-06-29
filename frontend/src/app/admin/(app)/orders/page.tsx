"use client"

import { useEffect, useState, useCallback } from "react"
import { Eye, ChevronLeft, ChevronRight, Loader2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { OrderStatusBadge } from "@/components/common/OrderStatusBadge"
import { FlightPriceTag } from "@/components/common/FlightPriceTag"
import * as adminApi from "@/services/adminApi"
import type { OrderVO } from "@/types/order"
import type { ApiError } from "@/lib/request"

export default function AdminOrdersPage() {
  const [orders, setOrders] = useState<OrderVO[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [statusFilter, setStatusFilter] = useState("")
  const [orderNoFilter, setOrderNoFilter] = useState("")
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // 详情 Drawer
  const [detailOrder, setDetailOrder] = useState<OrderVO | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)

  const fetchOrders = useCallback(async () => {
    try {
      const params: Record<string, string | number | boolean | undefined> = { page, size: 10 }
      if (statusFilter) params.status = statusFilter
      if (orderNoFilter) params.orderNo = orderNoFilter
      const data = await adminApi.getAdminOrders(params)
      setOrders(data.records)
      setTotal(data.total)
      setError(null)
    } catch (err) {
      setError((err as ApiError).message || "加载订单失败")
    } finally {
      setIsLoading(false)
    }
  }, [page, statusFilter, orderNoFilter])

  useEffect(() => { fetchOrders() }, [fetchOrders])

  const openDetail = async (order: OrderVO) => {
    setDetailOrder(order)
    setDetailLoading(true)
    try {
      const fresh = await adminApi.getAdminOrderById(order.id)
      setDetailOrder(fresh)
    } catch {
      // keep existing
    } finally {
      setDetailLoading(false)
    }
  }

  const formatTime = (iso: string) => {
    if (!iso) return ""
    return new Date(iso).toLocaleString("zh-CN")
  }

  const totalPages = Math.max(1, Math.ceil(total / 10))

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">订单管理</h1>

      {/* 筛选栏 */}
      <div className="flex flex-wrap gap-3">
        <Input
          placeholder="搜索订单号..."
          className="w-60"
          value={orderNoFilter}
          onChange={(e) => { setIsLoading(true); setOrderNoFilter(e.target.value); setPage(1) }}
        />
        <Select value={statusFilter} onValueChange={(v) => { if (!v) return; setIsLoading(true); setStatusFilter(v); setPage(1) }}>
          <SelectTrigger className="w-36">
            <SelectValue placeholder="全部状态" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="全部">全部状态</SelectItem>
            <SelectItem value="PENDING_PAYMENT">待支付</SelectItem>
            <SelectItem value="ISSUED">已出票</SelectItem>
            <SelectItem value="CHANGED">已改签</SelectItem>
            <SelectItem value="REFUNDED">已退票</SelectItem>
            <SelectItem value="CANCELLED">已取消</SelectItem>
          </SelectContent>
        </Select>
        <Button variant="outline" size="sm" onClick={() => { setIsLoading(true); setStatusFilter(""); setOrderNoFilter(""); setPage(1) }}>
          清除
        </Button>
      </div>

      {/* Table */}
      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-12 w-full rounded-lg" />)}
        </div>
      ) : error ? (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-8 text-center text-destructive">{error}</div>
      ) : (
        <div className="rounded-xl border bg-white overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>订单号</TableHead>
                <TableHead>用户</TableHead>
                <TableHead>航班号</TableHead>
                <TableHead>金额</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>创建时间</TableHead>
                <TableHead className="w-[80px]">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {orders.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} className="text-center text-muted-foreground py-8">暂无订单数据</TableCell>
                </TableRow>
              ) : (
                orders.map((o) => (
                  <TableRow key={o.id}>
                    <TableCell className="font-mono text-xs">{o.orderNo}</TableCell>
                    <TableCell>{o.userNickname || o.userEmail}</TableCell>
                    <TableCell>{o.flightNo}</TableCell>
                    <TableCell><FlightPriceTag price={o.totalAmount} className="text-sm" /></TableCell>
                    <TableCell><OrderStatusBadge status={o.status} /></TableCell>
                    <TableCell className="text-sm">{formatTime(o.createdAt)}</TableCell>
                    <TableCell>
                      <Button variant="ghost" size="sm" onClick={() => openDetail(o)}>
                        <Eye className="h-3.5 w-3.5" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-2">
          <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => { setIsLoading(true); setPage(page - 1) }}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-sm text-muted-foreground">{page} / {totalPages}</span>
          <Button variant="outline" size="sm" disabled={page >= totalPages} onClick={() => { setIsLoading(true); setPage(page + 1) }}>
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}

      {/* 详情 Drawer */}
      <Sheet open={!!detailOrder} onOpenChange={() => setDetailOrder(null)}>
        <SheetContent className="w-[420px] sm:max-w-[420px] overflow-y-auto">
          <SheetHeader>
            <SheetTitle>订单详情</SheetTitle>
          </SheetHeader>
          {detailLoading ? (
            <div className="flex justify-center py-12">
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            </div>
          ) : detailOrder ? (
            <div className="space-y-4 mt-4">
              <div>
                <p className="text-xs text-muted-foreground">订单号</p>
                <p className="font-mono text-sm">{detailOrder.orderNo}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">状态</p>
                <OrderStatusBadge status={detailOrder.status} />
              </div>
              <div>
                <p className="text-xs text-muted-foreground">用户</p>
                <p className="text-sm">{detailOrder.userNickname || "—"} ({detailOrder.userEmail})</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">航班</p>
                <p className="text-sm">{detailOrder.airlineName} {detailOrder.flightNo}</p>
                <p className="text-xs text-muted-foreground">
                  {detailOrder.departureCity} → {detailOrder.arrivalCity}
                  {detailOrder.departureTime && ` · ${formatTime(detailOrder.departureTime)}`}
                </p>
              </div>
              <Separator />
              <div>
                <p className="text-xs text-muted-foreground mb-2">乘机人</p>
                {detailOrder.passengers.map((p) => (
                  <div key={p.passengerId} className="flex justify-between text-sm py-1">
                    <span>{p.passengerName}</span>
                    <span className="text-muted-foreground">{p.seatNo} ¥{p.ticketPrice}</span>
                  </div>
                ))}
              </div>
              <Separator />
              <div className="space-y-1 text-sm">
                <div className="flex justify-between"><span className="text-muted-foreground">票价</span><span>¥{detailOrder.ticketAmount.toLocaleString()}</span></div>
                <div className="flex justify-between"><span className="text-muted-foreground">机场建设费</span><span>¥{detailOrder.airportFee.toLocaleString()}</span></div>
                <div className="flex justify-between"><span className="text-muted-foreground">燃油费</span><span>¥{detailOrder.fuelFee.toLocaleString()}</span></div>
                <div className="flex justify-between"><span className="text-muted-foreground">服务费</span><span>¥{detailOrder.serviceFee.toLocaleString()}</span></div>
                <Separator />
                <div className="flex justify-between font-bold"><span>合计</span><FlightPriceTag price={detailOrder.totalAmount} /></div>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">创建时间</p>
                <p className="text-sm">{formatTime(detailOrder.createdAt)}</p>
              </div>
              {detailOrder.payTime && (
                <div>
                  <p className="text-xs text-muted-foreground">支付时间</p>
                  <p className="text-sm">{formatTime(detailOrder.payTime)}</p>
                </div>
              )}
            </div>
          ) : null}
        </SheetContent>
      </Sheet>
    </div>
  )
}

"use client"

import { useCallback, useEffect, useState } from "react"
import {
  ArrowRightLeft,
  ChevronLeft,
  ChevronRight,
  Eye,
  MoreHorizontal,
  Plus,
  Trash2,
  Undo2,
} from "lucide-react"
import { FlightPriceTag } from "@/components/common/FlightPriceTag"
import { OrderStatusBadge } from "@/components/common/OrderStatusBadge"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { formatDateFull, formatTime } from "@/lib/date-utils"
import type { ApiError } from "@/lib/request"
import * as adminApi from "@/services/adminApi"
import type { AdminOrderDetailVO, AdminOrderQueryDTO, OrderVO } from "@/types/order"
import { ChangeOrderDialog } from "./_components/ChangeOrderDialog"
import { CancelOrderDialog } from "./_components/CancelOrderDialog"
import { DeleteCancelDialog } from "./_components/DeleteCancelDialog"
import { OrderDetailSheet } from "./_components/OrderDetailSheet"
import { OrderFormDialog } from "./_components/OrderFormDialog"
import { RefundConfirmDialog } from "./_components/RefundConfirmDialog"

const PAGE_SIZE = 10
const ORDER_STATUS_LABELS: Record<string, string> = {
  ALL: "全部状态",
  PENDING_PAYMENT: "待支付",
  ISSUED: "已出票",
  CHANGED: "已改签",
  CHANGE_PENDING: "改签处理中",
  REFUNDED: "已退票",
  CANCELLED: "已取消",
  VOIDED: "已作废",
}

function formatDateTime(iso?: string | null) {
  if (!iso) return "—"
  return `${formatDateFull(iso)} ${formatTime(iso)}`
}

export default function AdminOrdersPage() {
  const [orders, setOrders] = useState<OrderVO[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [statusFilter, setStatusFilter] = useState("ALL")
  const [orderNoFilter, setOrderNoFilter] = useState("")
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [formOpen, setFormOpen] = useState(false)

  const [detailOpen, setDetailOpen] = useState(false)
  const [detailOrder, setDetailOrder] = useState<AdminOrderDetailVO | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)

  const [refundOrder, setRefundOrder] = useState<OrderVO | null>(null)
  const [changeOrder, setChangeOrder] = useState<OrderVO | null>(null)
  const [cancelOrder, setCancelOrder] = useState<OrderVO | null>(null)
  const [deleteOrder, setDeleteOrder] = useState<OrderVO | null>(null)

  const fetchOrders = useCallback(async () => {
    setIsLoading(true)
    try {
      const params: AdminOrderQueryDTO = {
        page,
        size: PAGE_SIZE,
      }
      if (statusFilter !== "ALL") {
        params.status = statusFilter
      }
      if (orderNoFilter.trim()) {
        params.orderNo = orderNoFilter.trim()
      }

      const data = await adminApi.getAdminOrders(params)
      setOrders(data.records)
      setTotal(data.total)
      setError(null)
    } catch (err) {
      setError((err as ApiError).message || "加载订单失败")
    } finally {
      setIsLoading(false)
    }
  }, [orderNoFilter, page, statusFilter])

  useEffect(() => {
    fetchOrders()
  }, [fetchOrders])

  const refreshOrders = useCallback(async () => {
    await fetchOrders()
  }, [fetchOrders])

  const openCreate = () => {
    setFormOpen(true)
  }

  const openDetail = async (order: OrderVO) => {
    setDetailOpen(true)
    setDetailOrder(null)
    setDetailLoading(true)
    try {
      const fresh = await adminApi.getAdminOrderDetailEnhanced(order.id)
      setDetailOrder(fresh)
    } catch {
      setDetailOrder(null)
    } finally {
      setDetailLoading(false)
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">订单管理</h1>
          <p className="text-sm text-muted-foreground">
            按订单状态提供取消、退票、改签和作废操作。
          </p>
        </div>
        <Button onClick={openCreate}>
          <Plus className="h-4 w-4" />
          新增订单
        </Button>
      </div>

      <div className="flex flex-wrap gap-3">
        <Input
          placeholder="搜索订单号"
          className="w-60"
          value={orderNoFilter}
          onChange={(event) => {
            setOrderNoFilter(event.target.value)
            setPage(1)
          }}
        />
        <Select
          value={statusFilter}
          onValueChange={(value) => {
            setStatusFilter(value ?? "ALL")
            setPage(1)
          }}
        >
          <SelectTrigger className="w-40">
            <SelectValue>{ORDER_STATUS_LABELS[statusFilter]}</SelectValue>
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">全部状态</SelectItem>
            <SelectItem value="PENDING_PAYMENT">待支付</SelectItem>
            <SelectItem value="ISSUED">已出票</SelectItem>
            <SelectItem value="CHANGED">已改签</SelectItem>
            <SelectItem value="CHANGE_PENDING">改签处理中</SelectItem>
            <SelectItem value="REFUNDED">已退票</SelectItem>
            <SelectItem value="CANCELLED">已取消</SelectItem>
            <SelectItem value="VOIDED">已作废</SelectItem>
          </SelectContent>
        </Select>
        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            setStatusFilter("ALL")
            setOrderNoFilter("")
            setPage(1)
          }}
        >
          清除
        </Button>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, index) => (
            <Skeleton key={index} className="h-12 w-full rounded-lg" />
          ))}
        </div>
      ) : error ? (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-8 text-center text-destructive">
          {error}
        </div>
      ) : (
        <div className="overflow-hidden rounded-xl border bg-white">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>订单号</TableHead>
                <TableHead>用户</TableHead>
                <TableHead>航班号</TableHead>
                <TableHead>金额</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>创建时间</TableHead>
                <TableHead className="w-[96px]">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {orders.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                    暂无订单数据
                  </TableCell>
                </TableRow>
              ) : (
                orders.map((order) => (
                  <TableRow key={order.id}>
                    <TableCell className="font-mono text-xs">{order.orderNo}</TableCell>
                    <TableCell>{order.userNickname || order.userEmail}</TableCell>
                    <TableCell>{order.flightNo}</TableCell>
                    <TableCell>
                      <FlightPriceTag price={order.totalAmount} className="text-sm" />
                    </TableCell>
                    <TableCell>
                      <OrderStatusBadge status={order.status} />
                    </TableCell>
                    <TableCell className="text-sm">{formatDateTime(order.createdAt)}</TableCell>
                    <TableCell>
                      <DropdownMenu>
                        <DropdownMenuTrigger
                          render={
                            <Button variant="ghost" size="icon-sm" aria-label="订单操作" />
                          }
                        >
                          <MoreHorizontal className="h-4 w-4" />
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => openDetail(order)}>
                            <Eye className="h-4 w-4" />
                            查看详情
                          </DropdownMenuItem>
                          {order.status === "PENDING_PAYMENT" ? (
                            <>
                              <DropdownMenuSeparator />
                              <DropdownMenuItem variant="destructive" onClick={() => setCancelOrder(order)}>
                                <Undo2 className="h-4 w-4" />
                                取消（不退款）
                              </DropdownMenuItem>
                            </>
                          ) : null}
                          {order.status === "ISSUED" || order.status === "CHANGED" ? (
                            <>
                              <DropdownMenuSeparator />
                              <DropdownMenuItem onClick={() => setRefundOrder(order)}>
                                <Undo2 className="h-4 w-4" />
                                退票
                              </DropdownMenuItem>
                              <DropdownMenuItem onClick={() => setChangeOrder(order)}>
                                <ArrowRightLeft className="h-4 w-4" />
                                改签
                              </DropdownMenuItem>
                            </>
                          ) : null}
                          {order.status === "CANCELLED" || order.status === "REFUNDED" ? (
                            <>
                              <DropdownMenuSeparator />
                              <DropdownMenuItem variant="destructive" onClick={() => setDeleteOrder(order)}>
                                <Trash2 className="h-4 w-4" />
                                作废
                              </DropdownMenuItem>
                            </>
                          ) : null}
                        </DropdownMenuContent>
                      </DropdownMenu>
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
          <Button
            variant="outline"
            size="sm"
            disabled={page <= 1}
            onClick={() => {
              setPage(page - 1)
            }}
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-sm text-muted-foreground">
            {page} / {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages}
            onClick={() => {
              setPage(page + 1)
            }}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}

      <OrderFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        mode="create"
        order={null}
        onSuccess={refreshOrders}
      />

      <RefundConfirmDialog
        open={Boolean(refundOrder)}
        onOpenChange={(open) => {
          if (!open) setRefundOrder(null)
        }}
        order={refundOrder}
        onSuccess={refreshOrders}
      />

      <ChangeOrderDialog
        open={Boolean(changeOrder)}
        onOpenChange={(open) => {
          if (!open) setChangeOrder(null)
        }}
        order={changeOrder}
        onSuccess={refreshOrders}
      />

      <DeleteCancelDialog
        open={Boolean(deleteOrder)}
        onOpenChange={(open) => {
          if (!open) setDeleteOrder(null)
        }}
        order={deleteOrder}
        onSuccess={refreshOrders}
      />

      <CancelOrderDialog
        open={Boolean(cancelOrder)}
        onOpenChange={(open) => {
          if (!open) setCancelOrder(null)
        }}
        order={cancelOrder}
        onSuccess={refreshOrders}
      />

      <OrderDetailSheet
        open={detailOpen}
        onOpenChange={setDetailOpen}
        order={detailOrder}
        loading={detailLoading}
      />
    </div>
  )
}

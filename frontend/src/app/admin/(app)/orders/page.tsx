"use client"

import { useCallback, useEffect, useState } from "react"
import {
  ArrowRightLeft,
  ChevronLeft,
  ChevronRight,
  Eye,
  MoreHorizontal,
  Pencil,
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
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { formatDateFull, formatTime } from "@/lib/date-utils"
import type { ApiError } from "@/lib/request"
import * as adminApi from "@/services/adminApi"
import type { UserAdminVO } from "@/types/admin"
import type { FlightVO } from "@/types/flight"
import type { AdminOrderDetailVO, OrderVO } from "@/types/order"
import { ChangeOrderDialog } from "./_components/ChangeOrderDialog"
import { DeleteCancelDialog, type DeleteOrderAction } from "./_components/DeleteCancelDialog"
import { OrderDetailSheet } from "./_components/OrderDetailSheet"
import { OrderFormDialog } from "./_components/OrderFormDialog"
import { RefundConfirmDialog } from "./_components/RefundConfirmDialog"

type OrderFormMode = "create" | "edit"
const PAGE_SIZE = 10
const CLIENT_FILTER_BATCH_SIZE = 200

function formatDateTime(iso?: string | null) {
  if (!iso) return "—"
  return `${formatDateFull(iso)} ${formatTime(iso)}`
}

export default function AdminOrdersPage() {
  const [orders, setOrders] = useState<OrderVO[]>([])
  const [users, setUsers] = useState<UserAdminVO[]>([])
  const [flightsList, setFlightsList] = useState<FlightVO[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [statusFilter, setStatusFilter] = useState("ALL")
  const [orderNoFilter, setOrderNoFilter] = useState("")
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [formOpen, setFormOpen] = useState(false)
  const [formMode, setFormMode] = useState<OrderFormMode>("create")
  const [editingOrder, setEditingOrder] = useState<OrderVO | null>(null)

  const [detailOpen, setDetailOpen] = useState(false)
  const [detailOrder, setDetailOrder] = useState<AdminOrderDetailVO | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)

  const [refundOrder, setRefundOrder] = useState<OrderVO | null>(null)
  const [changeOrder, setChangeOrder] = useState<OrderVO | null>(null)
  const [deleteOrder, setDeleteOrder] = useState<OrderVO | null>(null)
  const [deleteInitialType, setDeleteInitialType] = useState<DeleteOrderAction>("cancel")

  const fetchOrders = useCallback(async () => {
    setIsLoading(true)
    try {
      const hasClientFilter = statusFilter !== "ALL" || Boolean(orderNoFilter.trim())
      const params: Record<string, string | number | boolean | undefined> = hasClientFilter
        ? { page: 1, size: CLIENT_FILTER_BATCH_SIZE }
        : { page, size: PAGE_SIZE }
      const data = await adminApi.getAdminOrders(params)
      if (hasClientFilter) {
        const normalizedKeyword = orderNoFilter.trim().toLowerCase()
        const filtered = data.records.filter((order) => {
          const matchesStatus = statusFilter === "ALL" || order.status === statusFilter
          const matchesOrderNo = !normalizedKeyword || order.orderNo.toLowerCase().includes(normalizedKeyword)
          return matchesStatus && matchesOrderNo
        })
        const startIndex = (page - 1) * PAGE_SIZE
        setOrders(filtered.slice(startIndex, startIndex + PAGE_SIZE))
        setTotal(filtered.length)
      } else {
        setOrders(data.records)
        setTotal(data.total)
      }
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

  useEffect(() => {
    let cancelled = false

    const fetchRefs = async () => {
      try {
        const [usersData, flightsData] = await Promise.all([
          adminApi.getUsers({ page: 1, size: 200, role: "USER" }),
          adminApi.getFlights({ page: 1, size: 200, publishStatus: "PUBLISHED" }),
        ])

        if (!cancelled) {
          setUsers(usersData.records)
          setFlightsList(flightsData.records)
        }
      } catch {
        if (!cancelled) {
          setUsers([])
          setFlightsList([])
        }
      }
    }

    void fetchRefs()

    return () => {
      cancelled = true
    }
  }, [])

  const refreshOrders = useCallback(async () => {
    await fetchOrders()
  }, [fetchOrders])

  const openCreate = () => {
    setFormMode("create")
    setEditingOrder(null)
    setFormOpen(true)
  }

  const openEdit = (order: OrderVO) => {
    setFormMode("edit")
    setEditingOrder(order)
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
            支持新增、编辑、退票、改签、作废和高风险删除操作。
          </p>
        </div>
        <Button onClick={openCreate}>
          <Plus className="h-4 w-4" />
          新增订单
        </Button>
      </div>

      <div className="flex flex-wrap gap-3">
        <Input
          placeholder="搜索订单号（客户端筛选）"
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
            <SelectValue placeholder="全部状态" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">全部状态</SelectItem>
            <SelectItem value="PENDING_PAYMENT">待支付</SelectItem>
            <SelectItem value="ISSUED">已出票</SelectItem>
            <SelectItem value="CHANGED">已改签</SelectItem>
            <SelectItem value="REFUNDED">已退票</SelectItem>
            <SelectItem value="CANCELLED">已取消</SelectItem>
            <SelectItem value="EXPIRED">已过期</SelectItem>
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
                          <Tooltip>
                            <TooltipTrigger render={<span className="block w-full" tabIndex={0} />}>
                              <span className="block w-full">
                                <DropdownMenuItem disabled onClick={() => openEdit(order)}>
                                  <Pencil className="h-4 w-4" />
                                  编辑
                                </DropdownMenuItem>
                              </span>
                            </TooltipTrigger>
                            <TooltipContent>编辑接口 (PUT) 后端尚未实现</TooltipContent>
                          </Tooltip>
                          <DropdownMenuItem onClick={() => setRefundOrder(order)}>
                            <Undo2 className="h-4 w-4" />
                            退票
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => setChangeOrder(order)}>
                            <ArrowRightLeft className="h-4 w-4" />
                            改签
                          </DropdownMenuItem>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem
                            onClick={() => {
                              setDeleteInitialType("cancel")
                              setDeleteOrder(order)
                            }}
                          >
                            <Trash2 className="h-4 w-4" />
                            作废订单
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            variant="destructive"
                            onClick={() => {
                              setDeleteInitialType("delete")
                              setDeleteOrder(order)
                            }}
                          >
                            <Trash2 className="h-4 w-4" />
                            删除订单
                          </DropdownMenuItem>
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
        mode={formMode}
        order={editingOrder}
        users={users}
        flights={flightsList}
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
        initialType={deleteInitialType}
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

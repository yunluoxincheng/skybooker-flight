"use client"

import { useEffect, useState } from "react"
import {
  DollarSign,
  Users,
  Ticket,
  TrendingUp,
} from "lucide-react"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
} from "recharts"
import * as adminApi from "@/services/adminApi"
import type { DashboardSummaryVO, HotRouteVO, OrderStatusDistributionVO } from "@/types/admin"

const PIE_COLORS = ["#3b82f6", "#22c55e", "#f59e0b", "#ef4444", "#8b5cf6", "#6b7280"]

const STATUS_LABELS: Record<string, string> = {
  PENDING_PAYMENT: "待支付",
  ISSUED: "已出票",
  CHANGED: "已改签",
  REFUNDED: "已退票",
  CANCELLED: "已取消",
  EXPIRED: "已过期",
}

export default function AdminDashboardPage() {
  const [summary, setSummary] = useState<DashboardSummaryVO | null>(null)
  const [hotRoutes, setHotRoutes] = useState<HotRouteVO[]>([])
  const [statusDist, setStatusDist] = useState<OrderStatusDistributionVO[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([
      adminApi.getDashboardSummary(),
      adminApi.getHotRoutes(),
      adminApi.getOrderStatusDistribution(),
    ])
      .then(([s, h, d]) => {
        setSummary(s)
        setHotRoutes(h)
        setStatusDist(d)
      })
      .catch(() => setError("加载数据失败"))
      .finally(() => setIsLoading(false))
  }, [])

  if (isLoading) {
    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-bold">数据看板</h1>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
          ))}
        </div>
        <div className="grid gap-6 lg:grid-cols-2">
          <Skeleton className="h-80 rounded-xl" />
          <Skeleton className="h-80 rounded-xl" />
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div>
        <h1 className="text-2xl font-bold mb-4">数据看板</h1>
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-8 text-center text-destructive">
          {error}
        </div>
      </div>
    )
  }

  const stats = summary
    ? [
        { label: "今日订单数", value: summary.totalTicketOrders, icon: Ticket, color: "text-blue-600", bg: "bg-blue-50" },
        { label: "销售额", value: `¥${summary.grossIssuedOrderRevenue.toLocaleString()}`, icon: DollarSign, color: "text-emerald-600", bg: "bg-emerald-50" },
        { label: "退票数", value: summary.refundedTicketOrders, icon: TrendingUp, color: "text-orange-600", bg: "bg-orange-50" },
        { label: "候补订单", value: summary.totalWaitlistOrders, icon: Users, color: "text-violet-600", bg: "bg-violet-50" },
      ]
    : []

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">数据看板</h1>

      {/* Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map((s) => (
          <Card key={s.label}>
            <CardContent className="p-5 flex items-center gap-4">
              <div className={`flex items-center justify-center h-12 w-12 rounded-xl ${s.bg}`}>
                <s.icon className={`h-6 w-6 ${s.color}`} />
              </div>
              <div>
                <p className="text-sm text-muted-foreground">{s.label}</p>
                <p className="text-2xl font-bold tabular-nums">{s.value}</p>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Charts */}
      <div className="grid gap-6 lg:grid-cols-2">
        {/* 热门航线柱状图 */}
        <Card>
          <CardContent className="p-5">
            <h2 className="font-semibold mb-4">热门航线 Top 10</h2>
            <ResponsiveContainer width="100%" height={320}>
              <BarChart data={hotRoutes.slice(0, 10)} layout="vertical">
                <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                <XAxis type="number" fontSize={12} />
                <YAxis
                  type="category"
                  dataKey="departureCity"
                  fontSize={12}
                  tickFormatter={(city, i) => `${city} → ${hotRoutes[i]?.arrivalCity}`}
                  width={100}
                />
                <Tooltip
                  formatter={(value) => [`${value} 单`, "订单数"]}
                  labelFormatter={(_, payload) => {
                    const d = payload?.[0]?.payload
                    return d ? `${d.departureCity} → ${d.arrivalCity}` : ""
                  }}
                />
                <Bar dataKey="issuedOrderCount" fill="#3b82f6" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        {/* 订单状态饼图 */}
        <Card>
          <CardContent className="p-5">
            <h2 className="font-semibold mb-4">订单状态分布</h2>
            <ResponsiveContainer width="100%" height={320}>
              <PieChart>
                <Pie
                  data={statusDist}
                  dataKey="count"
                  nameKey="status"
                  cx="50%"
                  cy="50%"
                  outerRadius={100}
                  label={({ payload }) => `${STATUS_LABELS[payload.status] || payload.status}: ${payload.count}`}
                >
                  {statusDist.map((_, i) => (
                    <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip
                  formatter={(value, name) => [
                    `${value} 单`,
                    STATUS_LABELS[name as string] || name,
                  ]}
                />
              </PieChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

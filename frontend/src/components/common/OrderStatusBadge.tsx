import { Badge } from "@/components/ui/badge"
import type { OrderStatus } from "@/types/order"

const STATUS_MAP: Record<
  OrderStatus,
  { label: string; variant: "default" | "secondary" | "destructive" | "outline" }
> = {
  PENDING_PAYMENT: { label: "待支付", variant: "secondary" },
  ISSUED: { label: "已出票", variant: "default" },
  CHANGED: { label: "已改签", variant: "outline" },
  CHANGE_PENDING: { label: "改签处理中", variant: "secondary" },
  REFUNDED: { label: "已退票", variant: "destructive" },
  CANCELLED: { label: "已取消", variant: "outline" },
  VOIDED: { label: "已作废", variant: "secondary" },
}

interface OrderStatusBadgeProps {
  status: OrderStatus
  className?: string
}

export function OrderStatusBadge({ status, className }: OrderStatusBadgeProps) {
  const config = STATUS_MAP[status] || { label: status, variant: "outline" as const }
  return (
    <Badge variant={config.variant} className={className}>
      {config.label}
    </Badge>
  )
}

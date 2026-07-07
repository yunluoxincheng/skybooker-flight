import { Badge } from "@/components/ui/badge"
import type { WaitlistStatus } from "@/types/waitlist"

const STATUS_MAP: Record<
  WaitlistStatus,
  { label: string; variant: "default" | "secondary" | "destructive" | "outline" }
> = {
  PENDING_PAYMENT: { label: "待支付", variant: "secondary" },
  WAITING: { label: "排队中", variant: "default" },
  SUCCESS: { label: "候补成功", variant: "default" },
  FAILED: { label: "候补失败", variant: "destructive" },
  CANCELLED: { label: "已取消", variant: "outline" },
  REFUNDED: { label: "已退款", variant: "outline" },
  EXPIRED: { label: "已过期", variant: "outline" },
}

interface WaitlistStatusBadgeProps {
  status: WaitlistStatus
  className?: string
}

export function WaitlistStatusBadge({ status, className }: WaitlistStatusBadgeProps) {
  const config = STATUS_MAP[status] || { label: status, variant: "outline" as const }
  return (
    <Badge variant={config.variant} className={className}>
      {config.label}
    </Badge>
  )
}

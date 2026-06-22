import { Badge } from "@/components/ui/badge"
import type { FlightStatus } from "@/types/flight"

const STATUS_MAP: Record<
  FlightStatus,
  { label: string; variant: "default" | "secondary" | "destructive" | "outline" }
> = {
  ON_TIME: { label: "准点", variant: "default" },
  BOARDING: { label: "登机中", variant: "secondary" },
  DEPARTED: { label: "已起飞", variant: "outline" },
  ARRIVED: { label: "已到达", variant: "outline" },
  DELAYED: { label: "延误", variant: "destructive" },
  CANCELLED: { label: "已取消", variant: "destructive" },
}

interface FlightStatusBadgeProps {
  status: FlightStatus
  className?: string
}

export function FlightStatusBadge({ status, className }: FlightStatusBadgeProps) {
  const config = STATUS_MAP[status] || { label: status, variant: "outline" as const }
  return (
    <Badge variant={config.variant} className={className}>
      {config.label}
    </Badge>
  )
}

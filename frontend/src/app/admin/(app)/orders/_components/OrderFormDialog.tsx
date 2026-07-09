"use client"

import { useEffect, useMemo, useState } from "react"
import { z } from "zod"
import { FlightPriceTag } from "@/components/common/FlightPriceTag"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { formatDateFull, formatTime } from "@/lib/date-utils"
import { PASSENGER_TYPE_LABEL } from "@/lib/passenger-utils"
import { getDisplayName } from "@/lib/user-utils"
import type { UserAdminVO } from "@/types/admin"
import type { FlightVO } from "@/types/flight"
import type { OrderVO } from "@/types/order"

const orderFormSchema = z.object({
  userId: z.coerce.number().min(1, "请选择用户"),
  flightId: z.coerce.number().min(1, "请选择航班"),
  items: z.array(
    z.object({
      passengerId: z.number().min(1),
      seatId: z.coerce.number().min(1, "请选择座位"),
    })
  ).min(1, "请至少选择一位乘机人"),
})

type Mode = "create" | "edit"

interface OrderFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  mode: Mode
  order?: OrderVO | null
  users?: UserAdminVO[]
  flights?: FlightVO[]
  onSuccess?: () => Promise<void> | void
}

interface FormErrors {
  userId?: string
  flightId?: string
  items?: string
  seats: Record<number, string>
}

function formatDateTime(iso?: string | null) {
  if (!iso) return "—"
  return `${formatDateFull(iso)} ${formatTime(iso)}`
}

function formatFlightLabel(flight: FlightVO) {
  return `${flight.flightNo} · ${flight.departureCity} → ${flight.arrivalCity} · ${formatDateTime(flight.departureTime)}`
}

export function OrderFormDialog({
  open,
  onOpenChange,
  mode,
  order,
  users = [],
  flights = [],
}: OrderFormDialogProps) {
  const featureDisabledMessage = "管理端创建和编辑订单功能依赖后端接口，当前暂不可用。"
  const [userId, setUserId] = useState("")
  const [flightId, setFlightId] = useState("")
  const [selectedPassengerIds, setSelectedPassengerIds] = useState<number[]>([])
  const [seatAssignments, setSeatAssignments] = useState<Record<number, string>>({})
  const [errors, setErrors] = useState<FormErrors>({ seats: {} })

  const selectedFlight = useMemo(
    () => flights.find((flight) => flight.id === Number(flightId)) ?? null,
    [flightId, flights]
  )

  const editingSummary = useMemo(
    () => ({
      userName: order?.userNickname || order?.userEmail || "—",
      flightLabel: order ? `${order.flightNo} · ${order.departureCity || "—"} → ${order.arrivalCity || "—"}` : "—",
    }),
    [order]
  )

  useEffect(() => {
    if (!open) return

    const nextUserId = mode === "edit" && order ? String(order.userId) : ""
    const nextFlightId = mode === "edit" && order ? String(order.flightId) : ""
    const nextPassengerIds = mode === "edit" && order ? order.passengers.map((item) => item.passengerId) : []
    const nextSeatAssignments = mode === "edit" && order
      ? Object.fromEntries(order.passengers.map((item) => [item.passengerId, String(item.seatId)]))
      : {}

    setUserId(nextUserId)
    setFlightId(nextFlightId)
    setSelectedPassengerIds(nextPassengerIds)
    setSeatAssignments(nextSeatAssignments)
    setErrors({ seats: {} })
  }, [mode, open, order])

  const togglePassenger = (passengerId: number, checked: boolean) => {
    setSelectedPassengerIds((current) => {
      if (checked) {
        return current.includes(passengerId) ? current : [...current, passengerId]
      }
      const next = current.filter((id) => id !== passengerId)
      setSeatAssignments((assignments) => {
        const { [passengerId]: _removed, ...rest } = assignments
        return rest
      })
      return next
    })
    setErrors((current) => ({
      ...current,
      items: undefined,
      seats: { ...current.seats, [passengerId]: "" },
    }))
  }

  const handleSubmit = async () => {
    setErrors({ seats: {} })
    orderFormSchema.safeParse({
      userId,
      flightId,
      items: selectedPassengerIds.map((passengerId) => ({
        passengerId,
        seatId: seatAssignments[passengerId],
      })),
    })
  }

  const passengerCount = selectedPassengerIds.length

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{mode === "create" ? "新增订单" : "编辑订单"}</DialogTitle>
          <DialogDescription>
            {mode === "create"
              ? "按用户、乘机人、航班和座位创建后台订单。"
              : "编辑当前订单的乘机人与座位分配。"}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-5">
          <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            {featureDisabledMessage}
          </div>

          {mode === "create" ? (
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-1.5">
                <Label>用户</Label>
                <Select
                  disabled
                  value={userId}
                  onValueChange={(value) => {
                    setUserId(value ?? "")
                    setSelectedPassengerIds([])
                    setSeatAssignments({})
                    setErrors({ seats: {} })
                  }}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="请选择用户" />
                  </SelectTrigger>
                  <SelectContent>
                    {users.map((user) => (
                      <SelectItem key={user.id} value={String(user.id)}>
                        {getDisplayName(user)} · {user.email}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.userId && <p className="text-xs text-destructive">{errors.userId}</p>}
              </div>

              <div className="space-y-1.5">
                <Label>航班</Label>
                <Select
                  disabled
                  value={flightId}
                  onValueChange={(value) => {
                    setFlightId(value ?? "")
                    setSeatAssignments({})
                    setErrors((current) => ({ ...current, flightId: undefined, seats: {} }))
                  }}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="请选择航班" />
                  </SelectTrigger>
                  <SelectContent>
                    {flights.map((flight) => (
                      <SelectItem key={flight.id} value={String(flight.id)}>
                        {formatFlightLabel(flight)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.flightId && <p className="text-xs text-destructive">{errors.flightId}</p>}
              </div>
            </div>
          ) : (
            <div className="grid gap-4 rounded-xl border bg-muted/30 p-4 text-sm md:grid-cols-2">
              <div>
                <p className="text-xs text-muted-foreground">用户</p>
                <p className="font-medium">{editingSummary.userName}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">航班</p>
                <p className="font-medium">{editingSummary.flightLabel}</p>
                <p className="text-xs text-muted-foreground">{formatDateTime(order?.departureTime)}</p>
              </div>
            </div>
          )}

          {selectedFlight && (
            <div className="rounded-xl border bg-muted/30 p-4 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="font-medium">{selectedFlight.flightNo}</p>
                  <p className="text-muted-foreground">
                    {selectedFlight.departureCity} → {selectedFlight.arrivalCity}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-xs text-muted-foreground">基础票价</p>
                  <FlightPriceTag price={selectedFlight.basePrice} />
                </div>
              </div>
              <p className="mt-2 text-xs text-muted-foreground">
                起飞时间 {formatDateTime(selectedFlight.departureTime)}
              </p>
            </div>
          )}

          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="font-medium">乘机人与座位</h3>
                <p className="text-sm text-muted-foreground">
                  先选择乘机人，再为每位乘机人分配唯一座位。
                </p>
              </div>
            </div>

            {mode === "edit" && order ? (
              <div className="space-y-4">
                {order.passengers.map((passenger) => {
                  const checked = selectedPassengerIds.includes(passenger.passengerId)

                  return (
                    <div key={passenger.passengerId} className="rounded-xl border p-4">
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <label className="flex flex-1 items-start gap-3">
                          <Checkbox
                            disabled
                            checked={checked}
                            onCheckedChange={(value) => togglePassenger(passenger.passengerId, Boolean(value))}
                          />
                          <div className="space-y-1 text-sm">
                            <p className="font-medium">{passenger.passengerName}</p>
                            <p className="text-muted-foreground">
                              {PASSENGER_TYPE_LABEL[passenger.passengerType]} · 座位 {passenger.seatNo}
                            </p>
                          </div>
                        </label>

                        <div className="w-full max-w-xs space-y-1.5">
                          <Label>座位</Label>
                          <Select disabled value={seatAssignments[passenger.passengerId] || ""}>
                            <SelectTrigger className="w-full">
                              <SelectValue placeholder="当前暂不可分配座位" />
                            </SelectTrigger>
                            <SelectContent />
                          </Select>
                          {errors.seats[passenger.passengerId] && (
                            <p className="text-xs text-destructive">{errors.seats[passenger.passengerId]}</p>
                          )}
                        </div>
                      </div>
                    </div>
                  )
                })}
              </div>
            ) : (
              <div className="rounded-xl border border-dashed p-10 text-center text-sm text-muted-foreground">
                当前功能暂不可用，待后端接口补齐后可继续配置乘机人与座位。
              </div>
            )}

            {errors.items && <p className="text-xs text-destructive">{errors.items}</p>}
          </div>

          <div className="rounded-xl border bg-muted/30 p-4 text-sm">
            <div className="flex items-center justify-between">
              <span>已选择乘机人</span>
              <span className="font-medium">{passengerCount} 人</span>
            </div>
            <div className="mt-2 flex items-center justify-between">
              <span>已分配座位</span>
              <span className="font-medium">{Object.keys(seatAssignments).length} 个</span>
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Tooltip>
            <TooltipTrigger render={<span className="inline-flex" tabIndex={0} />}>
              <Button onClick={handleSubmit} disabled>
                {mode === "create" ? "创建订单" : "保存修改"}
              </Button>
            </TooltipTrigger>
            <TooltipContent>{featureDisabledMessage}</TooltipContent>
          </Tooltip>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

"use client"

import { useEffect, useMemo, useState } from "react"
import { z } from "zod"
import { Loader2 } from "lucide-react"
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
import { formatDateFull, formatTime } from "@/lib/date-utils"
import { PASSENGER_TYPE_LABEL } from "@/lib/passenger-utils"
import type { ApiError } from "@/lib/request"
import * as adminApi from "@/services/adminApi"
import type { UserAdminVO } from "@/types/admin"
import type { FlightSeatVO, FlightVO } from "@/types/flight"
import type { CreateAdminOrderDTO, OrderVO, UpdateAdminOrderDTO } from "@/types/order"
import type { PassengerVO } from "@/types/passenger"

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
  users: UserAdminVO[]
  flights: FlightVO[]
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
  users,
  flights,
  onSuccess,
}: OrderFormDialogProps) {
  const [userId, setUserId] = useState("")
  const [flightId, setFlightId] = useState("")
  const [selectedPassengerIds, setSelectedPassengerIds] = useState<number[]>([])
  const [seatAssignments, setSeatAssignments] = useState<Record<number, string>>({})
  const [passengers, setPassengers] = useState<PassengerVO[]>([])
  const [seats, setSeats] = useState<FlightSeatVO[]>([])
  const [loadingPassengers, setLoadingPassengers] = useState(false)
  const [loadingSeats, setLoadingSeats] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [errors, setErrors] = useState<FormErrors>({ seats: {} })
  const [isSubmitting, setIsSubmitting] = useState(false)

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
    setPassengers([])
    setSeats([])
    setLoadError(null)
    setSubmitError(null)
    setErrors({ seats: {} })
    setIsSubmitting(false)
  }, [mode, open, order])

  useEffect(() => {
    if (!open || !userId) {
      if (mode === "create") {
        setPassengers([])
      }
      return
    }

    setLoadingPassengers(true)
    setLoadError(null)

    adminApi
      .getPassengersByUser(Number(userId))
      .then((data) => {
        setPassengers(data)
      })
      .catch((error: ApiError) => {
        setPassengers([])
        setLoadError(error.message || "加载乘机人失败")
      })
      .finally(() => setLoadingPassengers(false))
  }, [mode, open, userId])

  useEffect(() => {
    if (!open || !flightId) {
      if (mode === "create") {
        setSeats([])
      }
      return
    }

    setLoadingSeats(true)
    setLoadError(null)

    adminApi
      .getAdminFlightSeats(Number(flightId))
      .then((data) => {
        setSeats(data)
      })
      .catch((error: ApiError) => {
        setSeats([])
        setLoadError(error.message || "加载座位信息失败")
      })
      .finally(() => setLoadingSeats(false))
  }, [flightId, mode, open])

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

  const getSeatOptions = (passengerId: number) => {
    const currentSeatId = Number(seatAssignments[passengerId] || "0")
    const occupiedByOthers = new Set(
      Object.entries(seatAssignments)
        .filter(([mappedPassengerId, seatId]) => Number(mappedPassengerId) !== passengerId && Boolean(seatId))
        .map(([, seatId]) => Number(seatId))
    )

    return seats.filter((seat) => {
      if (seat.status !== "AVAILABLE" && seat.id !== currentSeatId) return false
      if (occupiedByOthers.has(seat.id) && seat.id !== currentSeatId) return false
      return true
    })
  }

  const handleSubmit = async () => {
    const payload = {
      userId,
      flightId,
      items: selectedPassengerIds.map((passengerId) => ({
        passengerId,
        seatId: seatAssignments[passengerId],
      })),
    }

    const parsed = orderFormSchema.safeParse(payload)
    if (!parsed.success) {
      const nextErrors: FormErrors = { seats: {} }

      for (const issue of parsed.error.issues) {
        if (issue.path[0] === "userId" && !nextErrors.userId) {
          nextErrors.userId = issue.message
          continue
        }
        if (issue.path[0] === "flightId" && !nextErrors.flightId) {
          nextErrors.flightId = issue.message
          continue
        }
        if (issue.path[0] === "items" && issue.path.length === 1 && !nextErrors.items) {
          nextErrors.items = issue.message
          continue
        }
        if (issue.path[0] === "items" && typeof issue.path[1] === "number") {
          const passengerId = payload.items[issue.path[1]]?.passengerId
          if (passengerId) {
            nextErrors.seats[passengerId] = issue.message
          }
        }
      }

      setErrors(nextErrors)
      return
    }

    setErrors({ seats: {} })
    setSubmitError(null)
    setIsSubmitting(true)
    try {
      if (mode === "create") {
        const dto: CreateAdminOrderDTO = parsed.data
        await adminApi.createAdminOrder(dto)
      } else if (order) {
        const dto: UpdateAdminOrderDTO = { items: parsed.data.items }
        await adminApi.updateAdminOrder(order.id, dto)
      }
      onOpenChange(false)
      await onSuccess?.()
    } catch (error) {
      setSubmitError((error as ApiError).message || "保存订单失败")
    } finally {
      setIsSubmitting(false)
    }
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
          {mode === "create" ? (
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-1.5">
                <Label>用户</Label>
                <Select
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
                        {user.realName} · {user.email}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.userId && <p className="text-xs text-destructive">{errors.userId}</p>}
              </div>

              <div className="space-y-1.5">
                <Label>航班</Label>
                <Select
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

          {loadError && (
            <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
              {loadError}
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
              {(loadingPassengers || loadingSeats) && (
                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  加载中
                </div>
              )}
            </div>

            {passengers.length === 0 ? (
              <div className="rounded-xl border border-dashed p-10 text-center text-sm text-muted-foreground">
                {userId ? "当前用户暂无可选乘机人" : "请先选择用户"}
              </div>
            ) : (
              <div className="space-y-4">
                {passengers.map((passenger) => {
                  const checked = selectedPassengerIds.includes(passenger.id)
                  const seatOptions = getSeatOptions(passenger.id)

                  return (
                    <div key={passenger.id} className="rounded-xl border p-4">
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <label className="flex flex-1 items-start gap-3">
                          <Checkbox
                            checked={checked}
                            onCheckedChange={(value) => togglePassenger(passenger.id, Boolean(value))}
                          />
                          <div className="space-y-1 text-sm">
                            <p className="font-medium">{passenger.name}</p>
                            <p className="text-muted-foreground">
                              {PASSENGER_TYPE_LABEL[passenger.passengerType]} · {passenger.idCardNo}
                            </p>
                            <p className="text-muted-foreground">{passenger.phone}</p>
                          </div>
                        </label>

                        <div className="w-full max-w-xs space-y-1.5">
                          <Label>座位</Label>
                          <Select
                            value={seatAssignments[passenger.id] || ""}
                            onValueChange={(value) => {
                              if (!checked) {
                                setSelectedPassengerIds((current) =>
                                  current.includes(passenger.id) ? current : [...current, passenger.id]
                                )
                              }
                              setSeatAssignments((current) => ({
                                ...current,
                                [passenger.id]: value ?? "",
                              }))
                              setErrors((current) => ({
                                ...current,
                                items: undefined,
                                seats: { ...current.seats, [passenger.id]: "" },
                              }))
                            }}
                            disabled={!flightId || seatOptions.length === 0}
                          >
                            <SelectTrigger className="w-full">
                              <SelectValue placeholder={flightId ? "请选择座位" : "请先选择航班"} />
                            </SelectTrigger>
                            <SelectContent>
                              {seatOptions.map((seat) => (
                                <SelectItem key={seat.id} value={String(seat.id)}>
                                  {seat.seatNo} · ¥{seat.price.toLocaleString()}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                          {errors.seats[passenger.id] && (
                            <p className="text-xs text-destructive">{errors.seats[passenger.id]}</p>
                          )}
                        </div>
                      </div>
                    </div>
                  )
                })}
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

          {submitError && (
            <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
              {submitError}
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
            取消
          </Button>
          <Button onClick={handleSubmit} disabled={isSubmitting}>
            {isSubmitting && <Loader2 className="h-4 w-4 animate-spin" />}
            {mode === "create" ? "创建订单" : "保存修改"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

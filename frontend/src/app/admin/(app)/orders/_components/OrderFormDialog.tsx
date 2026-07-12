"use client"

import { useEffect, useMemo, useState } from "react"
import { z } from "zod"
import { Loader2 } from "lucide-react"
import { FlightPriceTag } from "@/components/common/FlightPriceTag"
import { Button } from "@/components/ui/button"
import { Combobox } from "@/components/ui/combobox"
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
import { useDebounce } from "@/hooks/useDebounce"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { formatDateFull, formatTime } from "@/lib/date-utils"
import { getErrorMessage } from "@/lib/error-codes"
import { PASSENGER_TYPE_LABEL } from "@/lib/passenger-utils"
import { getDisplayName } from "@/lib/user-utils"
import * as adminApi from "@/services/adminApi"
import type { UserAdminVO } from "@/types/admin"
import type { FlightSeatVO, FlightVO } from "@/types/flight"
import type { PassengerVO } from "@/types/passenger"
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

function buildSelectedUserFromOrder(order: OrderVO): UserAdminVO {
  return {
    id: order.userId,
    email: order.userEmail,
    nickname: order.userNickname || undefined,
    role: "USER",
    status: "NORMAL",
    emailVerified: true,
    phoneVerified: false,
    createdAt: order.createdAt,
  }
}

function buildSelectedFlightFromOrder(order: OrderVO): FlightVO {
  return {
    id: order.flightId,
    flightNo: order.flightNo,
    airlineId: 0,
    airlineCode: "",
    airlineName: order.airlineName || "",
    departureAirportId: 0,
    departureAirportCode: "",
    departureAirportName: "",
    departureCity: order.departureCity || "—",
    arrivalAirportId: 0,
    arrivalAirportCode: "",
    arrivalAirportName: "",
    arrivalCity: order.arrivalCity || "—",
    departureTime: order.departureTime || order.createdAt,
    arrivalTime: order.arrivalTime || order.departureTime || order.createdAt,
    durationMinutes: 0,
    basePrice: order.ticketAmount,
    remainingSeats: 0,
    totalSeats: 0,
    status: "ON_TIME",
    publishStatus: "PUBLISHED",
    directFlag: 1,
    baggageAllowance: "",
    punctualityRate: 0,
  }
}

export function OrderFormDialog({
  open,
  onOpenChange,
  mode,
  order,
  onSuccess,
}: OrderFormDialogProps) {
  const editDisabledMessage = "编辑接口 (PUT) 后端尚未实现"
  const [userId, setUserId] = useState("")
  const [flightId, setFlightId] = useState("")
  const [selectedUser, setSelectedUser] = useState<UserAdminVO | null>(null)
  const [selectedFlight, setSelectedFlight] = useState<FlightVO | null>(null)
  const [userOptions, setUserOptions] = useState<UserAdminVO[]>([])
  const [flightOptions, setFlightOptions] = useState<FlightVO[]>([])
  const [userSearch, setUserSearch] = useState("")
  const [flightSearch, setFlightSearch] = useState("")
  const [selectedPassengerIds, setSelectedPassengerIds] = useState<number[]>([])
  const [seatAssignments, setSeatAssignments] = useState<Record<number, string>>({})
  const [errors, setErrors] = useState<FormErrors>({ seats: {} })
  const [passengers, setPassengers] = useState<PassengerVO[]>([])
  const [seats, setSeats] = useState<FlightSeatVO[]>([])
  const [loadError, setLoadError] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isUsersLoading, setIsUsersLoading] = useState(false)
  const [isFlightsLoading, setIsFlightsLoading] = useState(false)
  const [isPassengersLoading, setIsPassengersLoading] = useState(false)
  const [isSeatsLoading, setIsSeatsLoading] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const debouncedUserSearch = useDebounce(userSearch.trim(), 300)
  const debouncedFlightSearch = useDebounce(flightSearch.trim(), 300)

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
    setSelectedUser(mode === "edit" && order ? buildSelectedUserFromOrder(order) : null)
    setSelectedFlight(mode === "edit" && order ? buildSelectedFlightFromOrder(order) : null)
    setUserOptions([])
    setFlightOptions([])
    setUserSearch("")
    setFlightSearch("")
    setSelectedPassengerIds(nextPassengerIds)
    setSeatAssignments(nextSeatAssignments)
    setErrors({ seats: {} })
    setPassengers([])
    setSeats([])
    setLoadError(null)
    setSubmitError(null)
    setIsUsersLoading(false)
    setIsFlightsLoading(false)
    setIsPassengersLoading(false)
    setIsSeatsLoading(false)
    setIsSubmitting(false)
  }, [mode, open, order])

  useEffect(() => {
    if (!open || mode !== "create") return

    if (!debouncedUserSearch) {
      setUserOptions([])
      setIsUsersLoading(false)
      return
    }

    let cancelled = false

    const loadUsers = async () => {
      setIsUsersLoading(true)
      setLoadError(null)
      try {
        // TODO(#132): 后端暂不支持 keyword 搜索，目前拉取 50 条后由 Combobox 在客户端做二次过滤
        const data = await adminApi.getUsers({
          keyword: debouncedUserSearch,
          page: 1,
          size: 50,
          role: "USER",
        })
        if (!cancelled) {
          setUserOptions(data.records)
        }
      } catch (err) {
        if (!cancelled) {
          setUserOptions([])
          setLoadError(getErrorMessage(err, "搜索用户失败"))
        }
      } finally {
        if (!cancelled) {
          setIsUsersLoading(false)
        }
      }
    }

    void loadUsers()

    return () => {
      cancelled = true
    }
  }, [debouncedUserSearch, mode, open])

  useEffect(() => {
    if (!open || mode !== "create") return

    if (!debouncedFlightSearch) {
      setFlightOptions([])
      setIsFlightsLoading(false)
      return
    }

    let cancelled = false

    const loadFlights = async () => {
      setIsFlightsLoading(true)
      setLoadError(null)
      try {
        const data = await adminApi.getFlights({
          keyword: debouncedFlightSearch,
          page: 1,
          size: 50,
          publishStatus: "PUBLISHED",
        })
        if (!cancelled) {
          setFlightOptions(data.records)
        }
      } catch (err) {
        if (!cancelled) {
          setFlightOptions([])
          setLoadError(getErrorMessage(err, "搜索航班失败"))
        }
      } finally {
        if (!cancelled) {
          setIsFlightsLoading(false)
        }
      }
    }

    void loadFlights()

    return () => {
      cancelled = true
    }
  }, [debouncedFlightSearch, mode, open])

  useEffect(() => {
    if (!open || mode !== "create") return

    if (!userId) {
      setPassengers([])
      return
    }

    let cancelled = false

    const loadPassengers = async () => {
      setIsPassengersLoading(true)
      setLoadError(null)
      try {
        const data = await adminApi.getPassengersByUser(Number(userId))
        if (!cancelled) {
          setPassengers(data)
        }
      } catch (err) {
        if (!cancelled) {
          setPassengers([])
          setLoadError(getErrorMessage(err, "加载乘机人失败"))
        }
      } finally {
        if (!cancelled) {
          setIsPassengersLoading(false)
        }
      }
    }

    void loadPassengers()

    return () => {
      cancelled = true
    }
  }, [mode, open, userId])

  useEffect(() => {
    if (!open || mode !== "create") return

    if (!flightId) {
      setSeats([])
      return
    }

    let cancelled = false

    const loadSeats = async () => {
      setIsSeatsLoading(true)
      setLoadError(null)
      try {
        const data = await adminApi.getAdminFlightSeats(Number(flightId))
        if (!cancelled) {
          setSeats(data)
        }
      } catch (err) {
        if (!cancelled) {
          setSeats([])
          setLoadError(getErrorMessage(err, "加载航班座位失败"))
        }
      } finally {
        if (!cancelled) {
          setIsSeatsLoading(false)
        }
      }
    }

    void loadSeats()

    return () => {
      cancelled = true
    }
  }, [flightId, mode, open])

  const togglePassenger = (passengerId: number, checked: boolean) => {
    setSelectedPassengerIds((current) => {
      if (checked) {
        return current.includes(passengerId) ? current : [...current, passengerId]
      }
      const next = current.filter((id) => id !== passengerId)
      setSeatAssignments((assignments) => {
        const nextAssignments = { ...assignments }
        delete nextAssignments[passengerId]
        return nextAssignments
      })
      return next
    })
    setErrors((current) => ({
      ...current,
      items: undefined,
      seats: { ...current.seats, [passengerId]: "" },
    }))
  }

  const getSeatChoices = (passengerId: number) => {
    const selectedByOthers = new Set(
      Object.entries(seatAssignments)
        .filter(([id, seatId]) => Number(id) !== passengerId && seatId)
        .map(([, seatId]) => Number(seatId))
    )

    return seats.filter((seat) => {
      if (seat.status !== "AVAILABLE" && seat.id !== Number(seatAssignments[passengerId])) {
        return false
      }
      return !selectedByOthers.has(seat.id) || seat.id === Number(seatAssignments[passengerId])
    })
  }

  const handleSubmit = async () => {
    if (mode === "edit" || isSubmitting) return

    setErrors({ seats: {} })
    setSubmitError(null)

    const parsed = orderFormSchema.safeParse({
      userId,
      flightId,
      items: selectedPassengerIds.map((passengerId) => ({
        passengerId,
        seatId: seatAssignments[passengerId],
      })),
    })

    if (!parsed.success) {
      const nextErrors: FormErrors = { seats: {} }
      for (const issue of parsed.error.issues) {
        if (issue.path[0] === "userId" && !nextErrors.userId) {
          nextErrors.userId = issue.message
        } else if (issue.path[0] === "flightId" && !nextErrors.flightId) {
          nextErrors.flightId = issue.message
        } else if (issue.path[0] === "items" && typeof issue.path[1] !== "number" && !nextErrors.items) {
          nextErrors.items = issue.message
        } else if (issue.path[0] === "items" && typeof issue.path[1] === "number") {
          const passengerId = selectedPassengerIds[issue.path[1]]
          if (passengerId && !nextErrors.seats[passengerId]) {
            nextErrors.seats[passengerId] = issue.message
          }
        }
      }
      setErrors(nextErrors)
      return
    }

    setIsSubmitting(true)
    try {
      await adminApi.createAdminOrder({
        userId: parsed.data.userId,
        flightId: parsed.data.flightId,
        items: parsed.data.items.map((item) => ({
          passengerId: item.passengerId,
          seatId: item.seatId,
        })),
      })
      onOpenChange(false)
      await onSuccess?.()
    } catch (err) {
      setSubmitError(getErrorMessage(err, "创建订单失败"))
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
              : "编辑当前订单的乘机人与座位分配。当前仅支持只读查看。"}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-5">
          {mode === "create" ? (
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-1.5">
                <Label>用户</Label>
                <Combobox
                  options={userOptions}
                  value={userId}
                  selectedOption={selectedUser}
                  onValueChange={(value) => {
                    const nextUser = userOptions.find((user) => String(user.id) === value) ?? null
                    setUserId(value ?? "")
                    setSelectedUser(nextUser)
                    setPassengers([])
                    setSelectedPassengerIds([])
                    setSeatAssignments({})
                    setLoadError(null)
                    setSubmitError(null)
                    setErrors({ seats: {} })
                  }}
                  onSearch={setUserSearch}
                  placeholder="请选择用户"
                  searchPlaceholder="搜索昵称/邮箱..."
                  emptyMessage={userSearch.trim() ? "暂无匹配用户" : "请输入关键词搜索用户"}
                  getDisplayValue={(user) => `${getDisplayName(user)} · ${user.email}`}
                  getSearchFields={(user) => [user.email, user.nickname ?? ""]}
                  loading={isUsersLoading}
                />
                {errors.userId && <p className="text-xs text-destructive">{errors.userId}</p>}
              </div>

              <div className="space-y-1.5">
                <Label>航班</Label>
                <Combobox
                  options={flightOptions}
                  value={flightId}
                  selectedOption={selectedFlight}
                  onValueChange={(value) => {
                    const nextFlight = flightOptions.find((flight) => String(flight.id) === value) ?? null
                    setFlightId(value ?? "")
                    setSelectedFlight(nextFlight)
                    setSeats([])
                    setSeatAssignments({})
                    setLoadError(null)
                    setSubmitError(null)
                    setErrors((current) => ({ ...current, flightId: undefined, seats: {} }))
                  }}
                  onSearch={setFlightSearch}
                  placeholder="请选择航班"
                  searchPlaceholder="搜索航班号/城市..."
                  emptyMessage={flightSearch.trim() ? "暂无匹配航班" : "请输入关键词搜索航班"}
                  getDisplayValue={formatFlightLabel}
                  getSearchFields={(flight) => [flight.flightNo, flight.departureCity, flight.arrivalCity]}
                  loading={isFlightsLoading}
                />
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

          {mode === "create" && selectedFlight && (
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
              <div className="space-y-4">
                {!userId ? (
                  <div className="rounded-xl border border-dashed p-10 text-center text-sm text-muted-foreground">
                    请选择用户后加载乘机人
                  </div>
                ) : isPassengersLoading ? (
                  <div className="flex items-center justify-center rounded-xl border border-dashed p-10 text-sm text-muted-foreground">
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    正在加载乘机人...
                  </div>
                ) : passengers.length === 0 ? (
                  <div className="rounded-xl border border-dashed p-10 text-center text-sm text-muted-foreground">
                    该用户暂无可用乘机人
                  </div>
                ) : (
                  <div className="space-y-4">
                    {passengers.map((passenger) => {
                      const checked = selectedPassengerIds.includes(passenger.id)

                      return (
                        <div key={passenger.id} className="rounded-xl border p-4">
                          <div className="flex flex-wrap items-start justify-between gap-3">
                            <label className="flex flex-1 items-start gap-3">
                              <Checkbox
                                checked={checked}
                                onCheckedChange={(value) => togglePassenger(passenger.id, value === true)}
                              />
                              <div className="space-y-1 text-sm">
                                <p className="font-medium">{passenger.name}</p>
                                <p className="text-muted-foreground">
                                  {PASSENGER_TYPE_LABEL[passenger.passengerType]} · {passenger.idCardNo}
                                </p>
                              </div>
                            </label>

                            <div className="w-full max-w-xs space-y-1.5">
                              <Label>座位</Label>
                              <Select
                                value={seatAssignments[passenger.id] || ""}
                                onValueChange={(value) => {
                                  setSeatAssignments((current) => ({
                                    ...current,
                                    [passenger.id]: value ?? "",
                                  }))
                                  setErrors((current) => ({
                                    ...current,
                                    seats: { ...current.seats, [passenger.id]: "" },
                                  }))
                                }}
                                disabled={!checked || !flightId || isSeatsLoading}
                              >
                                <SelectTrigger className="w-full">
                                  <SelectValue
                                    placeholder={
                                      !flightId
                                        ? "请先选择航班"
                                        : isSeatsLoading
                                          ? "正在加载座位..."
                                          : "请选择座位"
                                    }
                                  />
                                </SelectTrigger>
                                <SelectContent>
                                  {getSeatChoices(passenger.id).map((seat) => (
                                    <SelectItem key={seat.id} value={String(seat.id)}>
                                      {seat.seatNo} · {seat.cabinClass} · ¥{seat.price.toLocaleString()}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                              {errors.seats[passenger.id] ? (
                                <p className="text-xs text-destructive">{errors.seats[passenger.id]}</p>
                              ) : null}
                            </div>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                )}
              </div>
            )}

            {errors.items && <p className="text-xs text-destructive">{errors.items}</p>}
          </div>

          {loadError ? (
            <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
              {loadError}
            </div>
          ) : null}

          {submitError ? (
            <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
              {submitError}
            </div>
          ) : null}

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
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
            取消
          </Button>
          {mode === "edit" ? (
            <Tooltip>
              <TooltipTrigger render={<span className="inline-flex" tabIndex={0} />}>
                <Button disabled>保存修改</Button>
              </TooltipTrigger>
              <TooltipContent>{editDisabledMessage}</TooltipContent>
            </Tooltip>
          ) : (
            <Button onClick={handleSubmit} disabled={isSubmitting}>
              {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
              {isSubmitting ? "创建中..." : "创建订单"}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

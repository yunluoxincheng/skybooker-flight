"use client"

import { useEffect, useMemo, useState } from "react"
import { z } from "zod"
import { ArrowRightLeft, Loader2 } from "lucide-react"
import { FlightPriceTag } from "@/components/common/FlightPriceTag"
import { Button } from "@/components/ui/button"
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
import { Separator } from "@/components/ui/separator"
import { formatDateFull, formatTime } from "@/lib/date-utils"
import { PASSENGER_TYPE_LABEL } from "@/lib/passenger-utils"
import type { ApiError } from "@/lib/request"
import * as adminApi from "@/services/adminApi"
import type { ChangeOptionVO, OrderVO } from "@/types/order"
import type { FlightSeatVO } from "@/types/flight"

const changeSeatMappingSchema = z.object({
  newFlightId: z.coerce.number().min(1, "请选择改签航班"),
  seatMappings: z.array(
    z.object({
      passengerId: z.number().min(1),
      newSeatId: z.coerce.number().min(1, "请选择新座位"),
    })
  ).min(1, "请完成座位映射"),
})

interface ChangeOrderDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  order: OrderVO | null
  onSuccess?: () => Promise<void> | void
}

function formatDateTime(iso?: string | null) {
  if (!iso) return "—"
  return `${formatDateFull(iso)} ${formatTime(iso)}`
}

export function ChangeOrderDialog({
  open,
  onOpenChange,
  order,
  onSuccess,
}: ChangeOrderDialogProps) {
  const [step, setStep] = useState<1 | 2>(1)
  const [options, setOptions] = useState<ChangeOptionVO[]>([])
  const [selectedFlightId, setSelectedFlightId] = useState("")
  const [seats, setSeats] = useState<FlightSeatVO[]>([])
  const [seatMappings, setSeatMappings] = useState<Record<number, string>>({})
  const [loadingOptions, setLoadingOptions] = useState(false)
  const [loadingSeats, setLoadingSeats] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [flightError, setFlightError] = useState<string | null>(null)
  const [seatErrors, setSeatErrors] = useState<Record<number, string>>({})
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (!open || !order) return

    setStep(1)
    setSelectedFlightId("")
    setSeats([])
    setSeatMappings({})
    setSubmitError(null)
    setFlightError(null)
    setSeatErrors({})
    setIsSubmitting(false)
    setLoadingOptions(true)

    adminApi
      .getAdminChangeOptions(order.id)
      .then((data) => {
        setOptions(data)
      })
      .catch((error: ApiError) => {
        setOptions([])
        setSubmitError(error.message || "加载改签选项失败")
      })
      .finally(() => setLoadingOptions(false))
  }, [open, order])

  const selectedOption = useMemo(
    () => options.find((option) => option.flightId === Number(selectedFlightId)) ?? null,
    [options, selectedFlightId]
  )

  const estimatedPriceDiff = useMemo(() => {
    if (!order || !selectedOption) return 0
    return selectedOption.basePrice * order.passengers.length - order.ticketAmount
  }, [order, selectedOption])

  const selectedSeatTotal = useMemo(() => {
    return Object.values(seatMappings).reduce((sum, seatId) => {
      const seat = seats.find((item) => item.id === Number(seatId))
      return sum + (seat?.price ?? 0)
    }, 0)
  }, [seatMappings, seats])

  const loadSeats = async () => {
    if (!selectedFlightId || !order) {
      setFlightError("请选择改签航班")
      return
    }

    setFlightError(null)
    setSubmitError(null)
    setLoadingSeats(true)
    try {
      const data = await adminApi.getAdminFlightSeats(Number(selectedFlightId))
      setSeats(data)
      setSeatMappings({})
      setSeatErrors({})
      setStep(2)
    } catch (error) {
      setSubmitError((error as ApiError).message || "加载新航班座位失败")
    } finally {
      setLoadingSeats(false)
    }
  }

  const getSeatOptions = (passengerId: number) => {
    const currentSeatId = Number(seatMappings[passengerId] || "0")
    const occupiedByOthers = new Set(
      Object.entries(seatMappings)
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
    if (!order) return

    const payload = {
      newFlightId: selectedFlightId,
      seatMappings: order.passengers.map((passenger) => ({
        passengerId: passenger.passengerId,
        newSeatId: seatMappings[passenger.passengerId],
      })),
    }

    const parsed = changeSeatMappingSchema.safeParse(payload)
    if (!parsed.success) {
      const nextSeatErrors: Record<number, string> = {}
      let nextFlightError: string | null = null

      for (const issue of parsed.error.issues) {
        if (issue.path[0] === "newFlightId" && !nextFlightError) {
          nextFlightError = issue.message
          continue
        }

        if (issue.path[0] === "seatMappings" && typeof issue.path[1] === "number") {
          const passenger = order.passengers[issue.path[1]]
          if (passenger) {
            nextSeatErrors[passenger.passengerId] = issue.message
          }
        }
      }

      setFlightError(nextFlightError)
      setSeatErrors(nextSeatErrors)
      return
    }

    setSubmitError(null)
    setSeatErrors({})
    setIsSubmitting(true)
    try {
      await adminApi.changeAdminOrder(order.id, parsed.data)
      onOpenChange(false)
      await onSuccess?.()
    } catch (error) {
      setSubmitError((error as ApiError).message || "改签失败")
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>改签订单</DialogTitle>
          <DialogDescription>
            第一步选择可改签航班，第二步为每位乘机人重新分配座位。
          </DialogDescription>
        </DialogHeader>

        {!order ? null : (
          <div className="space-y-4">
            <div className="rounded-xl border bg-muted/30 p-4 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="font-medium">{order.orderNo}</p>
                  <p className="text-muted-foreground">
                    当前航班 {order.flightNo} · {formatDateTime(order.departureTime)}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-xs text-muted-foreground">当前订单票价</p>
                  <FlightPriceTag price={order.ticketAmount} />
                </div>
              </div>
            </div>

            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <div className={`rounded-full px-2 py-1 ${step === 1 ? "bg-primary/10 text-primary" : "bg-muted"}`}>
                1. 选择航班
              </div>
              <div className={`rounded-full px-2 py-1 ${step === 2 ? "bg-primary/10 text-primary" : "bg-muted"}`}>
                2. 座位映射
              </div>
            </div>

            {step === 1 ? (
              <div className="space-y-4">
                {loadingOptions ? (
                  <div className="flex min-h-[220px] items-center justify-center">
                    <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                  </div>
                ) : options.length === 0 ? (
                  <div className="rounded-xl border border-dashed p-10 text-center text-sm text-muted-foreground">
                    暂无可改签航班
                  </div>
                ) : (
                  <>
                    <div className="space-y-1.5">
                      <Label>改签到航班</Label>
                      <Select
                        value={selectedFlightId}
                        onValueChange={(value) => {
                          setSelectedFlightId(value ?? "")
                          setFlightError(null)
                        }}
                      >
                        <SelectTrigger className="w-full">
                          <SelectValue placeholder="请选择可改签航班" />
                        </SelectTrigger>
                        <SelectContent>
                          {options.map((option) => (
                            <SelectItem key={option.flightId} value={String(option.flightId)}>
                              {option.flightNo} · {formatDateTime(option.departureTime)}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      {flightError && <p className="text-xs text-destructive">{flightError}</p>}
                    </div>

                    <div className="grid gap-3">
                      {options.map((option) => {
                        const diff = option.basePrice * order.passengers.length - order.ticketAmount
                        const active = option.flightId === Number(selectedFlightId)

                        return (
                          <button
                            key={option.flightId}
                            type="button"
                            onClick={() => {
                              setSelectedFlightId(String(option.flightId))
                              setFlightError(null)
                            }}
                            className={`rounded-xl border p-4 text-left transition-colors ${active ? "border-primary bg-primary/5" : "hover:bg-muted/40"}`}
                          >
                            <div className="flex flex-wrap items-center justify-between gap-3">
                              <div className="space-y-1">
                                <div className="flex items-center gap-2">
                                  <span className="font-medium">{option.flightNo}</span>
                                  <ArrowRightLeft className="h-4 w-4 text-muted-foreground" />
                                  <span className="text-muted-foreground">{order.flightNo}</span>
                                </div>
                                <p className="text-sm text-muted-foreground">
                                  {formatDateTime(option.departureTime)} → {formatDateTime(option.arrivalTime)}
                                </p>
                              </div>
                              <div className="text-right text-sm">
                                <p>余座 {option.remainingSeats}</p>
                                <p className={diff >= 0 ? "text-amber-700" : "text-emerald-700"}>
                                  预估差价 {diff >= 0 ? "+" : ""}¥{Math.abs(diff).toLocaleString()}
                                </p>
                              </div>
                            </div>
                          </button>
                        )
                      })}
                    </div>
                  </>
                )}
              </div>
            ) : (
              <div className="space-y-4">
                <div className="grid gap-3 rounded-xl border bg-muted/30 p-4 text-sm md:grid-cols-3">
                  <div>
                    <p className="text-xs text-muted-foreground">目标航班</p>
                    <p className="font-medium">{selectedOption?.flightNo || "—"}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">预估基础差价</p>
                    <p className={estimatedPriceDiff >= 0 ? "text-amber-700" : "text-emerald-700"}>
                      {estimatedPriceDiff >= 0 ? "+" : ""}¥{Math.abs(estimatedPriceDiff).toLocaleString()}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">新座位票价合计</p>
                    <p className="font-medium">¥{selectedSeatTotal.toLocaleString()}</p>
                  </div>
                </div>

                {loadingSeats ? (
                  <div className="flex min-h-[220px] items-center justify-center">
                    <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                  </div>
                ) : (
                  <div className="space-y-4">
                    {order.passengers.map((passenger) => {
                      const seatOptions = getSeatOptions(passenger.passengerId)

                      return (
                        <div key={passenger.passengerId} className="rounded-xl border p-4">
                          <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
                            <div>
                              <p className="font-medium">{passenger.passengerName}</p>
                              <p className="text-xs text-muted-foreground">
                                {PASSENGER_TYPE_LABEL[passenger.passengerType]} · 原座位 {passenger.seatNo}
                              </p>
                            </div>
                            <FlightPriceTag price={passenger.ticketPrice} className="text-sm" />
                          </div>

                          <div className="space-y-1.5">
                            <Label>新座位</Label>
                            <Select
                              value={seatMappings[passenger.passengerId] || ""}
                              onValueChange={(value) => {
                                setSeatMappings((current) => ({
                                  ...current,
                                  [passenger.passengerId]: value ?? "",
                                }))
                                setSeatErrors((current) => ({
                                  ...current,
                                  [passenger.passengerId]: "",
                                }))
                              }}
                            >
                              <SelectTrigger className="w-full">
                                <SelectValue placeholder="请选择新座位" />
                              </SelectTrigger>
                              <SelectContent>
                                {seatOptions.map((seat) => (
                                  <SelectItem key={seat.id} value={String(seat.id)}>
                                    {seat.seatNo} · ¥{seat.price.toLocaleString()}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                            {seatErrors[passenger.passengerId] && (
                              <p className="text-xs text-destructive">
                                {seatErrors[passenger.passengerId]}
                              </p>
                            )}
                          </div>
                        </div>
                      )
                    })}
                  </div>
                )}
              </div>
            )}

            {submitError && (
              <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                {submitError}
              </div>
            )}
          </div>
        )}

        <DialogFooter>
          {step === 2 ? (
            <>
              <Button
                variant="outline"
                onClick={() => setStep(1)}
                disabled={isSubmitting || loadingSeats}
              >
                上一步
              </Button>
              <Button variant="destructive" onClick={handleSubmit} disabled={!order || isSubmitting}>
                {isSubmitting && <Loader2 className="h-4 w-4 animate-spin" />}
                确认改签
              </Button>
            </>
          ) : (
            <>
              <Button variant="outline" onClick={() => onOpenChange(false)} disabled={loadingSeats}>
                取消
              </Button>
              <Button onClick={loadSeats} disabled={!order || loadingOptions || loadingSeats}>
                {loadingSeats && <Loader2 className="h-4 w-4 animate-spin" />}
                下一步
              </Button>
            </>
          )}
        </DialogFooter>

        {step === 2 && (
          <>
            <Separator />
            <div className="text-xs text-muted-foreground">
              最终费用以改签接口返回结果为准，当前仅用于前端预览。
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}

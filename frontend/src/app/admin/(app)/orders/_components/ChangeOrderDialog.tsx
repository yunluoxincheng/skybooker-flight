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
import { Separator } from "@/components/ui/separator"
import { Textarea } from "@/components/ui/textarea"
import { formatDateFull, formatTime } from "@/lib/date-utils"
import { getErrorMessage } from "@/lib/error-codes"
import { PASSENGER_TYPE_LABEL } from "@/lib/passenger-utils"
import * as adminApi from "@/services/adminApi"
import type { FlightSeatVO } from "@/types/flight"
import type { ChangeOptionVO, OrderVO } from "@/types/order"

const changeSeatMappingSchema = z.object({
  newFlightId: z.coerce.number().min(1, "请选择改签航班"),
  seatMappings: z
    .array(
      z.object({
        passengerId: z.number().min(1),
        newSeatId: z.coerce.number().min(1, "请选择新座位"),
      })
    )
    .min(1, "请完成座位映射"),
  reason: z.string().trim().min(1, "请输入改签原因").max(100, "改签原因不能超过 100 字"),
  force: z.boolean().optional(),
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
  const [seatOptions, setSeatOptions] = useState<FlightSeatVO[]>([])
  const [selectedFlightId, setSelectedFlightId] = useState("")
  const [seatMappings, setSeatMappings] = useState<Record<number, string>>({})
  const [reason, setReason] = useState("")
  const [force, setForce] = useState(false)
  const [flightError, setFlightError] = useState<string | null>(null)
  const [seatErrors, setSeatErrors] = useState<Record<number, string>>({})
  const [reasonError, setReasonError] = useState<string | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isOptionsLoading, setIsOptionsLoading] = useState(false)
  const [isSeatsLoading, setIsSeatsLoading] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    setStep(1)
    setOptions([])
    setSeatOptions([])
    setSelectedFlightId("")
    setSeatMappings({})
    setReason("")
    setForce(false)
    setFlightError(null)
    setSeatErrors({})
    setReasonError(null)
    setLoadError(null)
    setSubmitError(null)
    setIsOptionsLoading(false)
    setIsSeatsLoading(false)
    setIsSubmitting(false)
  }, [open, order])

  useEffect(() => {
    if (!open || !order) return

    let cancelled = false

    const loadOptions = async () => {
      setIsOptionsLoading(true)
      setLoadError(null)
      try {
        const data = await adminApi.getAdminChangeOptions(order.id)
        if (!cancelled) {
          setOptions(data)
        }
      } catch (err) {
        if (!cancelled) {
          setLoadError(getErrorMessage(err, "加载改签候选航班失败"))
        }
      } finally {
        if (!cancelled) {
          setIsOptionsLoading(false)
        }
      }
    }

    void loadOptions()

    return () => {
      cancelled = true
    }
  }, [open, order])

  const selectedOption = useMemo(
    () => options.find((option) => option.flightId === Number(selectedFlightId)) ?? null,
    [options, selectedFlightId]
  )

  const selectedSeatTotal = useMemo(() => {
    return Object.values(seatMappings).reduce((sum, seatId) => {
      const matchedSeat = seatOptions.find((seat) => seat.id === Number(seatId))
      return matchedSeat ? sum + matchedSeat.price : sum
    }, 0)
  }, [seatMappings, seatOptions])

  const estimatedPriceDiff = useMemo(() => {
    if (!order) return 0
    return selectedSeatTotal - order.ticketAmount
  }, [order, selectedSeatTotal])

  useEffect(() => {
    if (!open || !selectedFlightId || step !== 2) return

    let cancelled = false

    const loadSeats = async () => {
      setIsSeatsLoading(true)
      setLoadError(null)
      try {
        const data = await adminApi.getAdminFlightSeats(Number(selectedFlightId))
        if (!cancelled) {
          setSeatOptions(data)
        }
      } catch (err) {
        if (!cancelled) {
          setLoadError(getErrorMessage(err, "加载可选座位失败"))
          setSeatOptions([])
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
  }, [open, selectedFlightId, step])

  const getSeatChoices = (passengerId: number) => {
    const selectedByOthers = new Set(
      Object.entries(seatMappings)
        .filter(([id, seatId]) => Number(id) !== passengerId && seatId)
        .map(([, seatId]) => Number(seatId))
    )

    return seatOptions.filter((seat) => {
      if (seat.status !== "AVAILABLE" && seat.id !== Number(seatMappings[passengerId])) {
        return false
      }
      return !selectedByOthers.has(seat.id) || seat.id === Number(seatMappings[passengerId])
    })
  }

  const handleNextStep = () => {
    if (!selectedFlightId) {
      setFlightError("请选择改签航班")
      return
    }

    setStep(2)
  }

  const handleSubmit = async () => {
    if (!order || isSubmitting) return

    const payload = {
      newFlightId: selectedFlightId,
      seatMappings: order.passengers.map((passenger) => ({
        passengerId: passenger.passengerId,
        newSeatId: seatMappings[passenger.passengerId],
      })),
      reason,
      force,
    }

    const parsed = changeSeatMappingSchema.safeParse(payload)
    if (!parsed.success) {
      const nextSeatErrors: Record<number, string> = {}
      let nextFlightError: string | null = null
      let nextReasonError: string | null = null

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

        if (issue.path[0] === "reason" && !nextReasonError) {
          nextReasonError = issue.message
        }
      }

      setFlightError(nextFlightError)
      setSeatErrors(nextSeatErrors)
      setReasonError(nextReasonError)
      return
    }

    setIsSubmitting(true)
    setSubmitError(null)
    setReasonError(null)

    try {
      await adminApi.changeAdminOrder(order.id, {
        newFlightId: parsed.data.newFlightId,
        seatMappings: parsed.data.seatMappings,
        reason: parsed.data.reason.trim(),
        force: parsed.data.force,
      })
      onOpenChange(false)
      await onSuccess?.()
    } catch (err) {
      setSubmitError(getErrorMessage(err, "改签失败，请稍后重试"))
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
                <div className="space-y-1.5">
                  <Label>改签到航班</Label>
                  <Select
                    value={selectedFlightId}
                    onValueChange={(value) => {
                      setSelectedFlightId(value ?? "")
                      setFlightError(null)
                      setSeatMappings({})
                    }}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder={isOptionsLoading ? "正在加载候选航班..." : "请选择改签航班"} />
                    </SelectTrigger>
                    <SelectContent>
                      {options.map((option) => (
                        <SelectItem key={option.flightId} value={String(option.flightId)}>
                          {option.flightNo} · {formatDateTime(option.departureTime)} · ¥{option.basePrice.toLocaleString()}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {flightError && <p className="text-xs text-destructive">{flightError}</p>}
                </div>

                {selectedOption ? (
                  <div className="grid gap-3 rounded-xl border bg-muted/30 p-4 text-sm md:grid-cols-3">
                    <div>
                      <p className="text-xs text-muted-foreground">目标航班</p>
                      <p className="font-medium">{selectedOption.flightNo}</p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">起飞时间</p>
                      <p>{formatDateTime(selectedOption.departureTime)}</p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">基础票价</p>
                      <p className="font-medium">¥{selectedOption.basePrice.toLocaleString()}</p>
                    </div>
                  </div>
                ) : null}

                {loadError ? (
                  <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                    {loadError}
                  </div>
                ) : null}

                <div className="rounded-xl border border-dashed p-8 text-center text-sm text-muted-foreground">
                  {isOptionsLoading ? "正在加载候选航班..." : "请选择候选航班后进入座位分配"}
                </div>
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

                <div className="space-y-4">
                  {order.passengers.map((passenger) => (
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
                            setSeatErrors((current) => ({ ...current, [passenger.passengerId]: "" }))
                          }}
                        >
                          <SelectTrigger className="w-full">
                            <SelectValue placeholder={isSeatsLoading ? "正在加载座位..." : "请选择新座位"} />
                          </SelectTrigger>
                          <SelectContent>
                            {getSeatChoices(passenger.passengerId).map((seat) => (
                              <SelectItem key={seat.id} value={String(seat.id)}>
                                {seat.seatNo} · {seat.cabinClass} · ¥{seat.price.toLocaleString()}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        {seatErrors[passenger.passengerId] && (
                          <p className="text-xs text-destructive">{seatErrors[passenger.passengerId]}</p>
                        )}
                      </div>
                    </div>
                  ))}
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="change-order-reason">改签原因</Label>
                  <Textarea
                    id="change-order-reason"
                    rows={4}
                    value={reason}
                    disabled={isSubmitting}
                    onChange={(event) => {
                      setReason(event.target.value)
                      setReasonError(null)
                    }}
                    placeholder="请输入改签原因"
                    aria-invalid={reasonError ? "true" : "false"}
                  />
                  {reasonError ? <p className="text-xs text-destructive">{reasonError}</p> : null}
                </div>

                <div className="rounded-xl border p-4">
                  <label className="flex items-start gap-3 text-sm">
                    <Checkbox
                      checked={force}
                      disabled={isSubmitting}
                      onCheckedChange={(checked) => setForce(checked === true)}
                    />
                    <div className="space-y-1">
                      <span className="font-medium">强制执行</span>
                      <p className="text-muted-foreground">必要时跳过部分费用校验，由管理员直接提交改签。</p>
                    </div>
                  </label>
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
                disabled={isSubmitting}
              >
                上一步
              </Button>
              <Button variant="destructive" onClick={handleSubmit} disabled={isSubmitting || !order}>
                {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                {isSubmitting ? "提交中..." : "确认改签"}
              </Button>
            </>
          ) : (
            <>
              <Button variant="outline" onClick={() => onOpenChange(false)}>
                取消
              </Button>
              <Button onClick={handleNextStep} disabled={isOptionsLoading || !order}>
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

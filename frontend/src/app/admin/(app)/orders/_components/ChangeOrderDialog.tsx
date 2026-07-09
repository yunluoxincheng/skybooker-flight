"use client"

import { useEffect, useMemo, useState } from "react"
import { z } from "zod"
import { ArrowRightLeft } from "lucide-react"
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
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { formatDateFull, formatTime } from "@/lib/date-utils"
import { PASSENGER_TYPE_LABEL } from "@/lib/passenger-utils"
import type { ChangeOptionVO, OrderVO } from "@/types/order"

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
}: ChangeOrderDialogProps) {
  const featureDisabledMessage = "管理端改签功能依赖后端接口，当前暂不可用。"
  const [step, setStep] = useState<1 | 2>(1)
  const [options] = useState<ChangeOptionVO[]>([])
  const [selectedFlightId, setSelectedFlightId] = useState("")
  const [seatMappings, setSeatMappings] = useState<Record<number, string>>({})
  const [flightError, setFlightError] = useState<string | null>(null)
  const [seatErrors, setSeatErrors] = useState<Record<number, string>>({})

  useEffect(() => {
    if (!open || !order) return

    setStep(1)
    setSelectedFlightId("")
    setSeatMappings({})
    setFlightError(null)
    setSeatErrors({})
  }, [open, order])

  const selectedOption = useMemo(
    () => options.find((option) => option.flightId === Number(selectedFlightId)) ?? null,
    [options, selectedFlightId]
  )

  const estimatedPriceDiff = useMemo(() => {
    if (!order || !selectedOption) return 0
    return selectedOption.basePrice * order.passengers.length - order.ticketAmount
  }, [order, selectedOption])

  const selectedSeatTotal = useMemo(() => 0, [])

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
            <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
              {featureDisabledMessage}
            </div>

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
                    disabled
                    value={selectedFlightId}
                    onValueChange={(value) => {
                      setSelectedFlightId(value ?? "")
                      setFlightError(null)
                    }}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder="后端接口补齐后可选择改签航班" />
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

                <div className="rounded-xl border border-dashed p-10 text-center text-sm text-muted-foreground">
                  当前暂不可加载可改签航班与座位数据，待后端接口补齐后恢复。
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
                        <Select disabled value={seatMappings[passenger.passengerId] || ""}>
                          <SelectTrigger className="w-full">
                            <SelectValue placeholder="后端接口补齐后可选择新座位" />
                          </SelectTrigger>
                          <SelectContent />
                        </Select>
                        {seatErrors[passenger.passengerId] && (
                          <p className="text-xs text-destructive">{seatErrors[passenger.passengerId]}</p>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
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
              >
                上一步
              </Button>
              <Tooltip>
                <TooltipTrigger render={<span className="inline-flex" tabIndex={0} />}>
                  <Button variant="destructive" onClick={handleSubmit} disabled>
                    确认改签
                  </Button>
                </TooltipTrigger>
                <TooltipContent>{featureDisabledMessage}</TooltipContent>
              </Tooltip>
            </>
          ) : (
            <>
              <Button variant="outline" onClick={() => onOpenChange(false)}>
                取消
              </Button>
              <Tooltip>
                <TooltipTrigger render={<span className="inline-flex" tabIndex={0} />}>
                  <Button onClick={() => setStep(2)} disabled>
                    下一步
                  </Button>
                </TooltipTrigger>
                <TooltipContent>{featureDisabledMessage}</TooltipContent>
              </Tooltip>
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

"use client"

import { useEffect, useMemo, useState } from "react"
import { z } from "zod"
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
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import type { OrderVO } from "@/types/order"

const refundFormSchema = z.object({
  reason: z.string().trim().min(1, "请输入退票原因").max(500, "退票原因不能超过 500 字"),
  refundFee: z.preprocess(
    (value) => {
      if (value === "" || value === undefined || value === null) return undefined
      if (typeof value === "number") return Number.isNaN(value) ? undefined : value
      const numericValue = Number(value)
      return Number.isNaN(numericValue) ? value : numericValue
    },
    z.number().min(0, "手续费不能小于 0").optional()
  ),
})

type RefundFormValues = z.infer<typeof refundFormSchema>

interface RefundConfirmDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  order: OrderVO | null
  onSuccess?: () => Promise<void> | void
}

export function RefundConfirmDialog({
  open,
  onOpenChange,
  order,
}: RefundConfirmDialogProps) {
  const featureDisabledMessage = "管理端退票功能依赖后端接口，当前暂不可用。"
  const [form, setForm] = useState<{ reason: string; refundFee: string }>({
    reason: "",
    refundFee: "",
  })
  const [errors, setErrors] = useState<Partial<Record<keyof RefundFormValues, string>>>({})

  useEffect(() => {
    if (!open) return
    setForm({ reason: "", refundFee: "" })
    setErrors({})
  }, [open, order?.id])

  const estimatedRefund = useMemo(() => {
    if (!order) return 0
    const fee = form.refundFee.trim() ? Number(form.refundFee) : 0
    if (Number.isNaN(fee)) return order.totalAmount
    return Math.max(order.totalAmount - fee, 0)
  }, [form.refundFee, order])

  const handleSubmit = async () => {
    if (!order) return

    const parsed = refundFormSchema.safeParse({
      reason: form.reason,
      refundFee: form.refundFee,
    })

    if (!parsed.success) {
      const nextErrors: Partial<Record<keyof RefundFormValues, string>> = {}
      for (const issue of parsed.error.issues) {
        const field = issue.path[0] as keyof RefundFormValues | undefined
        if (field && !nextErrors[field]) {
          nextErrors[field] = issue.message
        }
      }
      setErrors(nextErrors)
      return
    }

    setErrors({})
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>确认退票</DialogTitle>
          <DialogDescription>
            提交后会按管理员退票流程执行，请确认退款原因和手续费覆盖金额。
          </DialogDescription>
        </DialogHeader>

        {!order ? null : (
          <div className="space-y-4">
            <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
              {featureDisabledMessage}
            </div>

            <div className="rounded-xl border bg-muted/30 p-4 text-sm">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="font-medium">{order.orderNo}</p>
                  <p className="text-xs text-muted-foreground">
                    {order.userNickname || order.userEmail} · {order.flightNo}
                  </p>
                </div>
                <FlightPriceTag price={order.totalAmount} />
              </div>
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                <div>
                  <p className="text-xs text-muted-foreground">订单金额</p>
                  <p className="font-medium">¥{order.totalAmount.toLocaleString()}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">预计退款金额</p>
                  <p className="font-medium text-primary">¥{estimatedRefund.toLocaleString()}</p>
                </div>
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="refund-reason">退票原因</Label>
              <Textarea
                id="refund-reason"
                rows={4}
                disabled
                value={form.reason}
                onChange={(event) => {
                  setForm((current) => ({ ...current, reason: event.target.value }))
                  setErrors((current) => ({ ...current, reason: undefined }))
                }}
                placeholder="请输入退票原因"
                aria-invalid={errors.reason ? "true" : "false"}
              />
              {errors.reason && <p className="text-xs text-destructive">{errors.reason}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="refund-fee">手续费覆盖</Label>
              <Input
                id="refund-fee"
                type="number"
                min="0"
                step="0.01"
                disabled
                value={form.refundFee}
                onChange={(event) => {
                  setForm((current) => ({ ...current, refundFee: event.target.value }))
                  setErrors((current) => ({ ...current, refundFee: undefined }))
                }}
                placeholder="留空则按后端默认规则计算"
                aria-invalid={errors.refundFee ? "true" : "false"}
              />
              {errors.refundFee && <p className="text-xs text-destructive">{errors.refundFee}</p>}
            </div>

            <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700">
              当前仅保留表单结构展示，退票提交需后端管理接口支持。
            </div>
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Tooltip>
            <TooltipTrigger render={<span className="inline-flex" tabIndex={0} />}>
              <Button variant="destructive" onClick={handleSubmit} disabled>
                确认退票
              </Button>
            </TooltipTrigger>
            <TooltipContent>{featureDisabledMessage}</TooltipContent>
          </Tooltip>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

"use client"

import { useEffect, useState } from "react"
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
import { Textarea } from "@/components/ui/textarea"
import { getErrorMessage } from "@/lib/error-codes"
import * as adminApi from "@/services/adminApi"
import type { OrderVO } from "@/types/order"

const refundFormSchema = z.object({
  reason: z.string().trim().min(1, "请输入退票原因").max(100, "退票原因不能超过 100 字"),
  force: z.boolean().optional(),
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
  onSuccess,
}: RefundConfirmDialogProps) {
  const [form, setForm] = useState<{ reason: string; force: boolean }>({
    reason: "",
    force: false,
  })
  const [errors, setErrors] = useState<Partial<Record<keyof RefundFormValues, string>>>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (!open) return
    setForm({ reason: "", force: false })
    setErrors({})
    setSubmitError(null)
  }, [open, order?.id])

  const handleSubmit = async () => {
    if (!order || isSubmitting) return

    const parsed = refundFormSchema.safeParse({
      reason: form.reason,
      force: form.force,
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

    setIsSubmitting(true)
    setErrors({})
    setSubmitError(null)

    try {
      await adminApi.refundAdminOrder(order.id, {
        reason: parsed.data.reason.trim(),
        force: parsed.data.force,
      })
      onOpenChange(false)
      await onSuccess?.()
    } catch (err) {
      setSubmitError(getErrorMessage(err, "退票失败，请稍后重试"))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>确认退票</DialogTitle>
          <DialogDescription>
            提交后会按管理员退票流程执行，请确认退款原因和是否需要强制执行。
          </DialogDescription>
        </DialogHeader>

        {!order ? null : (
          <div className="space-y-4">
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
              <div className="mt-3">
                <div>
                  <p className="text-xs text-muted-foreground">订单金额</p>
                  <p className="font-medium">¥{order.totalAmount.toLocaleString()}</p>
                </div>
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="refund-reason">退票原因</Label>
              <Textarea
                id="refund-reason"
                rows={4}
                disabled={isSubmitting}
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

            <div className="rounded-xl border p-4">
              <label className="flex items-start gap-3 text-sm">
                <Checkbox
                  checked={form.force}
                  disabled={isSubmitting}
                  onCheckedChange={(checked) => {
                    setForm((current) => ({ ...current, force: checked === true }))
                    setErrors((current) => ({ ...current, force: undefined }))
                  }}
                />
                <div className="space-y-1">
                  <span className="font-medium">强制执行</span>
                  <p className="text-muted-foreground">跳过费用检查，由管理员直接发起退票处理。</p>
                </div>
              </label>
              {errors.force && <p className="mt-2 text-xs text-destructive">{errors.force}</p>}
            </div>

            {submitError ? (
              <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                {submitError}
              </div>
            ) : null}
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
            取消
          </Button>
          <Button variant="destructive" onClick={handleSubmit} disabled={isSubmitting || !order}>
            {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            {isSubmitting ? "提交中..." : "确认退票"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

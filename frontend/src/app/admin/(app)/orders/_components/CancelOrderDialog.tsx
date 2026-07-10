"use client"

import { useEffect, useState } from "react"
import { Loader2 } from "lucide-react"
import { z } from "zod"
import { OrderStatusBadge } from "@/components/common/OrderStatusBadge"
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
import { Textarea } from "@/components/ui/textarea"
import { getErrorMessage } from "@/lib/error-codes"
import * as adminApi from "@/services/adminApi"
import type { OrderVO } from "@/types/order"

const cancelSchema = z.object({
  reason: z.string().trim().min(1, "请输入取消原因").max(100, "取消原因不能超过 100 字"),
})

interface CancelOrderDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  order: OrderVO | null
  onSuccess?: () => Promise<void> | void
}

export function CancelOrderDialog({ open, onOpenChange, order, onSuccess }: CancelOrderDialogProps) {
  const [reason, setReason] = useState("")
  const [reasonError, setReasonError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (!open) return
    setReason("")
    setReasonError(null)
    setError(null)
  }, [open, order?.id])

  const handleSubmit = async () => {
    if (!order || isSubmitting) return
    const parsed = cancelSchema.safeParse({ reason })
    if (!parsed.success) {
      setReasonError(parsed.error.issues[0]?.message || "请输入取消原因")
      return
    }

    setIsSubmitting(true)
    setError(null)
    setReasonError(null)
    try {
      await adminApi.cancelAdminOrder(order.id, parsed.data)
      onOpenChange(false)
      await onSuccess?.()
    } catch (err) {
      setError(getErrorMessage(err, "取消订单失败，请稍后重试"))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>取消待支付订单</DialogTitle>
          <DialogDescription>
            取消不退款。系统将释放已锁定座位并回补航班余票，不会创建退款记录。
          </DialogDescription>
        </DialogHeader>
        {order ? (
          <div className="space-y-4">
            <div className="rounded-xl border bg-muted/30 p-4 text-sm">
              <p className="font-medium">{order.orderNo}</p>
              <div className="mt-1 flex items-center gap-2 text-muted-foreground">
                <span>{order.flightNo}</span><span>·</span><OrderStatusBadge status={order.status} className="h-5" />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="cancel-order-reason">取消原因</Label>
              <Textarea
                id="cancel-order-reason"
                rows={4}
                value={reason}
                disabled={isSubmitting}
                onChange={(event) => { setReason(event.target.value); setReasonError(null) }}
                placeholder="请输入取消原因"
                aria-invalid={reasonError ? "true" : "false"}
              />
              {reasonError ? <p className="text-xs text-destructive">{reasonError}</p> : null}
            </div>
            {error ? <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">{error}</div> : null}
          </div>
        ) : null}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>返回</Button>
          <Button variant="destructive" onClick={handleSubmit} disabled={isSubmitting || !order}>
            {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            确认取消（不退款）
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

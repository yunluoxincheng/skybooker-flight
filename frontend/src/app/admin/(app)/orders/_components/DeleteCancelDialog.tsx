"use client"

import { useEffect, useMemo, useState } from "react"
import { z } from "zod"
import { Loader2 } from "lucide-react"
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
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { Textarea } from "@/components/ui/textarea"
import { getErrorMessage } from "@/lib/error-codes"
import * as adminApi from "@/services/adminApi"
import type { OrderVO } from "@/types/order"

export type DeleteOrderAction = "cancel" | "delete"

const deleteCancelSchema = z.object({
  type: z.enum(["cancel", "delete"]),
  reason: z.string().trim().min(1, "请输入操作原因").max(100, "操作原因不能超过 100 字"),
})

interface DeleteCancelDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  order: OrderVO | null
  initialType?: DeleteOrderAction
  onSuccess?: () => Promise<void> | void
}

export function DeleteCancelDialog({
  open,
  onOpenChange,
  order,
  initialType = "cancel",
  onSuccess,
}: DeleteCancelDialogProps) {
  const [type, setType] = useState<DeleteOrderAction>(initialType)
  const [reason, setReason] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [reasonError, setReasonError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (!open) return
    setType(initialType)
    setReason("")
    setError(null)
    setReasonError(null)
  }, [initialType, open, order?.id])

  const riskTips = useMemo(() => {
    if (!order) return []
    const tips: string[] = []
    if (order.payTime) tips.push("订单已存在支付记录")
    if (order.status === "ISSUED" || order.status === "CHANGED") tips.push("订单已进入出票流程")
    if (order.status === "REFUNDED") tips.push("订单已产生退票记录")
    if (order.passengers.length > 1) tips.push("订单包含多位乘机人，请确认后续履约影响")
    return tips
  }, [order])

  const handleSubmit = async () => {
    if (!order || isSubmitting) return

    const parsed = deleteCancelSchema.safeParse({ type, reason })
    if (!parsed.success) {
      setReasonError(parsed.error.issues[0]?.message || "请输入操作原因")
      return
    }

    setIsSubmitting(true)
    setError(null)
    setReasonError(null)

    try {
      if (parsed.data.type === "cancel") {
        await adminApi.voidAdminOrder(order.id, { reason: parsed.data.reason })
      } else {
        await adminApi.deleteAdminOrder(order.id, "delete", parsed.data.reason)
      }
      onOpenChange(false)
      await onSuccess?.()
    } catch (err) {
      setError(getErrorMessage(err, parsed.data.type === "cancel" ? "作废订单失败" : "删除订单失败"))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>删除 / 作废订单</DialogTitle>
          <DialogDescription>
            逻辑作废会保留数据痕迹，物理删除风险更高，请谨慎操作。
          </DialogDescription>
        </DialogHeader>

        {!order ? null : (
          <div className="space-y-4">
            <div className="rounded-xl border bg-muted/30 p-4 text-sm">
              <p className="font-medium">{order.orderNo}</p>
              <p className="mt-1 text-muted-foreground">
                {order.userNickname || order.userEmail} · {order.flightNo} · 当前状态 {order.status}
              </p>
            </div>

            <div className="space-y-3">
              <Label>操作类型</Label>
              <RadioGroup
                value={type}
                onValueChange={(value) => setType(value as DeleteOrderAction)}
                className="gap-3"
              >
                <label className="flex items-start gap-3 rounded-xl border p-3 text-sm">
                  <RadioGroupItem value="cancel" />
                  <div>
                    <p className="font-medium">作废订单</p>
                    <p className="text-muted-foreground">
                      保留订单记录，仅做逻辑作废，适合待支付、已取消、已过期等场景。
                    </p>
                  </div>
                </label>
                <label className="flex items-start gap-3 rounded-xl border border-destructive/30 p-3 text-sm">
                  <RadioGroupItem value="delete" />
                  <div>
                    <p className="font-medium text-destructive">物理删除订单</p>
                    <p className="text-muted-foreground">
                      会直接删除订单数据，不可恢复，仅在必须清理脏数据时使用。
                    </p>
                  </div>
                </label>
              </RadioGroup>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="delete-cancel-reason">操作原因</Label>
              <Textarea
                id="delete-cancel-reason"
                rows={4}
                value={reason}
                disabled={isSubmitting}
                onChange={(event) => {
                  setReason(event.target.value)
                  setReasonError(null)
                }}
                placeholder={type === "cancel" ? "请输入作废原因" : "请输入删除原因"}
                aria-invalid={reasonError ? "true" : "false"}
              />
              {reasonError ? <p className="text-xs text-destructive">{reasonError}</p> : null}
            </div>

            {riskTips.length > 0 && (
              <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
                <p className="font-medium">风险提示</p>
                <ul className="mt-2 list-disc space-y-1 pl-5">
                  {riskTips.map((tip) => (
                    <li key={tip}>{tip}</li>
                  ))}
                </ul>
              </div>
            )}

            {error ? (
              <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                {error}
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
            {type === "cancel" ? "确认作废" : "确认删除"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

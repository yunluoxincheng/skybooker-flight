"use client"

import { useEffect, useMemo, useState } from "react"
import { z } from "zod"
import { Loader2 } from "lucide-react"
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
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { Textarea } from "@/components/ui/textarea"
import { getErrorMessage } from "@/lib/error-codes"
import * as adminApi from "@/services/adminApi"
import type { OrderVO } from "@/types/order"

export type DeleteOrderAction = "cancel" | "delete"

const deleteCancelSchema = z.object({
  type: z.enum(["cancel", "delete"]),
  reason: z.string().trim().min(1, "请输入作废原因").max(100, "作废原因不能超过 100 字"),
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
    if (order.status !== "CANCELLED" && order.status !== "REFUNDED") {
      tips.push("当前订单不符合作废条件，操作将被拒绝")
    }
    if (order.payTime) {
      tips.push("订单已有支付记录，作废后仍可通过管理后台查看")
    }
    if (order.passengers.length > 1) tips.push("订单包含多位乘机人，请确认后续履约影响")
    return tips
  }, [order])

  const handleSubmit = async () => {
    if (!order || isSubmitting) return

    const parsed = deleteCancelSchema.safeParse({ type, reason })
    if (!parsed.success) {
      setReasonError(parsed.error.issues[0]?.message || "请输入作废原因")
      return
    }

    setIsSubmitting(true)
    setError(null)
    setReasonError(null)

    try {
      if (parsed.data.type === "cancel") {
        await adminApi.voidAdminOrder(order.id, { reason: parsed.data.reason })
      } else {
        await adminApi.voidAdminOrderByDelete(order.id, "delete", parsed.data.reason)
      }
      onOpenChange(false)
      await onSuccess?.()
    } catch (err) {
      setError(getErrorMessage(err, "作废订单失败"))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>作废订单</DialogTitle>
          <DialogDescription>
            两种作废方式均为逻辑作废（将订单状态置为"已作废"），系统保留订单数据与操作痕迹，不会物理删除。仅允许作废"已取消"或"已退票"状态的订单。
          </DialogDescription>
        </DialogHeader>

        {!order ? null : (
          <div className="space-y-4">
            <div className="rounded-xl border bg-muted/30 p-4 text-sm">
              <p className="font-medium">{order.orderNo}</p>
              <div className="mt-1 flex flex-wrap items-center gap-2 text-muted-foreground">
                <span>{order.userNickname || order.userEmail}</span>
                <span>·</span>
                <span>{order.flightNo}</span>
                <span>·</span>
                <span>当前状态</span>
                <OrderStatusBadge status={order.status} className="h-5" />
              </div>
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
                    <p className="font-medium">普通作废</p>
                    <p className="text-muted-foreground">
                      调用标准作废接口（POST void），将符合条件的订单（已取消 / 已退票）状态更新为"已作废"。订单数据与操作记录完整保留。
                    </p>
                  </div>
                </label>
                <label className="flex items-start gap-3 rounded-xl border p-3 text-sm">
                  <RadioGroupItem value="delete" />
                  <div>
                    <p className="font-medium">删除型作废</p>
                    <p className="text-muted-foreground">
                      调用 DELETE 接口作废（DELETE void），效果与普通作废一致——将符合条件的订单（已取消 / 已退票）状态更新为"已作废"。订单数据与操作记录完整保留，不执行物理删除。
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
                placeholder="请输入作废原因"
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
            确认作废
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

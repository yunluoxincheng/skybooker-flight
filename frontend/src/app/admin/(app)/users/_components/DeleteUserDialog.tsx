"use client"

import { useEffect, useState } from "react"
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
import type { DeleteUserBlockInfoVO, UserAdminVO } from "@/types/admin"
import type { ApiError } from "@/lib/request"
import * as adminApi from "@/services/adminApi"

interface DeleteUserDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  user: UserAdminVO | null
  onSuccess?: () => Promise<void> | void
}

function buildFallbackBlockInfo(): DeleteUserBlockInfoVO {
  return {
    activeOrderCount: 0,
    waitlistCount: 0,
    pendingRefundCount: 0,
    pendingChangeCount: 0,
    canDelete: false,
    blockReasons: [],
  }
}

export function DeleteUserDialog({
  open,
  onOpenChange,
  user,
  onSuccess,
}: DeleteUserDialogProps) {
  const [deleteType, setDeleteType] = useState<"soft" | "hard">("soft")
  const [checkInfo, setCheckInfo] = useState<DeleteUserBlockInfoVO | null>(null)
  const [isChecking, setIsChecking] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open || !user) return

    setDeleteType("soft")
    setCheckInfo(null)
    setError(null)
    setIsSubmitting(false)
    setIsChecking(true)

    adminApi
      .checkUserDeletable(user.id)
      .then((data) => {
        setCheckInfo(data)
      })
      .catch((err: ApiError) => {
        setError(err.message || "检查删除条件失败")
        setCheckInfo(buildFallbackBlockInfo())
      })
      .finally(() => setIsChecking(false))
  }, [open, user])

  const handleDelete = async () => {
    if (!user || !checkInfo?.canDelete) return
    setIsSubmitting(true)
    setError(null)
    try {
      await adminApi.deleteAdminUser(user.id, deleteType)
      onOpenChange(false)
      await onSuccess?.()
    } catch (err) {
      setError((err as ApiError).message || "删除用户失败")
    } finally {
      setIsSubmitting(false)
    }
  }

  const isBlocked = Boolean(checkInfo && !checkInfo.canDelete)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>删除用户</DialogTitle>
          <DialogDescription>
            删除前会先校验订单、候补、退款和改签等阻断条件。
          </DialogDescription>
        </DialogHeader>

        {!user ? null : (
          <div className="space-y-4">
            <div className="rounded-xl border bg-muted/30 p-4 text-sm">
              <p className="font-medium">{user.realName}</p>
              <p className="text-muted-foreground">{user.email}</p>
              {user.phone && <p className="text-muted-foreground">{user.phone}</p>}
            </div>

            {isChecking ? (
              <div className="flex min-h-[160px] items-center justify-center">
                <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
              </div>
            ) : (
              <>
                {checkInfo && (
                  <div className="grid gap-3 rounded-xl border bg-muted/30 p-4 text-sm sm:grid-cols-2">
                    <div>
                      <p className="text-xs text-muted-foreground">进行中订单</p>
                      <p className="font-medium">{checkInfo.activeOrderCount}</p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">候补记录</p>
                      <p className="font-medium">{checkInfo.waitlistCount}</p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">待处理退款</p>
                      <p className="font-medium">{checkInfo.pendingRefundCount}</p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">待处理改签</p>
                      <p className="font-medium">{checkInfo.pendingChangeCount}</p>
                    </div>
                  </div>
                )}

                {isBlocked ? (
                  <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
                    <p className="font-medium">当前用户暂不可删除</p>
                    <ul className="mt-2 list-disc space-y-1 pl-5">
                      {(checkInfo?.blockReasons.length ? checkInfo.blockReasons : ["存在未完成业务数据，请先处理后再删除"]).map((reason) => (
                        <li key={reason}>{reason}</li>
                      ))}
                    </ul>
                  </div>
                ) : (
                  <>
                    <div className="space-y-3">
                      <Label>删除方式</Label>
                      <RadioGroup
                        value={deleteType}
                        onValueChange={(value) => setDeleteType(value as "soft" | "hard")}
                      >
                        <label className="flex items-start gap-3 rounded-xl border p-3 text-sm">
                          <RadioGroupItem value="soft" />
                          <div>
                            <p className="font-medium">软删除</p>
                            <p className="text-muted-foreground">默认推荐，保留审计信息并隐藏用户。</p>
                          </div>
                        </label>
                        <label className="flex items-start gap-3 rounded-xl border border-destructive/30 p-3 text-sm">
                          <RadioGroupItem value="hard" />
                          <div>
                            <p className="font-medium text-destructive">硬删除</p>
                            <p className="text-muted-foreground">
                              彻底删除用户数据，不可撤销，请二次确认业务影响。
                            </p>
                          </div>
                        </label>
                      </RadioGroup>
                    </div>

                    <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                      此操作不可撤销，删除后将刷新当前用户列表。
                    </div>
                  </>
                )}
              </>
            )}

            {error && (
              <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                {error}
              </div>
            )}
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
            取消
          </Button>
          <Button
            variant="destructive"
            onClick={handleDelete}
            disabled={isChecking || isSubmitting || !checkInfo?.canDelete}
          >
            {isSubmitting && <Loader2 className="h-4 w-4 animate-spin" />}
            {deleteType === "soft" ? "确认软删除" : "确认硬删除"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

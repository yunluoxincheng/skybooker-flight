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
import { getDisplayName } from "@/lib/user-utils"
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
    canDelete: true,
    orderCount: 0,
    passengerCount: 0,
    waitlistCount: 0,
    refundOrChangeCount: 0,
    oauthBound: false,
    aiSessionCount: 0,
    aiRecommendationCount: 0,
    blockReasons: ["预检查暂不可用，删除结果以后端实际为准"],
  }
}

export function DeleteUserDialog({
  open,
  onOpenChange,
  user,
  onSuccess,
}: DeleteUserDialogProps) {
  const [checkInfo, setCheckInfo] = useState<DeleteUserBlockInfoVO | null>(null)
  const [isChecking, setIsChecking] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)

  useEffect(() => {
    if (!open || !user) return

    setCheckInfo(null)
    setError(null)
    setInfo(null)
    setIsSubmitting(false)
    setIsChecking(true)

    adminApi
      .checkUserDeletable(user.id)
      .then((data) => {
        setCheckInfo(data)
      })
      .catch((err: ApiError) => {
        if (err.code === 404) {
          setInfo("删除预检查接口暂未上线，系统将直接执行删除")
        } else {
          setError(err.message || "检查删除条件失败")
        }
        setCheckInfo(buildFallbackBlockInfo())
      })
      .finally(() => setIsChecking(false))
  }, [open, user])

  const handleDelete = async () => {
    if (!user || !checkInfo?.canDelete) return
    setIsSubmitting(true)
    setError(null)
    try {
      await adminApi.deleteUser(user.id)
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
          <DialogDescription>此操作将永久删除用户数据（物理删除），不可恢复。仅无业务记录的账户可被删除；有订单、乘机人等数据时，请改用禁用功能。</DialogDescription>
        </DialogHeader>

        {!user ? null : (
          <div className="space-y-4">
            <div className="rounded-xl border bg-muted/30 p-4 text-sm">
                <p className="font-medium">{getDisplayName(user)}</p>
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
                      <p className="text-xs text-muted-foreground">订单记录</p>
                      <p className="font-medium">{checkInfo.orderCount}</p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">乘机人记录</p>
                      <p className="font-medium">{checkInfo.passengerCount}</p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">候补记录</p>
                      <p className="font-medium">{checkInfo.waitlistCount}</p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">退款/改签记录</p>
                      <p className="font-medium">{checkInfo.refundOrChangeCount}</p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">第三方登录绑定</p>
                      <p className="font-medium">{checkInfo.oauthBound ? "已绑定" : "未绑定"}</p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">AI 对话记录</p>
                      <p className="font-medium">{checkInfo.aiSessionCount}</p>
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">AI 推荐记录</p>
                      <p className="font-medium">{checkInfo.aiRecommendationCount}</p>
                    </div>
                  </div>
                )}

                {info && (
                  <div className="rounded-lg border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-800">
                    {info}
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
                    <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                      此为物理删除，数据不可恢复。仅零业务引用的干净账号可删除。存在业务记录时请返回并选择&ldquo;禁用&rdquo;。
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
            确认删除
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

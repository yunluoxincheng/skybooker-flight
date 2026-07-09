"use client"

import { useEffect, useState } from "react"
import { AlertTriangle, Loader2 } from "lucide-react"
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
import { useAuth } from "@/contexts/AuthContext"
import { getErrorMessage } from "@/lib/error-codes"
import { ApiError } from "@/lib/request"
import * as authApi from "@/services/authApi"
import type { DeleteAccountBlockInfoVO } from "@/types/auth"

interface DeleteAccountDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onDeleted: () => Promise<void> | void
}

type DeleteStep = "warning" | "confirm"

export function DeleteAccountDialog({ open, onOpenChange, onDeleted }: DeleteAccountDialogProps) {
  const { deleteAccount } = useAuth()
  const [step, setStep] = useState<DeleteStep>("warning")
  const [password, setPassword] = useState("")
  const [isChecking, setIsChecking] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [blockInfo, setBlockInfo] = useState<DeleteAccountBlockInfoVO | null>(null)

  useEffect(() => {
    if (!open) {
      setStep("warning")
      setPassword("")
      setIsChecking(false)
      setIsSubmitting(false)
      setError(null)
      setInfo(null)
      setBlockInfo(null)
      return
    }

    let cancelled = false

    setStep("warning")
    setPassword("")
    setIsChecking(true)
    setIsSubmitting(false)
    setError(null)
    setInfo(null)
    setBlockInfo(null)

    authApi
      .checkDeleteAccount()
      .then((data) => {
        if (cancelled) return
        setBlockInfo(data)
      })
      .catch((err) => {
        if (cancelled) return
        if (err instanceof ApiError && err.code === 404) {
          setInfo("注销预检查接口暂未就绪，提交时将以后端实际校验结果为准")
          return
        }
        setInfo(getErrorMessage(err, "暂时无法预检查账号状态，提交时将以后端实际校验结果为准"))
      })
      .finally(() => {
        if (!cancelled) {
          setIsChecking(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [open])

  const handleSubmit = async () => {
    if (!password.trim() || isSubmitting) return

    setIsSubmitting(true)
    setError(null)

    try {
      await deleteAccount({ password: password.trim() })
      onOpenChange(false)
      await onDeleted()
    } catch (err) {
      setError(getErrorMessage(err, "注销账号失败，请稍后重试"))

      if (err instanceof ApiError && err.code === 409) {
        try {
          const nextBlockInfo = await authApi.checkDeleteAccount()
          setBlockInfo(nextBlockInfo)
        } catch {
          // Ignore secondary check failures and keep the original error message.
        }
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  const isBlocked = Boolean(blockInfo && !blockInfo.canDelete)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>注销账号</DialogTitle>
          <DialogDescription>
            注销后当前账号将无法继续使用，请在确认业务数据已处理完毕后再继续。
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {step === "warning" ? (
            <>
              <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">
                <div className="flex items-start gap-3">
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                  <div className="space-y-2">
                    <p className="font-medium">注销后可能无法恢复账号</p>
                    <ul className="list-disc space-y-1 pl-5 text-sm">
                      <li>登录状态会立即失效，当前设备中的本地登录信息会被清空。</li>
                      <li>历史订单、候补、退款和改签记录将按系统策略保留或结算。</li>
                      <li>如果账号仍有关联中的业务数据，后端会拒绝本次注销请求。</li>
                    </ul>
                  </div>
                </div>
              </div>

              {isChecking ? (
                <div className="flex items-center gap-2 rounded-lg border bg-muted/30 px-3 py-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  正在检查账号状态...
                </div>
              ) : null}

              {blockInfo ? (
                <div className="grid gap-3 rounded-xl border bg-muted/30 p-4 text-sm sm:grid-cols-2">
                  <div>
                    <p className="text-xs text-muted-foreground">进行中订单</p>
                    <p className="font-medium">{blockInfo.activeOrderCount}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">候补记录</p>
                    <p className="font-medium">{blockInfo.waitlistCount}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">待处理退款</p>
                    <p className="font-medium">{blockInfo.pendingRefundCount}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">待处理改签</p>
                    <p className="font-medium">{blockInfo.pendingChangeCount}</p>
                  </div>
                </div>
              ) : null}

              {isBlocked ? (
                <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
                  <p className="font-medium">当前账号存在待处理业务，提交时后端仍会再次校验</p>
                  <ul className="mt-2 list-disc space-y-1 pl-5">
                    {(blockInfo?.blockReasons.length
                      ? blockInfo.blockReasons
                      : ["存在未完成业务数据，请先处理后再尝试注销"]).map((reason) => (
                      <li key={reason}>{reason}</li>
                    ))}
                  </ul>
                </div>
              ) : null}

              {info ? (
                <div className="rounded-lg border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-800">
                  {info}
                </div>
              ) : null}
            </>
          ) : (
            <>
              <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                请输入登录密码完成身份验证。提交后将立即尝试注销账号，此操作不可撤销。
              </div>

              <div className="space-y-2">
                <Label htmlFor="delete-account-password">登录密码</Label>
                <Input
                  id="delete-account-password"
                  type="password"
                  autoComplete="current-password"
                  placeholder="请输入当前登录密码"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  disabled={isSubmitting}
                />
              </div>

              {isBlocked ? (
                <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
                  <p className="font-medium">当前账号暂不满足注销条件</p>
                  <ul className="mt-2 list-disc space-y-1 pl-5">
                    {(blockInfo?.blockReasons.length
                      ? blockInfo.blockReasons
                      : ["存在未完成业务数据，请先处理后再尝试注销"]).map((reason) => (
                      <li key={reason}>{reason}</li>
                    ))}
                  </ul>
                </div>
              ) : null}

              {info ? (
                <div className="rounded-lg border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-800">
                  {info}
                </div>
              ) : null}

              {error ? (
                <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                  {error}
                </div>
              ) : null}
            </>
          )}
        </div>

        <DialogFooter>
          {step === "warning" ? (
            <>
              <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
                取消
              </Button>
              <Button variant="destructive" onClick={() => setStep("confirm")} disabled={isSubmitting}>
                继续注销
              </Button>
            </>
          ) : (
            <>
              <Button variant="outline" onClick={() => setStep("warning")} disabled={isSubmitting}>
                返回上一步
              </Button>
              <Button variant="destructive" onClick={handleSubmit} disabled={isSubmitting || !password.trim()}>
                {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                {isSubmitting ? "注销中..." : "确认注销"}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

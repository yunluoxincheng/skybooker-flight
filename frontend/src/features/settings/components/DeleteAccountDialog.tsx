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
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setStep("warning")
    setPassword("")
    setIsSubmitting(false)
    setError(null)
  }, [open])

  const handleSubmit = async () => {
    if (!password.trim() || isSubmitting) return

    setIsSubmitting(true)
    setError(null)

    try {
      await deleteAccount({ currentPassword: password.trim() })
      onOpenChange(false)
      await onDeleted()
    } catch (err) {
      if (err instanceof ApiError && err.code === 409) {
        setError(getErrorMessage(err, "当前账号存在未完成业务，暂时无法注销"))
      } else {
        setError(getErrorMessage(err, "注销账号失败，请稍后重试"))
      }
    } finally {
      setIsSubmitting(false)
    }
  }

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

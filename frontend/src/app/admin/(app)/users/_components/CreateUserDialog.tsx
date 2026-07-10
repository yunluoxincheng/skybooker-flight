"use client"

import { useEffect, useState } from "react"
import { useForm, type Resolver } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import type { z } from "zod"
import { Loader2 } from "lucide-react"
import { PasswordInput } from "@/components/common/PasswordInput"
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
import type { ApiError } from "@/lib/request"
import * as adminApi from "@/services/adminApi"
import { createUserSchema } from "./createUserSchema"

type CreateUserFormValues = z.infer<typeof createUserSchema>

interface CreateUserDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: () => Promise<void> | void
}

export function CreateUserDialog({
  open,
  onOpenChange,
  onSuccess,
}: CreateUserDialogProps) {
  const [submitError, setSubmitError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CreateUserFormValues>({
    resolver: zodResolver(createUserSchema) as Resolver<CreateUserFormValues>,
    defaultValues: {
      email: "",
      nickname: "",
      phone: "",
      password: "",
    },
  })

  useEffect(() => {
    if (!open) return
    reset({
      email: "",
      nickname: "",
      phone: "",
      password: "",
    })
    setSubmitError(null)
  }, [open, reset])

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null)
    try {
      await adminApi.createAdminUser({
        email: values.email.trim(),
        nickname: values.nickname.trim(),
        phone: values.phone?.trim() || undefined,
        password: values.password,
      })
      onOpenChange(false)
      await onSuccess?.()
    } catch (error) {
      setSubmitError((error as ApiError).message || "新增用户失败")
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>新增普通用户</DialogTitle>
          <DialogDescription>
            管理后台创建的账号默认角色为普通用户，初始密码可在后续由用户自行修改。
          </DialogDescription>
        </DialogHeader>

        <form className="space-y-4" onSubmit={onSubmit}>
          <div className="space-y-1.5">
            <Label htmlFor="create-user-email">邮箱</Label>
            <Input
              id="create-user-email"
              type="email"
              placeholder="请输入邮箱地址"
              autoComplete="email"
              aria-invalid={errors.email ? "true" : "false"}
              {...register("email")}
            />
            {errors.email && <p className="text-xs text-destructive">{errors.email.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="create-user-nickname">昵称</Label>
            <Input
              id="create-user-nickname"
              placeholder="请输入昵称"
              aria-invalid={errors.nickname ? "true" : "false"}
              {...register("nickname")}
            />
            {errors.nickname && <p className="text-xs text-destructive">{errors.nickname.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="create-user-phone">手机号</Label>
            <Input
              id="create-user-phone"
              placeholder="选填，11 位手机号"
              autoComplete="tel"
              aria-invalid={errors.phone ? "true" : "false"}
              {...register("phone")}
            />
            {errors.phone && <p className="text-xs text-destructive">{errors.phone.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="create-user-password">初始密码</Label>
            <PasswordInput
              id="create-user-password"
              placeholder="请输入初始密码"
              autoComplete="new-password"
              register={register("password")}
              error={errors.password?.message}
              disabled={isSubmitting}
            />
          </div>

          {submitError && (
            <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
              {submitError}
            </div>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
              取消
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting && <Loader2 className="h-4 w-4 animate-spin" />}
              创建用户
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

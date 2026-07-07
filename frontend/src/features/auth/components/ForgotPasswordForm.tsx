"use client"

import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import Link from "next/link"
import { PasswordInput } from "@/components/common/PasswordInput"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { CheckCircle } from "lucide-react"
import { useForgotPassword } from "../hooks/useAuth"
import { EmailCodeButton } from "./EmailCodeButton"

const forgotPasswordSchema = z.object({
  email: z.string().email("请输入有效的邮箱地址"),
  code: z.string().length(6, "验证码为6位数字"),
  newPassword: z
    .string()
    .min(8, "密码至少8位")
    .max(20, "密码最多20位")
    .regex(/[a-z]/, "需包含小写字母")
    .regex(/[A-Z]/, "需包含大写字母")
    .regex(/[0-9]/, "需包含数字"),
  confirmPassword: z.string(),
}).refine((data) => data.newPassword === data.confirmPassword, {
  message: "两次密码输入不一致",
  path: ["confirmPassword"],
})

type ForgotPasswordFormData = z.infer<typeof forgotPasswordSchema>

export function ForgotPasswordForm() {
  const { resetPassword, error, success } = useForgotPassword()
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordFormData>({
    resolver: zodResolver(forgotPasswordSchema),
  })

  const email = watch("email")

  if (success) {
    return (
      <div className="flex flex-col items-center justify-center py-8 space-y-4">
        <CheckCircle className="h-12 w-12 text-green-500" />
        <p className="text-lg font-medium">密码重置成功</p>
        <p className="text-sm text-muted-foreground">即将跳转到登录页...</p>
      </div>
    )
  }

  return (
    <form
      onSubmit={handleSubmit((data) => resetPassword(data))}
      className="space-y-4"
    >
      {error && (
        <div className="rounded-lg bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="space-y-2">
        <Label htmlFor="email">邮箱</Label>
        <Input id="email" type="email" placeholder="请输入注册邮箱" {...register("email")} />
        {errors.email && <p className="text-xs text-destructive">{errors.email.message}</p>}
      </div>

      <div className="space-y-2">
        <Label htmlFor="code">验证码</Label>
        <div className="flex gap-2">
          <Input id="code" className="flex-1" placeholder="6位验证码" maxLength={6} {...register("code")} />
          <EmailCodeButton email={email} scene="RESET_PASSWORD" />
        </div>
        {errors.code && <p className="text-xs text-destructive">{errors.code.message}</p>}
      </div>

      <div className="space-y-2">
        <Label htmlFor="newPassword">新密码</Label>
        <PasswordInput
          id="newPassword"
          placeholder="8-20位，含大小写字母和数字"
          register={register("newPassword")}
          error={errors.newPassword?.message}
          showCapsLock
          autoComplete="new-password"
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="confirmPassword">确认新密码</Label>
        <PasswordInput
          id="confirmPassword"
          placeholder="请再次输入新密码"
          register={register("confirmPassword")}
          error={errors.confirmPassword?.message}
          showCapsLock
          ariaLabelShow="显示确认新密码"
          ariaLabelHide="隐藏确认新密码"
          autoComplete="new-password"
        />
      </div>

      <Button type="submit" className="w-full" disabled={isSubmitting}>
        {isSubmitting ? "重置中..." : "重置密码"}
      </Button>

      <p className="text-center text-sm text-muted-foreground">
        <Link href="/login" className="text-primary hover:underline font-medium">
          返回登录
        </Link>
      </p>
    </form>
  )
}

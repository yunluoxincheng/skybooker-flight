"use client"

import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useRegister } from "../hooks/useAuth"
import { EmailCodeButton } from "./EmailCodeButton"

const registerSchema = z.object({
  email: z.string().email("请输入有效的邮箱地址"),
  code: z.string().length(6, "验证码为6位数字"),
  nickname: z.string().min(1, "请输入昵称").max(20, "昵称最多20个字符"),
  password: z
    .string()
    .min(8, "密码至少8位")
    .max(20, "密码最多20位")
    .regex(/[a-z]/, "需包含小写字母")
    .regex(/[A-Z]/, "需包含大写字母")
    .regex(/[0-9]/, "需包含数字"),
  confirmPassword: z.string(),
}).refine((data) => data.password === data.confirmPassword, {
  message: "两次密码输入不一致",
  path: ["confirmPassword"],
})

type RegisterFormData = z.infer<typeof registerSchema>

export function RegisterForm() {
  const { register: doRegister, error } = useRegister()
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
  })

  const email = watch("email")

  return (
    <form
      onSubmit={handleSubmit((data) => doRegister(data))}
      className="space-y-4"
    >
      {error && (
        <div className="rounded-lg bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="space-y-2">
        <Label htmlFor="email">邮箱</Label>
        <Input id="email" type="email" placeholder="请输入邮箱" {...register("email")} />
        {errors.email && <p className="text-xs text-destructive">{errors.email.message}</p>}
      </div>

      <div className="space-y-2">
        <Label htmlFor="code">验证码</Label>
        <div className="flex gap-2">
          <Input id="code" className="flex-1" placeholder="6位验证码" maxLength={6} {...register("code")} />
          <EmailCodeButton email={email} scene="REGISTER" />
        </div>
        {errors.code && <p className="text-xs text-destructive">{errors.code.message}</p>}
      </div>

      <div className="space-y-2">
        <Label htmlFor="nickname">昵称</Label>
        <Input id="nickname" placeholder="请输入昵称" {...register("nickname")} />
        {errors.nickname && <p className="text-xs text-destructive">{errors.nickname.message}</p>}
      </div>

      <div className="space-y-2">
        <Label htmlFor="password">密码</Label>
        <Input id="password" type="password" placeholder="8-20位，含大小写字母和数字" {...register("password")} />
        {errors.password && <p className="text-xs text-destructive">{errors.password.message}</p>}
      </div>

      <div className="space-y-2">
        <Label htmlFor="confirmPassword">确认密码</Label>
        <Input id="confirmPassword" type="password" placeholder="请再次输入密码" {...register("confirmPassword")} />
        {errors.confirmPassword && <p className="text-xs text-destructive">{errors.confirmPassword.message}</p>}
      </div>

      <Button type="submit" className="w-full" disabled={isSubmitting}>
        {isSubmitting ? "注册中..." : "注册"}
      </Button>

      <p className="text-center text-sm text-muted-foreground">
        已有账号？{" "}
        <Link href="/login" className="text-primary hover:underline font-medium">
          立即登录
        </Link>
      </p>
    </form>
  )
}

"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { useAdminAuth } from "@/contexts/AdminAuthContext"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Shield } from "lucide-react"
import { ApiError } from "@/lib/request"

const adminLoginSchema = z.object({
  username: z.string().min(1, "请输入管理员用户名"),
  password: z.string().min(1, "请输入密码"),
})

type AdminLoginFormData = z.infer<typeof adminLoginSchema>

export function AdminLoginForm() {
  const router = useRouter()
  const { login } = useAdminAuth()
  const [error, setError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<AdminLoginFormData>({
    resolver: zodResolver(adminLoginSchema),
  })

  const onSubmit = async (data: AdminLoginFormData) => {
    setError(null)
    try {
      await login(data.username, data.password)
      router.push("/admin/dashboard")
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
      } else {
        setError("登录失败，请稍后重试")
      }
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm">
        {/* 管理端品牌 */}
        <div className="mb-8 text-center">
          <div className="inline-flex items-center justify-center h-14 w-14 rounded-2xl bg-primary text-white mb-4">
            <Shield className="h-7 w-7" />
          </div>
          <h1 className="text-2xl font-bold">SkyBooker 管理后台</h1>
          <p className="mt-1 text-sm text-muted-foreground">请使用管理员账号登录</p>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            {error && (
              <div className="rounded-lg bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
                {error}
              </div>
            )}

            <div className="space-y-2">
              <Label htmlFor="username">管理员用户名</Label>
              <Input id="username" placeholder="请输入管理员用户名" {...register("username")} />
              {errors.username && <p className="text-xs text-destructive">{errors.username.message}</p>}
            </div>

            <div className="space-y-2">
              <Label htmlFor="password">密码</Label>
              <Input id="password" type="password" placeholder="请输入密码" {...register("password")} />
              {errors.password && <p className="text-xs text-destructive">{errors.password.message}</p>}
            </div>

            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? "登录中..." : "管理员登录"}
            </Button>
          </form>
        </div>
      </div>
    </div>
  )
}

"use client"

import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import { ShieldAlert } from "lucide-react"
import { UserLayout } from "@/components/layout/UserLayout"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/contexts/AuthContext"
import { DeleteAccountDialog } from "@/features/settings/components/DeleteAccountDialog"

function SettingsSkeleton() {
  return (
    <UserLayout>
      <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6 lg:px-8 space-y-6">
        <div className="space-y-2">
          <Skeleton className="h-8 w-32" />
          <Skeleton className="h-4 w-56" />
        </div>
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    </UserLayout>
  )
}

export default function SettingsPage() {
  const router = useRouter()
  const { isAuthenticated, isLoading } = useAuth()
  const [mounted, setMounted] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)

  useEffect(() => {
    setMounted(true)
  }, [])

  useEffect(() => {
    if (!mounted || isLoading) return
    if (!isAuthenticated) {
      router.replace("/login?redirect=%2Fsettings")
    }
  }, [isAuthenticated, isLoading, mounted, router])

  if (!mounted || isLoading) {
    return <SettingsSkeleton />
  }

  if (!isAuthenticated) {
    return null
  }

  return (
    <UserLayout>
      <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6 lg:px-8">
        <div className="mb-8 space-y-2">
          <h1 className="text-2xl font-bold">账号设置</h1>
          <p className="text-sm text-muted-foreground">管理当前账号的安全操作与敏感变更。</p>
        </div>

        <Card className="border-destructive/30">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-destructive">
              <ShieldAlert className="h-5 w-5" />
              危险区域
            </CardTitle>
            <CardDescription>
              注销账号后，当前登录状态会失效，部分历史业务数据将按系统策略保留或结算，请谨慎操作。
            </CardDescription>
          </CardHeader>

          <CardContent className="space-y-4">
            <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
              如果账号仍存在进行中的订单、候补、退款或改签流程，系统可能拒绝本次注销请求。
            </div>

            <div className="flex justify-end">
              <Button variant="destructive" onClick={() => setDeleteOpen(true)}>
                注销账号
              </Button>
            </div>
          </CardContent>
        </Card>

        <DeleteAccountDialog
          open={deleteOpen}
          onOpenChange={setDeleteOpen}
          onDeleted={() => router.push("/")}
        />
      </div>
    </UserLayout>
  )
}

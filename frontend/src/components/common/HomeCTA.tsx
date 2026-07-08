"use client"

import Link from "next/link"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/contexts/AuthContext"

export function HomeCTA() {
  const { isAuthenticated, isLoading } = useAuth()

  return (
    <section className="py-16 sm:py-20">
      <div className="mx-auto max-w-7xl px-4 text-center sm:px-6 lg:px-8">
        {isLoading ? (
          <div className="flex flex-col items-center">
            <Skeleton className="mb-4 h-9 w-72 max-w-full" />
            <Skeleton className="mb-8 h-5 w-80 max-w-full" />
            <div className="flex flex-col items-center justify-center gap-3 sm:flex-row">
              <Skeleton className="h-9 w-28" />
              <Skeleton className="h-9 w-28" />
            </div>
          </div>
        ) : isAuthenticated ? (
          <>
            <h2 className="mb-4 text-2xl font-bold sm:text-3xl">准备好出发了吗？</h2>
            <p className="mb-8 text-muted-foreground">搜索航班、查看订单，或管理你的行程</p>
            <div className="flex flex-col items-center justify-center gap-3 sm:flex-row">
              <Button render={<Link href="/flights">搜索航班</Link>} size="lg" nativeButton={false} />
              <Button
                variant="outline"
                render={<Link href="/orders">查看订单</Link>}
                size="lg"
                nativeButton={false}
              />
            </div>
          </>
        ) : (
          <>
            <h2 className="mb-4 text-2xl font-bold sm:text-3xl">准备好开始你的旅程了吗？</h2>
            <p className="mb-8 text-muted-foreground">立即注册，体验 AI 智能购票的便捷</p>
            <Button render={<Link href="/register">免费注册</Link>} size="lg" nativeButton={false} />
          </>
        )}
      </div>
    </section>
  )
}

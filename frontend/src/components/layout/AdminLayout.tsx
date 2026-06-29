"use client"

import { useEffect, type ReactNode } from "react"
import { useRouter } from "next/navigation"
import { AdminAuthProvider, useAdminAuth } from "@/contexts/AdminAuthContext"
import { AdminSidebar } from "./AdminSidebar"
import { AdminHeader } from "./AdminHeader"

function AdminLayoutInner({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAdminAuth()
  const router = useRouter()

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace("/admin")
    }
  }, [isLoading, isAuthenticated, router])

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    )
  }

  if (!isAuthenticated) return null

  return (
    <div className="min-h-screen bg-slate-50">
      <AdminSidebar />
      <AdminHeader />
      <main className="ml-60 p-6">{children}</main>
    </div>
  )
}

export function AdminLayout({ children }: { children: ReactNode }) {
  return (
    <AdminAuthProvider>
      <AdminLayoutInner>{children}</AdminLayoutInner>
    </AdminAuthProvider>
  )
}

"use client"

import { createContext, useContext, useEffect, useState, useCallback, type ReactNode } from "react"
import { useRouter } from "next/navigation"
import type { AdminUser } from "@/types/auth"
import * as adminApi from "@/services/adminApi"
import {
  getAdminToken,
  setAdminToken,
  removeAdminToken,
  getAdminRefreshToken,
  setAdminRefreshToken,
  removeAdminRefreshToken,
  setAdminData,
  removeAdminData,
} from "@/lib/auth-storage"

interface AdminAuthState {
  admin: AdminUser | null
  token: string | null
  isLoading: boolean
  isAuthenticated: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  refreshAdmin: () => Promise<void>
}

const AdminAuthContext = createContext<AdminAuthState | null>(null)
const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "/api"

export function AdminAuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter()
  const [admin, setAdmin] = useState<AdminUser | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const storedToken = getAdminToken()
    if (!storedToken) {
      setIsLoading(false)
      return
    }

    const initSession = async () => {
      try {
        const u = await adminApi.getAdminMe()
        setAdmin(u)
        setToken(storedToken)
      } catch {
        const refreshToken = getAdminRefreshToken()
        if (!refreshToken) {
          removeAdminToken()
          removeAdminRefreshToken()
          removeAdminData()
          return
        }

        try {
          const res = await fetch(`${BASE_URL}/admin/auth/refresh`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ refreshToken }),
          })

          if (res.ok) {
            const json = await res.json()
            if (json.code === 200 && json.data?.accessToken && json.data?.refreshToken) {
              setAdminToken(json.data.accessToken)
              setAdminRefreshToken(json.data.refreshToken)
              if (json.data.admin) {
                setAdminData(json.data.admin)
                setAdmin(json.data.admin)
              }
              setToken(json.data.accessToken)
              return
            }
          }
        } catch {
          // Ignore and clear the session below.
        }

        removeAdminToken()
        removeAdminRefreshToken()
        removeAdminData()
      } finally {
        setIsLoading(false)
      }
    }

    void initSession()
  }, [])

  useEffect(() => {
    const handler = (e: Event) => {
      const { auth } = (e as CustomEvent<{ auth: "user" | "admin" }>).detail
      if (auth !== "admin") return

      removeAdminToken()
      removeAdminRefreshToken()
      removeAdminData()
      setToken(null)
      setAdmin(null)
      router.push("/admin")
    }

    window.addEventListener("auth:session-expired", handler)
    return () => window.removeEventListener("auth:session-expired", handler)
  }, [router])

  const login = useCallback(async (username: string, password: string) => {
    const res = await adminApi.adminLogin(username, password)
    setAdminToken(res.accessToken)
    setAdminRefreshToken(res.refreshToken)
    setAdminData(res.admin)
    setToken(res.accessToken)
    setAdmin(res.admin)
  }, [])

  const logout = useCallback(async () => {
    const refreshToken = getAdminRefreshToken()
    try {
      await adminApi.adminLogout(refreshToken ?? undefined)
    } catch {
      // ignore
    }
    removeAdminToken()
    removeAdminRefreshToken()
    removeAdminData()
    setToken(null)
    setAdmin(null)
  }, [])

  const refreshAdmin = useCallback(async () => {
    try {
      const u = await adminApi.getAdminMe()
      setAdmin(u)
      setAdminData(u)
    } catch {
      // ignore
    }
  }, [])

  return (
    <AdminAuthContext.Provider
      value={{
        admin,
        token,
        isLoading,
        isAuthenticated: !!token && !!admin,
        login,
        logout,
        refreshAdmin,
      }}
    >
      {children}
    </AdminAuthContext.Provider>
  )
}

export function useAdminAuth(): AdminAuthState {
  const ctx = useContext(AdminAuthContext)
  if (!ctx) {
    throw new Error("useAdminAuth must be used within AdminAuthProvider")
  }
  return ctx
}

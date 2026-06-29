"use client"

import { createContext, useContext, useEffect, useState, useCallback, type ReactNode } from "react"
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

export function AdminAuthProvider({ children }: { children: ReactNode }) {
  const [admin, setAdmin] = useState<AdminUser | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const storedToken = getAdminToken()
    if (!storedToken) {
      setIsLoading(false)
      return
    }

    adminApi
      .getAdminMe()
      .then((u) => {
        setAdmin(u)
        setToken(storedToken)
      })
      .catch(() => {
        removeAdminToken()
        removeAdminRefreshToken()
        removeAdminData()
      })
      .finally(() => setIsLoading(false))
  }, [])

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

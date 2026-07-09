"use client"

import { createContext, useContext, useEffect, useState, useCallback, type ReactNode } from "react"
import { useRouter } from "next/navigation"
import type { DeleteAccountRequest, User } from "@/types/auth"
import * as authApi from "@/services/authApi"
import {
  getUserToken,
  setUserToken,
  removeUserToken,
  getUserRefreshToken,
  setUserRefreshToken,
  removeUserRefreshToken,
  setUserData,
  removeUserData,
} from "@/lib/auth-storage"

interface AuthState {
  user: User | null
  token: string | null
  isLoading: boolean
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<void>
  register: (data: { email: string; code: string; nickname: string; password: string; confirmPassword: string }) => Promise<void>
  logout: () => Promise<void>
  deleteAccount: (data: DeleteAccountRequest) => Promise<void>
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)
const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "/api"

export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter()
  const [user, setUser] = useState<User | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const clearUserSession = useCallback(() => {
    removeUserToken()
    removeUserRefreshToken()
    removeUserData()
    setToken(null)
    setUser(null)
  }, [])

  // 初始化：从 localStorage 恢复会话
  useEffect(() => {
    const storedToken = getUserToken()
    if (!storedToken) {
      setIsLoading(false)
      return
    }

    const initSession = async () => {
      try {
        const u = await authApi.getMe()
        setUser(u)
        setToken(storedToken)
      } catch {
        const refreshToken = getUserRefreshToken()
        if (!refreshToken) {
          clearUserSession()
          return
        }

        try {
          const res = await fetch(`${BASE_URL}/auth/refresh`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ refreshToken }),
          })

          if (res.ok) {
            const json = await res.json()
            if (json.code === 200 && json.data?.accessToken && json.data?.refreshToken) {
              setUserToken(json.data.accessToken)
              setUserRefreshToken(json.data.refreshToken)
              if (json.data.user) {
                setUserData(json.data.user)
                setUser(json.data.user)
              }
              setToken(json.data.accessToken)
              return
            }
          }
        } catch {
          // Ignore and clear the session below.
        }

        clearUserSession()
      } finally {
        setIsLoading(false)
      }
    }

    void initSession()
  }, [clearUserSession])

  useEffect(() => {
    const handler = (e: Event) => {
      const { auth } = (e as CustomEvent<{ auth: "user" | "admin" }>).detail
      if (auth !== "user") return

      clearUserSession()

      const currentPath = window.location.pathname + window.location.search
      router.push(`/login?redirect=${encodeURIComponent(currentPath)}`)
    }

    window.addEventListener("auth:session-expired", handler)
    return () => window.removeEventListener("auth:session-expired", handler)
  }, [clearUserSession, router])

  const login = useCallback(async (email: string, password: string) => {
    const res = await authApi.login(email, password)
    setUserToken(res.accessToken)
    setUserRefreshToken(res.refreshToken)
    setUserData(res.user)
    setToken(res.accessToken)
    setUser(res.user)
  }, [])

  const register = useCallback(
    async (data: { email: string; code: string; nickname: string; password: string; confirmPassword: string }) => {
      await authApi.register(data)
      // 注册后不自动登录，需跳转到登录页
    },
    []
  )

  const logout = useCallback(async () => {
    const refreshToken = getUserRefreshToken()
    try {
      await authApi.logout(refreshToken ?? undefined)
    } catch {
      // 即使 API 失败也清除本地状态
    }
    clearUserSession()
  }, [clearUserSession])

  const deleteAccount = useCallback(
    async (data: DeleteAccountRequest) => {
      await authApi.deleteAccount(data)
      clearUserSession()
    },
    [clearUserSession]
  )

  const refreshUser = useCallback(async () => {
    try {
      const u = await authApi.getMe()
      setUser(u)
      setUserData(u)
    } catch {
      // ignore
    }
  }, [])

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isLoading,
        isAuthenticated: !!token && !!user,
        login,
        register,
        logout,
        deleteAccount,
        refreshUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error("useAuth must be used within AuthProvider")
  }
  return ctx
}

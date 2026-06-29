"use client"

import { createContext, useContext, useEffect, useState, useCallback, type ReactNode } from "react"
import type { User } from "@/types/auth"
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
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  // 初始化：从 localStorage 恢复会话
  useEffect(() => {
    const storedToken = getUserToken()
    if (!storedToken) {
      setIsLoading(false)
      return
    }

    // 验证 token 是否仍然有效
    authApi
      .getMe()
      .then((u) => {
        setUser(u)
        setToken(storedToken)
      })
      .catch(() => {
        removeUserToken()
        removeUserRefreshToken()
        removeUserData()
      })
      .finally(() => setIsLoading(false))
  }, [])

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
    removeUserToken()
    removeUserRefreshToken()
    removeUserData()
    setToken(null)
    setUser(null)
  }, [])

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

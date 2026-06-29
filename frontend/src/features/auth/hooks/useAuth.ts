"use client"

import { useState } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { useAuth as useAuthContext } from "@/contexts/AuthContext"
import * as authApi from "@/services/authApi"
import { ApiError } from "@/lib/request"

export function useLogin() {
  const { login } = useAuthContext()
  const router = useRouter()
  const searchParams = useSearchParams()
  const [error, setError] = useState<string | null>(null)

  const handleLogin = async (email: string, password: string) => {
    setError(null)
    try {
      await login(email, password)
      const redirect = searchParams.get("redirect")
      router.push(redirect || "/")
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
      } else {
        setError("登录失败，请稍后重试")
      }
    }
  }

  return { login: handleLogin, error, setError }
}

export function useRegister() {
  const { register } = useAuthContext()
  const router = useRouter()
  const [error, setError] = useState<string | null>(null)

  const handleRegister = async (data: {
    email: string
    code: string
    nickname: string
    password: string
    confirmPassword: string
  }) => {
    setError(null)
    try {
      await register(data)
      router.push("/login")
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
      } else {
        setError("注册失败，请稍后重试")
      }
      throw err
    }
  }

  return { register: handleRegister, error, setError }
}

export function useForgotPassword() {
  const router = useRouter()
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  const handleReset = async (data: {
    email: string
    code: string
    newPassword: string
    confirmPassword: string
  }) => {
    setError(null)
    try {
      await authApi.resetPassword(data)
      setSuccess(true)
      setTimeout(() => router.push("/login"), 2000)
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
      } else {
        setError("重置失败，请稍后重试")
      }
      throw err
    }
  }

  return { resetPassword: handleReset, error, success, setError }
}

export function useSendCode() {
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const sendCode = async (email: string, scene: "REGISTER" | "RESET_PASSWORD") => {
    setError(null)
    setSending(true)
    try {
      await authApi.sendEmailCode(email, scene)
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
      } else {
        setError("发送失败")
      }
      throw err
    } finally {
      setSending(false)
    }
  }

  return { sendCode, sending, error, setError }
}

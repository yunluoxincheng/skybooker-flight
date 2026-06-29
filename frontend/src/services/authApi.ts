import { post, get } from "@/lib/request"
import type { LoginResponse, User } from "@/types/auth"

/** 登录 */
export function login(email: string, password: string) {
  return post<LoginResponse>("/auth/login", { email, password })
}

/** 发送邮箱验证码 */
export function sendEmailCode(email: string, scene: "REGISTER" | "RESET_PASSWORD") {
  return post<null>("/auth/email-code", { email, scene })
}

/** 注册 */
export function register(data: {
  email: string
  code: string
  nickname: string
  password: string
  confirmPassword: string
}) {
  return post<null>("/auth/register", data)
}

/** 重置密码 */
export function resetPassword(data: {
  email: string
  code: string
  newPassword: string
  confirmPassword: string
}) {
  return post<null>("/auth/reset-password", data)
}

/** 获取当前用户信息 */
export function getMe() {
  return get<User>("/auth/me", undefined, { auth: "user" })
}

/** 登出 */
export function logout(refreshToken?: string) {
  return post<null>("/auth/logout", refreshToken ? { refreshToken } : undefined, { auth: "user" })
}

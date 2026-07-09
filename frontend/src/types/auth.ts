/** 用户信息（用户端） — 匹配后端 UserVO */
export interface User {
  id: number
  email: string
  nickname: string
  role: "USER"
}

/** 用户端登录响应 — 匹配后端 LoginVO */
export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: User
}

/** 邮箱验证码场景 */
export type EmailCodeScene = "REGISTER" | "RESET_PASSWORD" | "DELETE_ACCOUNT"

/** 注销账号请求 */
export interface DeleteAccountRequest {
  password?: string
  code?: string
}

/** 注销账号前阻断信息 */
export interface DeleteAccountBlockInfoVO {
  activeOrderCount: number
  waitlistCount: number
  pendingRefundCount: number
  pendingChangeCount: number
  canDelete: boolean
  blockReasons: string[]
}

/** 管理员信息 — 匹配后端 AdminVO */
export interface AdminUser {
  id: number
  username: string
  realName: string
  role: "ADMIN"
}

/** 管理员登录响应 — 匹配后端 AdminLoginVO */
export interface AdminLoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  admin: AdminUser
}

/** 用户信息（用户端） */
export interface User {
  id: number
  email: string
  nickname: string
  avatar?: string
  role: "USER"
  status: "ENABLED" | "DISABLED"
  createdAt: string
}

/** 用户端登录响应 */
export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: User
}

/** 管理员信息 */
export interface AdminUser {
  id: number
  username: string
  nickname: string
  role: "ADMIN"
  status: "ENABLED" | "DISABLED"
  createdAt: string
}

/** 管理员登录响应 */
export interface AdminLoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  admin: AdminUser
}

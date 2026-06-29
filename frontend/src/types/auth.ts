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

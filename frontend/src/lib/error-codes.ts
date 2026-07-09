import { ApiError } from "./request"

export const ERROR_CODES = {
  INVALID_CREDENTIALS: 10007,
  UNAUTHORIZED: 10001,
  FORBIDDEN: 10002,
  VALIDATION_ERROR: 10003,
  ACCOUNT_DISABLED: 10008,
  ACCOUNT_TYPE_MISMATCH: 10012,
  ADMIN_PROFILE_DISABLED: 10019,
  TOKEN_INVALID: 10018,
  TOKEN_EXPIRED: 10011,
  REFRESH_TOKEN_INVALID: 10021,
  LOGIN_RATE_LIMITED: 10017,
  SYSTEM_ERROR: 90000,
} as const

export function getErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiError) {
    return err.message || fallback
  }

  if (err instanceof Error) {
    return err.message || fallback
  }

  return fallback
}

export function getLoginErrorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.code === ERROR_CODES.INVALID_CREDENTIALS) return "账号或密码错误"
    if (err.code === ERROR_CODES.ACCOUNT_DISABLED) return "账号已被禁用"
    if (err.code === ERROR_CODES.ACCOUNT_TYPE_MISMATCH) return "账号类型不允许登录当前入口"
    if (err.code === ERROR_CODES.LOGIN_RATE_LIMITED) return "登录失败次数过多，请稍后再试"
    if (err.message === "未登录或登录已过期") return "登录失败，请稍后重试"
    return err.message || "登录失败，请稍后重试"
  }

  return "登录失败，请稍后重试"
}

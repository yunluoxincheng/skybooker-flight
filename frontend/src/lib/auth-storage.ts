const USER_TOKEN_KEY = "skybooker_user_token"
const USER_REFRESH_KEY = "skybooker_user_refresh"
const USER_DATA_KEY = "skybooker_user_data"
const ADMIN_TOKEN_KEY = "skybooker_admin_token"
const ADMIN_REFRESH_KEY = "skybooker_admin_refresh"
const ADMIN_DATA_KEY = "skybooker_admin_data"

// ---- 用户端 ----

export function getUserToken(): string | null {
  if (typeof window === "undefined") return null
  return localStorage.getItem(USER_TOKEN_KEY)
}

export function setUserToken(token: string): void {
  localStorage.setItem(USER_TOKEN_KEY, token)
}

export function removeUserToken(): void {
  localStorage.removeItem(USER_TOKEN_KEY)
}

export function getUserRefreshToken(): string | null {
  if (typeof window === "undefined") return null
  return localStorage.getItem(USER_REFRESH_KEY)
}

export function setUserRefreshToken(token: string): void {
  localStorage.setItem(USER_REFRESH_KEY, token)
}

export function removeUserRefreshToken(): void {
  localStorage.removeItem(USER_REFRESH_KEY)
}

export function getUserData<T = unknown>(): T | null {
  if (typeof window === "undefined") return null
  const raw = localStorage.getItem(USER_DATA_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

export function setUserData<T = unknown>(data: T): void {
  localStorage.setItem(USER_DATA_KEY, JSON.stringify(data))
}

export function removeUserData(): void {
  localStorage.removeItem(USER_DATA_KEY)
}

// ---- 管理端 ----

export function getAdminToken(): string | null {
  if (typeof window === "undefined") return null
  return localStorage.getItem(ADMIN_TOKEN_KEY)
}

export function setAdminToken(token: string): void {
  localStorage.setItem(ADMIN_TOKEN_KEY, token)
}

export function removeAdminToken(): void {
  localStorage.removeItem(ADMIN_TOKEN_KEY)
}

export function getAdminRefreshToken(): string | null {
  if (typeof window === "undefined") return null
  return localStorage.getItem(ADMIN_REFRESH_KEY)
}

export function setAdminRefreshToken(token: string): void {
  localStorage.setItem(ADMIN_REFRESH_KEY, token)
}

export function removeAdminRefreshToken(): void {
  localStorage.removeItem(ADMIN_REFRESH_KEY)
}

export function getAdminData<T = unknown>(): T | null {
  if (typeof window === "undefined") return null
  const raw = localStorage.getItem(ADMIN_DATA_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

export function setAdminData<T = unknown>(data: T): void {
  localStorage.setItem(ADMIN_DATA_KEY, JSON.stringify(data))
}

export function removeAdminData(): void {
  localStorage.removeItem(ADMIN_DATA_KEY)
}

// ---- 统一清除 ----

export function clearAllAuth(): void {
  removeUserToken()
  removeUserRefreshToken()
  removeUserData()
  removeAdminToken()
  removeAdminRefreshToken()
  removeAdminData()
}

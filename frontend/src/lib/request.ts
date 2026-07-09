import type { ApiResponse } from "@/types/api"
import {
  getUserToken,
  getAdminToken,
  removeUserToken,
  removeUserRefreshToken,
  removeUserData,
  removeAdminToken,
  removeAdminRefreshToken,
  removeAdminData,
  getUserRefreshToken,
  setUserToken,
  setUserRefreshToken,
  setUserData,
  getAdminRefreshToken,
  setAdminToken,
  setAdminRefreshToken,
  setAdminData,
} from "./auth-storage"

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "/api"
const DEFAULT_TIMEOUT = 15000

export class ApiError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
    this.name = "ApiError"
  }
}

type AuthType = "user" | "admin" | "none"

interface RequestOptions {
  auth?: AuthType
  timeout?: number
}

function getToken(auth: AuthType): string | null {
  switch (auth) {
    case "user":
      return getUserToken()
    case "admin":
      return getAdminToken()
    default:
      return null
  }
}

function handleUnauthorized(auth: AuthType): void {
  if (typeof window === "undefined") return
  switch (auth) {
    case "user":
      removeUserToken()
      removeUserRefreshToken()
      removeUserData()
      break
    case "admin":
      removeAdminToken()
      removeAdminRefreshToken()
      removeAdminData()
      break
  }
}

// ---- Token Refresh Logic ----

type RefreshableAuthType = Extract<AuthType, "user" | "admin">

type RefreshResponseData = {
  accessToken: string
  refreshToken: string
  user?: unknown
  admin?: unknown
}

const refreshState: Record<
  RefreshableAuthType,
  {
    isRefreshing: boolean
    subscribers: Array<(token: string | null) => void>
  }
> = {
  user: {
    isRefreshing: false,
    subscribers: [],
  },
  admin: {
    isRefreshing: false,
    subscribers: [],
  },
}

function onRefreshed(auth: RefreshableAuthType, token: string | null): void {
  refreshState[auth].subscribers.forEach((cb) => cb(token))
  refreshState[auth].subscribers.length = 0
}

function addRefreshSubscriber(auth: RefreshableAuthType, cb: (token: string | null) => void): void {
  refreshState[auth].subscribers.push(cb)
}

function dispatchSessionExpired(auth: RefreshableAuthType): void {
  if (typeof window === "undefined") return
  window.dispatchEvent(new CustomEvent("auth:session-expired", { detail: { auth } }))
}

async function tryRefreshToken(auth: RefreshableAuthType): Promise<string | null> {
  const refreshToken = auth === "user" ? getUserRefreshToken() : getAdminRefreshToken()
  if (!refreshToken) return null

  const endpoint = auth === "user" ? "/auth/refresh" : "/admin/auth/refresh"

  try {
    const res = await fetch(`${BASE_URL}${endpoint}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    })

    if (!res.ok) return null

    const json: ApiResponse<RefreshResponseData> = await res.json()
    if (json.code !== 200 || !json.data?.accessToken || !json.data?.refreshToken) {
      return null
    }

    const { accessToken: newAccess, refreshToken: newRefresh, user, admin } = json.data

    if (auth === "user") {
      setUserToken(newAccess)
      setUserRefreshToken(newRefresh)
      if (user) {
        setUserData(user)
      }
    } else {
      setAdminToken(newAccess)
      setAdminRefreshToken(newRefresh)
      if (admin) {
        setAdminData(admin)
      }
    }

    return newAccess
  } catch {
    return null
  }
}

async function handle401AndRetry(auth: RefreshableAuthType): Promise<string | null> {
  if (refreshState[auth].isRefreshing) {
    return new Promise((resolve) => {
      addRefreshSubscriber(auth, (token) => resolve(token))
    })
  }

  refreshState[auth].isRefreshing = true
  try {
    const newToken = await tryRefreshToken(auth)
    onRefreshed(auth, newToken)
    return newToken
  } finally {
    refreshState[auth].isRefreshing = false
  }
}

async function parseUnauthorizedResponse(res: Response): Promise<{ serverCode: number; serverMessage: string }> {
  let serverCode = 401
  let serverMessage = ""

  try {
    const json: ApiResponse = await res.json()
    serverCode = typeof json.code === "number" ? json.code : 401
    serverMessage = json.message || ""
  } catch {
    // Ignore non-JSON 401 responses and fall back to generic copy.
  }

  return { serverCode, serverMessage }
}

async function request<T>(
  method: string,
  url: string,
  body?: unknown,
  options: RequestOptions = {}
): Promise<T> {
  const { auth = "none", timeout = DEFAULT_TIMEOUT } = options

  const controller = new AbortController()
  let timer = setTimeout(() => controller.abort(), timeout)

  const headers: Record<string, string> = {}
  if (!(body instanceof FormData)) {
    headers["Content-Type"] = "application/json"
  }

  const token = getToken(auth)
  if (token) {
    headers["Authorization"] = `Bearer ${token}`
  }

  try {
    let res = await fetch(`${BASE_URL}${url}`, {
      method,
      headers,
      body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    })

    clearTimeout(timer)

    if (res.status === 401) {
      let { serverCode, serverMessage } = await parseUnauthorizedResponse(res)

      if (auth !== "none") {
        const newToken = await handle401AndRetry(auth)
        if (newToken) {
          headers["Authorization"] = `Bearer ${newToken}`
          timer = setTimeout(() => controller.abort(), timeout)
          res = await fetch(`${BASE_URL}${url}`, {
            method,
            headers,
            body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
            signal: controller.signal,
          })
          clearTimeout(timer)

          if (res.status === 401) {
            const parsedRetryUnauthorized = await parseUnauthorizedResponse(res)
            serverCode = parsedRetryUnauthorized.serverCode
            serverMessage = parsedRetryUnauthorized.serverMessage
            handleUnauthorized(auth)
            dispatchSessionExpired(auth)
            throw new ApiError(serverCode, serverMessage || "未登录或登录已过期")
          }
        } else {
          handleUnauthorized(auth)
          dispatchSessionExpired(auth)
          throw new ApiError(serverCode, serverMessage || "未登录或登录已过期")
        }
      }

      if (res.status === 401) {
        throw new ApiError(serverCode, serverMessage || "用户名或密码错误")
      }
    }

    const json: ApiResponse<T> = await res.json()

    if (json.code !== 200) {
      throw new ApiError(json.code, json.message)
    }

    return json.data as T
  } catch (err) {
    clearTimeout(timer)
    if (err instanceof ApiError) throw err
    if (err instanceof DOMException && err.name === "AbortError") {
      throw new ApiError(0, "请求超时")
    }
    if (err instanceof TypeError) {
      throw new ApiError(0, "无法连接服务器，请检查网络或稍后重试")
    }
    if (err instanceof SyntaxError) {
      throw new ApiError(0, "服务器异常，请稍后重试")
    }
    throw new ApiError(0, "网络错误")
  }
}

export function get<T>(
  url: string,
  params?: Record<string, string | number | boolean | undefined>,
  options?: RequestOptions
): Promise<T> {
  const searchParams = new URLSearchParams()
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== "") {
        searchParams.set(key, String(value))
      }
    })
  }
  const qs = searchParams.toString()
  return request<T>("GET", qs ? `${url}?${qs}` : url, undefined, options)
}

export function post<T>(
  url: string,
  body?: unknown,
  options?: RequestOptions
): Promise<T> {
  return request<T>("POST", url, body, options)
}

export function put<T>(
  url: string,
  body?: unknown,
  options?: RequestOptions
): Promise<T> {
  return request<T>("PUT", url, body, options)
}

export function del<T>(url: string, options?: RequestOptions): Promise<T>
export function del<T>(
  url: string,
  body: unknown,
  options?: RequestOptions
): Promise<T>
export function del<T>(
  url: string,
  bodyOrOptions?: unknown,
  options?: RequestOptions
): Promise<T> {
  if (options !== undefined) {
    return request<T>("DELETE", url, bodyOrOptions, options)
  }

  return request<T>("DELETE", url, undefined, bodyOrOptions as RequestOptions | undefined)
}

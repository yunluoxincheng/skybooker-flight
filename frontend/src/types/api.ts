/** 通用 API 响应格式 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页响应 */
export interface PageData<T> {
  records: T[]
  total: number
  page: number
  size: number
}

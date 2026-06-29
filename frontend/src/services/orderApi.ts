import { get, post } from "@/lib/request"
import type { OrderVO, CreateOrderDTO, ChangeOptionVO, ChangeOrderDTO } from "@/types/order"
import type { PageData } from "@/types/api"

/** 创建订单 */
export function createOrder(data: CreateOrderDTO) {
  return post<OrderVO>("/orders", data, { auth: "user" })
}

/** 支付订单 */
export function payOrder(id: number) {
  return post<OrderVO>(`/orders/${id}/pay`, undefined, { auth: "user" })
}

/** 我的订单列表 */
export function getMyOrders(params?: {
  status?: string
  page?: number
  size?: number
}) {
  return get<PageData<OrderVO>>("/orders", params as Record<string, string | number | boolean | undefined>, { auth: "user" })
}

/** 订单详情 */
export function getOrderById(id: number) {
  return get<OrderVO>(`/orders/${id}`, undefined, { auth: "user" })
}

/** 取消订单 */
export function cancelOrder(id: number) {
  return post<OrderVO>(`/orders/${id}/cancel`, undefined, { auth: "user" })
}

/** 退票 */
export function refundOrder(id: number, reason?: string) {
  return post<OrderVO>(`/orders/${id}/refund`, { reason }, { auth: "user" })
}

/** 获取改签可选航班 */
export function getChangeOptions(id: number) {
  return get<ChangeOptionVO[]>(`/orders/${id}/change-options`, undefined, { auth: "user" })
}

/** 执行改签 */
export function changeOrder(id: number, data: ChangeOrderDTO) {
  return post<OrderVO>(`/orders/${id}/change`, data, { auth: "user" })
}

import { get, post } from "@/lib/request"
import type { WaitlistVO, CreateWaitlistDTO } from "@/types/waitlist"

/** 创建候补 */
export function createWaitlist(data: CreateWaitlistDTO) {
  return post<WaitlistVO>("/waitlist", data, { auth: "user" })
}

/** 我的候补列表 */
export function getMyWaitlist() {
  return get<WaitlistVO[]>("/waitlist/my", undefined, { auth: "user" })
}

/** 候补详情 */
export function getWaitlistById(id: number) {
  return get<WaitlistVO>(`/waitlist/${id}`, undefined, { auth: "user" })
}

/** 支付候补订单 */
export function payWaitlist(id: number) {
  return post<WaitlistVO>(`/waitlist/${id}/pay`, undefined, { auth: "user" })
}

/** 取消候补 */
export function cancelWaitlist(id: number) {
  return post<null>(`/waitlist/${id}/cancel`, undefined, { auth: "user" })
}

import { get, post, put, del } from "@/lib/request"
import type { PassengerVO, PassengerDTO } from "@/types/passenger"

/** 获取常用乘机人列表 */
export function getMyPassengers() {
  return get<PassengerVO[]>("/passengers", undefined, { auth: "user" })
}

/** 新增乘机人 */
export function createPassenger(data: PassengerDTO) {
  return post<PassengerVO>("/passengers", data, { auth: "user" })
}

/** 更新乘机人 */
export function updatePassenger(id: number, data: PassengerDTO) {
  return put<PassengerVO>(`/passengers/${id}`, data, { auth: "user" })
}

/** 删除乘机人 */
export function deletePassenger(id: number) {
  return del<null>(`/passengers/${id}`, { auth: "user" })
}

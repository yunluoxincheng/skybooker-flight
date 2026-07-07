import type { PassengerType } from "@/types/order"

/** 乘机人类型对应的中文展示标签 */
export const PASSENGER_TYPE_LABEL: Record<PassengerType, string> = {
  ADULT: "成人",
  CHILD: "儿童",
  INFANT: "婴儿",
}

/** 乘机人 VO（用户保存的常用乘机人） — 匹配后端 PassengerVO */
export interface PassengerVO {
  id: number
  name: string
  idCardNo: string
  passengerType: "ADULT" | "CHILD" | "INFANT"
  phone: string
}

/** 创建/更新乘机人 DTO */
export interface PassengerDTO {
  name: string
  idCardNo: string
  passengerType: "ADULT" | "CHILD" | "INFANT"
  phone: string
}

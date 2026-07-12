import type { PassengerType } from "@/types/order";

export type PassengerForm = {
  name: string;
  idCardNo: string;
  phone: string;
  passengerType: PassengerType;
};

export function validatePassengerForm(form: PassengerForm) {
  return {
    name: form.name.trim() ? "" : "请输入姓名",
    idCardNo: /^\d{15}(\d{2}[\dXx])?$/.test(form.idCardNo)
      ? ""
      : "请输入 15 或 18 位有效身份证号",
    phone: /^1\d{10}$/.test(form.phone) ? "" : "请输入 11 位有效手机号",
  };
}

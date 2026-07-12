import { describe, expect, it } from "vitest";
import { validatePassengerForm } from "../passengerValidation";

describe("BookingWizard passenger validation", () => {
  it("preserves the original identity card and mobile constraints", () => {
    expect(
      validatePassengerForm({
        name: "张三",
        idCardNo: "123",
        phone: "10086",
        passengerType: "ADULT",
      }),
    ).toMatchObject({
      idCardNo: expect.stringContaining("15 或 18"),
      phone: expect.stringContaining("11 位"),
    });
    expect(
      validatePassengerForm({
        name: "张三",
        idCardNo: "110101199001011234",
        phone: "13800138000",
        passengerType: "ADULT",
      }),
    ).toEqual({ name: "", idCardNo: "", phone: "" });
  });
});

import { describe, expect, it } from "vitest"
import { createUserSchema } from "./createUserSchema"

const validUser = {
  email: "user@example.com",
  nickname: "SkyBooker",
  phone: "",
  password: "Password1!",
}

describe("createUserSchema", () => {
  it("accepts a nickname at the backend maximum length", () => {
    expect(createUserSchema.safeParse({ ...validUser, nickname: "a".repeat(50) }).success).toBe(true)
  })

  it("rejects a nickname longer than the backend maximum length", () => {
    expect(createUserSchema.safeParse({ ...validUser, nickname: "a".repeat(51) }).success).toBe(false)
  })

  it("rejects password characters outside the backend allowlist", () => {
    expect(createUserSchema.safeParse({ ...validUser, password: "Password1?" }).success).toBe(false)
  })
})

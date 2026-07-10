import { z } from "zod"

export const ADMIN_CREATE_USER_PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d!@#$%^&*()_+\-=]{8,20}$/

export const createUserSchema = z.object({
  email: z.string().trim().email("请输入有效的邮箱地址"),
  nickname: z.string().trim().min(1, "请输入昵称").max(50, "昵称最多 50 个字符"),
  phone: z
    .string()
    .trim()
    .regex(/^1[3-9]\d{9}$/, "请输入有效的手机号")
    .optional()
    .or(z.literal("")),
  password: z
    .string()
    .regex(ADMIN_CREATE_USER_PASSWORD_PATTERN, "密码必须为 8–20 位，包含大小写字母和数字，且仅能使用允许的特殊字符"),
})

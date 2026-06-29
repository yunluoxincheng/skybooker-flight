import { AuthLayout } from "@/features/auth/components/AuthLayout"
import { ForgotPasswordForm } from "@/features/auth/components/ForgotPasswordForm"

export default function ForgotPasswordPage() {
  return (
    <AuthLayout title="找回密码" subtitle="通过邮箱验证码重置密码">
      <ForgotPasswordForm />
    </AuthLayout>
  )
}

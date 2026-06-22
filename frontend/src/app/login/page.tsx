import { AuthLayout } from "@/features/auth/components/AuthLayout"
import { LoginForm } from "@/features/auth/components/LoginForm"

export default function LoginPage() {
  return (
    <AuthLayout title="欢迎回来" subtitle="登录您的账号以继续">
      <LoginForm />
    </AuthLayout>
  )
}
